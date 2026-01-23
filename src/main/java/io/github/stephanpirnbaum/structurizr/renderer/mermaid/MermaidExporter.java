package io.github.stephanpirnbaum.structurizr.renderer.mermaid;

import com.structurizr.export.Diagram;
import com.structurizr.export.DiagramExporter;
import com.structurizr.export.mermaid.MermaidDiagramExporter;
import io.github.stephanpirnbaum.structurizr.renderer.AbstractBuildInDiagramExporter;
import io.github.stephanpirnbaum.structurizr.renderer.StructurizrRenderingException;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
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
    protected Path render(Diagram diagram, Path outputFilePath) throws StructurizrRenderingException {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    mmdcPath,
                    "-i", outputFilePath.getParent().resolve(diagram.getKey() + ".mmd").toString(),
                    "-o", outputFilePath.toString()
            );

            pb.directory(new File(".")); // working directory
            pb.inheritIO(); // show CLI output in console
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                log.info("Mermaid diagram rendered successfully: {}", outputFilePath.getFileName().toString());
            } else {
                log.warn("Mermaid rendering failed with exit code {}", exitCode);
            }
            return outputFilePath;
        } catch (InterruptedException | IOException e) {
            throw new StructurizrRenderingException("Failed to render Mermaid diagram", e);
        }
    }

    @Override
    protected String getRendererString() {
        return "Mermaid";
    }
}
