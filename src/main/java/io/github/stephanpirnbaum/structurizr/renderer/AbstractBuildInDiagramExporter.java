package io.github.stephanpirnbaum.structurizr.renderer;

import com.structurizr.Workspace;
import com.structurizr.export.Diagram;
import com.structurizr.export.DiagramExporter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Base class for all exporters for which Structurizr itself brings support, i.e. a representation of the target format
 * can be generated directly from its API.
 *
 * @author Stephan Pirnbaum
 */
@RequiredArgsConstructor
@Getter
@Slf4j
public abstract class AbstractBuildInDiagramExporter extends AbstractDiagramExporter {

    private final String fileExtension;

    @Override
    public final Map<String, Path> export(Workspace workspace, Optional<File> workspaceJson, File outputDir) throws StructurizrRenderingException {
        DiagramExporter exporter = getExporter();
        Collection<Diagram> diagrams = exporter.export(workspace);
        Map<String, Path> generatedFiles = new HashMap<>();
        try {
            Files.createDirectories(outputDir.toPath());
        } catch (IOException e) {
            throw new StructurizrRenderingException("Failed to create output directory", e);
        }

        for (Diagram diagram : diagrams) {
            String svgFileName = diagram.getKey() + ".svg";
            String sourceFileName = diagram.getKey() + this.fileExtension;

            log.info("Rendering diagram {}", svgFileName);

            Path sourcePath = outputDir.toPath().resolve(sourceFileName);
            try (OutputStream os = Files.newOutputStream(sourcePath)) {
                try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
                    outputStreamWriter.write(diagram.getDefinition());
                }
            } catch (IOException e) {
                throw new StructurizrRenderingException("Failed to write file during rendering of diagram", e);
            }

            generatedFiles.put(diagram.getKey(), render(diagram, svgFileName, outputDir));
        }

        log.info("Export completed. SVG files in: {}", outputDir.getAbsolutePath());
        return generatedFiles;
    }

    protected abstract DiagramExporter getExporter();

    protected abstract Path render(Diagram diagram, String fileName, File outputDir) throws StructurizrRenderingException;

}
