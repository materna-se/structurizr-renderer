package io.github.stephanpirnbaum.structurizr.renderer.plantuml;

import com.structurizr.export.Diagram;
import com.structurizr.export.DiagramExporter;
import io.github.stephanpirnbaum.structurizr.renderer.AbstractBuildInDiagramExporter;
import io.github.stephanpirnbaum.structurizr.renderer.StructurizrRenderingException;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Exporter implementation to convert a Structurizr {@link com.structurizr.Workspace} into a Plantuml .puml file and have it rendered as SVG.
 * If Graphviz is specified as {@link PlantumlLayoutEngine}, an installation of it is necessary.
 *
 * @author Stephan Pirnbaum
 */
@Slf4j
public class PlantUMLExporter extends AbstractBuildInDiagramExporter {

    private final PlantumlLayoutEngine plantumlLayoutEngine;

    public PlantUMLExporter(PlantumlLayoutEngine plantumlLayoutEngine) {
        super(".puml");
        this.plantumlLayoutEngine = plantumlLayoutEngine;
    }

    @Override
    protected DiagramExporter getExporter() {
        return new ConfigurableC4PlantUMLExporter(this.plantumlLayoutEngine);
    }

    @Override
    protected Path render(Diagram diagram, Path outputFilePath) throws StructurizrRenderingException {
        String plantUmlSource = diagram.getDefinition();
        try (OutputStream os = Files.newOutputStream(outputFilePath)) {
            SourceStringReader reader = new SourceStringReader(plantUmlSource);
            reader.outputImage(os, new FileFormatOption(FileFormat.SVG));
            return outputFilePath;
        } catch (IOException e) {
            throw new StructurizrRenderingException("Failed to write file during rendering of PlantUML diagram", e);
        }
    }

    @Override
    protected String getRendererString() {
        return "C4-PlantUML(" + plantumlLayoutEngine.getRepresentation() + ")";
    }
}
