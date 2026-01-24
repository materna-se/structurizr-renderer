package io.github.stephanpirnbaum.structurizr.renderer;

import com.structurizr.Workspace;
import com.structurizr.dsl.StructurizrDslParser;
import com.structurizr.dsl.StructurizrDslParserException;
import com.structurizr.view.ThemeUtils;
import com.structurizr.view.View;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Base interface for different export strategies.
 *
 * @author Stephan Pirnbaum
 */
@Slf4j
public abstract class AbstractDiagramExporter {

    /**
     * Mapping from view key to an entry of hash and rendered diagram
     */
    protected final Map<String, AbstractMap.SimpleEntry<String, String>> cache = new HashMap<>();

    public final Map<String, Path> export(Path workspacePath, Path workspaceJsonPath, File outputDir, String viewKey) throws StructurizrRenderingException {
        String hash;
        Path outputFile;
        Path outputHashFile;
        AbstractMap.SimpleEntry<String, Path> cachedEntry;
        if (StringUtils.isNotEmpty(viewKey)) {
            hash = HashingUtil.buildHash(workspacePath, workspaceJsonPath, viewKey, getRendererString());
            outputFile = constructOutputFilePath(outputDir, viewKey);
            outputHashFile = constructOutputHashFilePath(outputFile, hash);

            /*
             * Always parsing the workspace is expensive especially when being run from the IntelliJ AsciiDoctor Plugin
             * Therefore, if a specific view key is given, check the in-memory cache first
             */
            cachedEntry = getFromCache(outputFile, outputHashFile, viewKey, hash);
            if (cachedEntry != null) {
                return Map.ofEntries(cachedEntry);
            }
        }

        Map<String, Path> result = new HashMap<>();

        try {
            Files.createDirectories(outputDir.toPath());
        } catch (IOException e) {
            throw new StructurizrRenderingException("Failed to create output directory", e);
        }

        Workspace workspace = parseWorkspace(workspacePath);

        Set<String> viewKeys = viewKey != null ?
                Set.of(viewKey) :
                workspace.getViews().getViews().stream().map(View::getKey).collect(Collectors.toSet());

        for (String key : viewKeys) {
            outputFile = constructOutputFilePath(outputDir, key);
            hash = HashingUtil.buildHash(workspacePath, workspaceJsonPath, key, getRendererString());
            outputHashFile = constructOutputHashFilePath(outputFile, hash);

            cachedEntry = getFromCache(outputFile, outputHashFile, key, hash);
            if (cachedEntry != null) {
                result.put(cachedEntry.getKey(), cachedEntry.getValue());
            } else {
                result.put(key, export(workspacePath, workspace, workspaceJsonPath, outputDir, key));
            }

        }
        log.info("Export completed. SVG files in: {}", outputDir.getAbsolutePath());
        return result;
    }

    protected void writeFile(String svg, Path outputFile, Path outputHashFile) throws IOException {
        if (!outputHashFile.toFile().exists()) {
            Files.writeString(outputFile, svg, StandardCharsets.UTF_8);
            outputHashFile.toFile().createNewFile();
        }
    }

    private AbstractMap.SimpleEntry<String, Path> getFromCache(Path outputFile, Path outputHashFile, String viewKey, String hash) throws StructurizrRenderingException {
        if (StringUtils.isNotEmpty(viewKey)) {
            if (this.cache.containsKey(viewKey)) {
                AbstractMap.SimpleEntry<String, String> renderedView = this.cache.get(viewKey);
                if (renderedView.getKey().equals(hash)) {
                    // we need to write the value as a file
                    try {
                        writeFile(renderedView.getValue(), outputFile, outputHashFile);
                        log.info("In-memory cache hit for view {}", viewKey);
                        return new AbstractMap.SimpleEntry<>(viewKey, outputFile);
                    } catch (IOException e) {
                        throw new StructurizrRenderingException("Unable to write cached diagram for view: " + viewKey, e);
                    }
                }
            } else if (outputFile.toFile().exists() && outputHashFile.toFile().exists()) {
                // current rendered version is up-to-date
                log.info("Cache hit for view {}. Already rendered.", viewKey);
                return new AbstractMap.SimpleEntry<>(outputFile.getFileName().toString(), outputFile);
            }
        }
        return null;
    }

    private Workspace parseWorkspace(Path workspacePath) throws StructurizrRenderingException {
        log.info("Parsing Structurizr DSL: {}", workspacePath);

        Workspace workspace;
        try {
            String workspaceDsl = Files.readString(workspacePath);
            StructurizrDslParser parser = new StructurizrDslParser();
            parser.parse(workspaceDsl, workspacePath.toFile());
            workspace = parser.getWorkspace();
            ThemeUtils.loadThemes(workspace);
        } catch (IOException | StructurizrDslParserException e) {
            throw new StructurizrRenderingException("Could not read workspace dsl", e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return workspace;
    }

    protected Path constructOutputHashFilePath(Path outputFilePath, String hash) {

        return outputFilePath.resolveSibling(outputFilePath.getFileName().toString() + "." + hash);
    }

    /**
     * Export the given workspace to the specified output directory.
     *
     * @param workspacePath The path of the workspace file.
     * @param workspace The workspace to export.
     * @param workspaceJsonPath The workspace including layout information.
     * @param outputDir The output directory.
     * @param viewKey The key of the view to render or null, if all views should be rendered.
     *
     * @return A map of all generated files with the file name as key and the path to it as value.
     *
     * @throws StructurizrRenderingException In case the workspace could not be rendered.
     */
    protected abstract Path export(Path workspacePath, Workspace workspace, Path workspaceJsonPath, File outputDir, String viewKey) throws StructurizrRenderingException;

    protected abstract String getRendererString();

    protected final Path constructOutputFilePath(File outputDir, String viewKey) {
        final String fileName = (viewKey + "_" + getRendererString()).replaceAll("[^a-zA-Z0-9._-]", "_");

        return outputDir.toPath().resolve(fileName + ".svg");
    }

}
