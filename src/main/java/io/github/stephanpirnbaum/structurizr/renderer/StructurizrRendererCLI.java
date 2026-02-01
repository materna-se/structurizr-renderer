package io.github.stephanpirnbaum.structurizr.renderer;

import io.github.stephanpirnbaum.structurizr.renderer.plantuml.PlantumlLayoutEngine;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.nio.file.Path;

/**
 * CLI application of the structurizr renderer.
 *
 * @author Stephan Pirnbaum
 */
@Slf4j
@CommandLine.Command(name = "render", description = "Renders the views of a given workspace to SVG files")
public class StructurizrRendererCLI implements Runnable {

    private final WorkspaceRenderer workspaceRenderer = new WorkspaceRenderer();

    @CommandLine.Option(names = {"-w", "--workspace"}, required = true, description = "Path to the workspace DSL file.")
    private Path workspaceDslPath;

    @CommandLine.Option(names = {"-j", "--workspaceJson"}, description = "Path to the manual layout JSON file.")
    private Path workspaceJsonPath;

    @CommandLine.Option(names = {"-o", "--outputDir"}, required = true, description = "Path to write the output to.")
    private Path outputDir;

    @CommandLine.Option(names = {"-v", "--viewKey"}, required = true, description = "The key of the view to render.")
    private String viewKey;

    @CommandLine.Option(names = {"-r", "--renderer"}, description = "The renderer to use. Defaults to STRUCTURIZR.")
    private Renderer renderer;

    @CommandLine.Option(names = {"-e", "--plantumlLayoutEngine"}, description = "The layout engine to use for the PLANTUML-C4 renderer. Defaults to GraphViz.")
    private PlantumlLayoutEngine plantumlLayoutEngine;

    public static void main(String[] args) {
        CommandLine.run(new StructurizrRendererCLI(), args);
    }

    @SneakyThrows
    @Override
    public void run() {
        this.workspaceRenderer.render(this.workspaceDslPath, this.workspaceJsonPath, this.outputDir, this.viewKey, this.renderer, this.plantumlLayoutEngine);
    }

}