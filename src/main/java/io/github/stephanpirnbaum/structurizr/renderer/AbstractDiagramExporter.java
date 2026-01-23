package io.github.stephanpirnbaum.structurizr.renderer;

import com.structurizr.Workspace;
import com.structurizr.dsl.StructurizrDslParser;
import com.structurizr.dsl.StructurizrDslParserException;
import com.structurizr.view.ThemeUtils;
import com.structurizr.view.View;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static io.github.stephanpirnbaum.structurizr.renderer.HashingUtil.normalize;

/**
 * Base interface for different export strategies.
 *
 * @author Stephan Pirnbaum
 */
@Slf4j
public abstract class AbstractDiagramExporter {

    public final Map<String, Path> export(Path workspacePath, Path workspaceJsonPath, File outputDir, String viewKey) throws StructurizrRenderingException {
        log.info("Parsing Structurizr DSL: {}", workspacePath);

        try {
            Files.createDirectories(outputDir.toPath());
        } catch (IOException e) {
            throw new StructurizrRenderingException("Failed to create output directory", e);
        }

        Workspace workspace;
        try {
            String workspaceDsl = Files.readString(workspacePath);
            StructurizrDslParser parser = new StructurizrDslParser();
            parser.parse(workspaceDsl);
            workspace = parser.getWorkspace();
            ThemeUtils.loadThemes(workspace);
        } catch (IOException | StructurizrDslParserException e) {
            throw new StructurizrRenderingException("Could not read workspace dsl", e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Optional<File> workspaceJsonFile;
        if (workspaceJsonPath != null) {
            workspaceJsonFile = Optional.of(workspaceJsonPath.toFile());
        } else {
            workspaceJsonFile = Optional.empty();
        }

        Set<String> viewKeys = viewKey != null ?
                Set.of(viewKey) :
                workspace.getViews().getViews().stream().map(View::getKey).collect(Collectors.toSet());

        Map<String, Path> result = new HashMap<>();
        for (String key : viewKeys) {
            Path outputFilePath = constructOutputFilePath(outputDir, key);
            Path outputHashFilePath = constructOutputHashFilePath(workspacePath, workspaceJsonPath, outputFilePath, key);
            if (outputFilePath.toFile().exists() && outputHashFilePath.toFile().exists()) {
                // current rendered version is up-to-date
                log.info("Cache hit for view {}. Already rendered.", key);
                result.put(outputFilePath.getFileName().toString(), outputFilePath);
            } else {
                result.put(key, export(workspacePath, workspace, workspaceJsonFile, outputDir, key));
                try {
                    outputHashFilePath.toFile().createNewFile();
                    // todo old hash files must be deleted
                } catch (IOException e) {
                    throw new StructurizrRenderingException("Unable to create has file", e);
                }
            }

        }
        log.info("Export completed. SVG files in: {}", outputDir.getAbsolutePath());
        return result;
    }

    protected Path constructOutputHashFilePath(Path workspacePath, Path workspaceJsonPath, Path outputFilePath, String key) {
        String hash = buildHash(workspacePath, workspaceJsonPath, key);

        return outputFilePath.resolveSibling(outputFilePath.getFileName().toString() + "." + hash);
    }

    /**
     * Export the given workspace to the specified output directory.
     *
     * @param workspacePath The path of the workspace file.
     * @param workspace The workspace to export.
     * @param workspaceJson The workspace including layout information.
     * @param outputDir The output directory.
     * @param viewKey The key of the view to render or null, if all views should be rendered.
     *
     * @return A map of all generated files with the file name as key and the path to it as value.
     *
     * @throws StructurizrRenderingException In case the workspace could not be rendered.
     */
    protected abstract Path export(Path workspacePath, Workspace workspace, Optional<File> workspaceJson, File outputDir, String viewKey) throws StructurizrRenderingException;

    protected abstract String getHashingString();

    protected final Path constructOutputFilePath(File outputDir, String viewKey) {
        final String fileName = viewKey.replaceAll("[^a-zA-Z0-9._-]", "_");

        return outputDir.toPath().resolve(fileName + ".svg");
    }


    private String buildHash(Path workspacePath, Path workspaceJsonPath, String viewKey) {
        return HashingUtil.sha256HexConcat(md -> {
            // Renderer + Version
            md.update(normalize("renderer=" + getHashingString()));

            // View
            md.update(normalize("viewKey=" + viewKey));

            // Workspace mtime
            long wsMtime = 0;
            try { wsMtime = Files.getLastModifiedTime(workspacePath).toMillis(); } catch (Exception ignore) {}
            md.update(normalize("wsPath=" + workspacePath.toAbsolutePath()));
            md.update(normalize("wsMtime=" + wsMtime));

            // Layout mtime
            if (workspaceJsonPath != null) {
                long layoutMtime = 0;
                try { layoutMtime = Files.getLastModifiedTime(workspaceJsonPath).toMillis(); } catch (Exception ignore) {}
                md.update(normalize("wsJsonPath=" + workspaceJsonPath.toAbsolutePath()));
                md.update(normalize("wsJsonMtime=" + layoutMtime));
            }
        });
    }

}
