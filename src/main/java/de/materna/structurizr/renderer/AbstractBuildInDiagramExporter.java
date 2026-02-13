package de.materna.structurizr.renderer;

import com.structurizr.Workspace;
import com.structurizr.export.Diagram;
import com.structurizr.export.DiagramExporter;
import com.structurizr.view.View;
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
    protected final Path export(Path workspacePath, Workspace workspace, Path workspaceJsonPath, File outputDir, String viewKey) throws StructurizrRenderingException {
        DiagramExporter exporter = getExporter();
        Collection<Diagram> diagrams = exporter.export(workspace);

        View view = workspace.getViews().getViewWithKey(viewKey);
        if (view != null) {
            Optional<Diagram> diagram = diagrams.stream().filter(d -> d.getKey().equals(viewKey)).findFirst();
            if (diagram.isPresent()) {
                String sourceFileName = viewKey + this.fileExtension;
                log.info("Rendering diagram for view {}", viewKey);
                Path sourcePath = outputDir.toPath().resolve(sourceFileName);
                try (OutputStream os = Files.newOutputStream(sourcePath)) {
                    try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
                        outputStreamWriter.write(diagram.get().getDefinition());
                    }
                } catch (IOException e) {
                    throw new StructurizrRenderingException("Failed to write file during rendering of diagram", e);
                }
                return render(diagram.get(), constructOutputFilePath(outputDir, viewKey));
            }
        }
        throw new StructurizrRenderingException("No view with key " + viewKey);
    }

    protected abstract DiagramExporter getExporter();

    protected abstract Path render(Diagram diagram, Path outputFilePath) throws StructurizrRenderingException;

}
