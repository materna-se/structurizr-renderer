package io.github.stephanpirnbaum.structurizr.renderer.plantuml;

import com.structurizr.export.IndentingWriter;
import com.structurizr.export.plantuml.C4PlantUMLExporter;
import com.structurizr.view.ModelView;
import lombok.RequiredArgsConstructor;

/**
 * Custom override of the {@link C4PlantUMLExporter} to support the specification of the layouting algorithm.
 *
 * @author Stephan Pirnbaum
 */
@RequiredArgsConstructor
public class ConfigurableC4PlantUMLExporter extends C4PlantUMLExporter {

    private final PlantumlLayoutEngine plantumlLayoutEngine;

    @Override
    protected void writeHeader(ModelView view, IndentingWriter writer) {
        super.writeHeader(view, writer);
        writer.writeLine("!pragma layout " + plantumlLayoutEngine.getRepresentation());
    }
}
