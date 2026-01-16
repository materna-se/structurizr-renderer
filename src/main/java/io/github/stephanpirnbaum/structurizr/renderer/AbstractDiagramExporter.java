package io.github.stephanpirnbaum.structurizr.renderer;

import com.structurizr.Workspace;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Base interface for different export strategies.
 *
 * @author Stephan Pirnbaum
 */
public abstract class AbstractDiagramExporter {

    /**
     * Export the given workspace to the specified output directory.
     *
     * @param workspace The workspace to export.
     * @param workspaceJson The workspace including layout information.
     * @param outputDir The output directory.
     * @param viewKey The key of the view to render or null, if all views should be rendered.
     *
     * @return A map of all generated files with the file name as key and the path to it as value.
     *
     * @throws StructurizrRenderingException In case the workspace could not be rendered.
     */
    public abstract Map<String, Path> export(Workspace workspace, Optional<File> workspaceJson, File outputDir, String viewKey) throws StructurizrRenderingException;

}
