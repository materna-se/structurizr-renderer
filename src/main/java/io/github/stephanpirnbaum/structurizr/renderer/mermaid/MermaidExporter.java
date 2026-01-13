package io.github.stephanpirnbaum.structurizr.renderer.mermaid;

import com.structurizr.export.Diagram;
import com.structurizr.export.DiagramExporter;
import com.structurizr.export.mermaid.MermaidDiagramExporter;
import io.github.stephanpirnbaum.structurizr.renderer.AbstractBuildInDiagramExporter;
import io.github.stephanpirnbaum.structurizr.renderer.StructurizrRenderingException;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Exporter implementation to convert a Structurizr {@link com.structurizr.Workspace} into a Mermaid .mmd file and have it rendered as SVG.
 * The latter requires an installation of Mermaid, e.g. npm install -g @mermaid-js/mermaid-cli.
 *
 * @author Stephan Pirnbaum
 */
@Slf4j
public class MermaidExporter extends AbstractBuildInDiagramExporter {

    private static final String mmdcPath = "mmdc";

    public MermaidExporter() {
        super(".mmd");
    }

    @Override
    protected DiagramExporter getExporter() {
        return new MermaidDiagramExporter();
    }

    @Override
    protected Path render(Diagram diagram, String fileName, File outputDir) throws StructurizrRenderingException {
        String mermaidSource = diagram.getDefinition();

        Path mermaidPath = outputDir.toPath().resolve(diagram.getKey() + ".mmd");
        try (OutputStream os = Files.newOutputStream(mermaidPath)) {
            try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(os, StandardCharsets.UTF_8)){
                outputStreamWriter.write(mermaidSource);
            }
        } catch (IOException e) {
            throw new StructurizrRenderingException("Failed to write file during rendering of Mermaid diagram", e);
        }


        try {
            Path outputPath = outputDir.toPath().resolve(fileName);
            ProcessBuilder pb = new ProcessBuilder(
                    mmdcPath,
                    "-i", outputDir.toPath().resolve(diagram.getKey() + ".mmd").toString(),
                    "-o", outputPath.toString()
            );

            pb.directory(new File(".")); // working directory
            pb.inheritIO(); // show CLI output in console
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                log.info("Mermaid diagram rendered successfully: {}", fileName);
            } else {
                log.warn("Mermaid rendering failed with exit code {}", exitCode);
            }
            return outputPath;
        } catch (InterruptedException | IOException e) {
            throw new StructurizrRenderingException("Failed to render Mermaid diagram", e);
        }
    }

}
