package io.github.stephanpirnbaum.structurizr.renderer.structurizr;

import com.microsoft.playwright.*;
import com.microsoft.playwright.impl.driver.Driver;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.structurizr.Workspace;
import com.structurizr.util.WorkspaceUtils;
import io.github.stephanpirnbaum.structurizr.renderer.AbstractDiagramExporter;
import io.github.stephanpirnbaum.structurizr.renderer.HashingUtil;
import io.github.stephanpirnbaum.structurizr.renderer.StructurizrRenderingException;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Exporter implementation to convert a Structurizr {@link com.structurizr.Workspace} into a SVG using the native rendering mechanism also used in Structurizr UI.
 * If the workspace is given as a json including layout information, e.g. from a manual layout from the UI, this layout will be used for rendering.
 * If instead no layout is given, one will be generated using GraphViz. Therefore, a GraphViz installation is necessary.
 *
 *
 * @author Stephan Pirnbaum
 */
@Slf4j
public class StructurizrExporter extends AbstractDiagramExporter {

    public StructurizrExporter(boolean installBrowser) throws StructurizrRenderingException {
        if (installBrowser) {
            try {
                installBrowser(new String[]{"install", "chromium", "--with-deps", "--only-shell"});
            } catch (IOException | InterruptedException e) {
                throw new StructurizrRenderingException("Could not install Chromium", e);
            }
        }
    }

    @Override
    public Path export(Path workspacePath, Workspace workspace, Path workspaceJsonPath, File outputDir, String viewKey) throws StructurizrRenderingException {
        String wsContent;

        try {
            Path resource = extractHtmlExporterResources(outputDir);
            Path html = Paths.get(resource.toString(), "diagram-basic.html").toAbsolutePath();

            if (workspaceJsonPath == null) {
                wsContent = WorkspaceUtils.toJson(workspace, true);
            } else {
                log.info("Workspace layout file provided. Using this instead of the Workspace DSL");
                wsContent = Files.readString(workspaceJsonPath);
            }

            String url = "file://" + html.toString().replace('\\', '/');
            log.debug("Opening: " + url);

            Map<String, String> config = new HashMap<>();
            config.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
            try (Playwright pw = Playwright.create(new Playwright.CreateOptions().setEnv(config))) {
                BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions().setHeadless(true);
                try (Browser b = pw.chromium().launch(opts)) {
                    Page page = loadPage(b, wsContent, url);

                    Map<String, String> views = (Map<String, String>) page.evaluate("() => resolveViews()");
                    log.info("Rendering views: {}", views.keySet());
                    // Rendering a diagram this way is expensive as of the browser overhead. Therefore, render all diagrams.
                    Map<String, Path> result = new HashMap<>();
                    if (views == null || views.isEmpty()) {
                        throw new StructurizrRenderingException("No views defined in workspace-file. Nothing generated.");
                    } else if (!views.containsKey(viewKey)) {
                        throw new StructurizrRenderingException("No view with key " + viewKey + " in provided workspace-file. Nothing generated.");
                    } else {
                        String hash;
                        Path outputFile;
                        Path outputHashFile;
                        for (Map.Entry<String, String> entry : views.entrySet()) {
                            hash = HashingUtil.buildHash(workspacePath, workspaceJsonPath, entry.getKey(), getRendererString());
                            outputFile = constructOutputFilePath(outputDir, entry.getKey());
                            outputHashFile = constructOutputHashFilePath(outputFile, hash);

                            exportView(page, outputFile, outputHashFile, hash, entry.getKey());
                            result.put(entry.getKey(), outputFile);
                        }
                    }
                    return result.get(viewKey);
                }
            }
        } catch (Exception e) {
            throw new StructurizrRenderingException("Failed to export workspace to SVG", e);
        }
    }

    @Override
    protected String getRendererString() {
        return "Structurizr";
    }

    private Page loadPage(Browser browser, String wsContent, String url) {
        BrowserContext ctx = browser.newContext(new Browser.NewContextOptions().setViewportSize(1920, 1080));
        Page page = ctx.newPage();

        page.onConsoleMessage(msg -> log.debug(String.format("[console.%s] %s%n", msg.type(), msg.text())));

        page.onPageError(err -> log.warn("[pageerror] " + err));

        final String BIG_BANK_URL =
                "https://raw.githubusercontent.com/structurizr/ui/main/examples/big-bank-plc.json";
        ctx.route(BIG_BANK_URL, route -> {
            route.fulfill(new Route.FulfillOptions()
                    .setStatus(200)
                    .setContentType("application/json")
                    .setBody(wsContent));
        });

        page.navigate(url);
        return page;
    }

    private void exportView(Page page, Path outputFile, Path outputHashFile, String hash, String key) throws IOException {
        page.evaluate("(k) => changeView(k)", key);

        // wait for rendered diagram
        page.locator("#diagram svg")
                .waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(30000));

        String svg = (String) page.evaluate("() => exportSvg()");
        if (svg == null) {
            log.warn("SVG not retrieved for view {} – skipping.", key);
        } else {
            svg = normalizeSvgSize(svg);

            writeFile(svg, outputFile, outputHashFile);

            this.cache.put(key, new AbstractMap.SimpleEntry<>(hash, svg));
            log.info("Exported: {}", outputFile.toAbsolutePath());
        }
    }

    private Path extractHtmlExporterResources(File outputDir) throws IOException {
        final String resourceDir = "structurizr";
        final Path targetDir = Paths.get(outputDir.getPath(), "workdir");

        ClassLoader cl = getClass().getClassLoader();
        URL url = cl.getResource(resourceDir);
        if (url != null) {
            try {
                URI uri = url.toURI();
                Files.createDirectories(targetDir);

                try (FileSystem fs = FileSystems.newFileSystem(uri, Map.of())) {
                    Path jarPath = fs.getPath("/" + resourceDir);
                    copyRecursively(jarPath, targetDir);
                }
            } catch (Exception e) {
                throw new IOException("Invalid resource URI for " + resourceDir, e);
            }
        } else {
            throw new IOException("Resource directory not found on classpath: " + resourceDir);
        }

        return targetDir;
    }

    private void copyRecursively(Path src, Path dest) throws IOException {
        try (var stream = Files.walk(src)) {
            stream.forEach(p -> {
                try {
                    Path rel = src.relativize(p);
                    Path out = dest.resolve(rel.toString());
                    if (Files.isDirectory(p)) {
                        Files.createDirectories(out);
                    } else {
                        Files.createDirectories(out.getParent());
                        Files.copy(p, out, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private int installBrowser(String[] args) throws IOException, InterruptedException {
        log.info("Installing Chromium via Playwright");
        // mimic behaviour from com.microsoft.playwright.CLI#main
        // see: https://playwright.dev/java/docs/browsers
        Driver driver = Driver.ensureDriverInstalled(Collections.emptyMap(), false);
        ProcessBuilder pb = driver.createProcessBuilder();
        pb.command().addAll(Arrays.asList(args));
        String version = Playwright.class.getPackage().getImplementationVersion();
        if (version != null) {
            pb.environment().put("PW_CLI_DISPLAY_VERSION", version);
        }

        pb.inheritIO();
        Process process = pb.start();
        return process.waitFor();
    }

    /*
     * Structurizr exports SVG with 100% width / height, leading to rendering issues in some tools
     */
    private String normalizeSvgSize(String svg) {
        Matcher styleW = Pattern.compile("width:\\s*(\\d+(?:\\.\\d+)?)px").matcher(svg);
        Matcher styleH = Pattern.compile("height:\\s*(\\d+(?:\\.\\d+)?)px").matcher(svg);

        String w = styleW.find() ? styleW.group(1) : null;
        String h = styleH.find() ? styleH.group(1) : null;

        if (w == null || h == null) {
            log.warn("Unable to normalize SVG size. Some viewers may have issues showing the diagram correctly.");
            return svg;
        }

        if (svg.matches("(?s).*\\bwidth\\s*=\\s*\"[^\"]*\".*")) {
            svg = svg.replaceFirst("\\bwidth\\s*=\\s*\"[^\"]*\"", "width=\"" + w + "px\"");
        }

        if (svg.matches("(?s).*\\bheight\\s*=\\s*\"[^\"]*\".*")) {
            svg = svg.replaceFirst("\\bheight\\s*=\\s*\"[^\"]*\"", "height=\"" + h + "px\"");
        }

        return svg;
    }



}
