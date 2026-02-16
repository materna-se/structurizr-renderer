package de.materna.structurizr.renderer;

import de.materna.structurizr.renderer.mermaid.MermaidExporter;
import de.materna.structurizr.renderer.plantuml.PlantUMLExporter;
import de.materna.structurizr.renderer.plantuml.PlantumlLayoutEngine;
import de.materna.structurizr.renderer.structurizr.StructurizrExporter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Map;

/**
 * Entry point for rendering Structurizr workspaces.
 *
 * @author Stephan Pirnbaum
 */
@Slf4j
public class WorkspaceRenderer {

    // Cache expensive exporters (Playwright installation)
    private StructurizrExporter structurizrExporter;

    public Map<String, Path> render(@NonNull Path workspaceDslPath,
                                    @Nullable Path workspaceJsonPath,
                                    @NonNull Path outputDir,
                                    @Nullable String viewKey,
                                    @Nullable Renderer renderer,
                                    @Nullable PlantumlLayoutEngine plantumlLayoutEngine,
                                    @Nullable String playwrightWsEndpoint) throws StructurizrRenderingException {
        if (renderer == null) {
            log.info("No renderer for view {} provided. Using Structurizr.", viewKey);
            renderer = Renderer.STRUCTURIZR;
        }
        if (renderer == Renderer.PLANTUML_C4 && plantumlLayoutEngine == null) {
            log.info("No PlantUML layout engine provided for view {}. Using Graphviz.", viewKey);
        }
        AbstractDiagramExporter diagramExporter = resolveDiagramExporter(
                renderer,
                plantumlLayoutEngine != null ? plantumlLayoutEngine : PlantumlLayoutEngine.GRAPHVIZ,
                playwrightWsEndpoint
        );

        log.debug("Rendering view with key {} using engine {}", viewKey, renderer);

        return diagramExporter.export(workspaceDslPath, workspaceJsonPath, outputDir.toFile(), viewKey);
    }

    private AbstractDiagramExporter resolveDiagramExporter(@NonNull Renderer renderer, @NonNull PlantumlLayoutEngine plantumlLayoutEngine, @Nullable String playwrightWsEndpoint) throws StructurizrRenderingException {
        return switch (renderer) {
            case PLANTUML_C4 -> new PlantUMLExporter(plantumlLayoutEngine);
            case MERMAID -> new MermaidExporter();
            case STRUCTURIZR -> {
                if (this.structurizrExporter == null) {
                    this.structurizrExporter = new StructurizrExporter(playwrightWsEndpoint);
                }
                yield this.structurizrExporter;
            }
        };
    }

}
