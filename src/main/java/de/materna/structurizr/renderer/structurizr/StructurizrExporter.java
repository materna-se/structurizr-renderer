package de.materna.structurizr.renderer.structurizr;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.impl.driver.Driver;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.structurizr.Workspace;
import com.structurizr.util.WorkspaceUtils;
import de.materna.structurizr.renderer.AbstractDiagramExporter;
import de.materna.structurizr.renderer.HashingUtil;
import de.materna.structurizr.renderer.StructurizrRenderingException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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

    private static final String ENV_WS_ENDPOINT = "PLAYWRIGHT_WS_ENDPOINT";
    private static final String WORKDIR_ORIGIN = "http://workdir.local";

    @Getter
    private final String rendererString = "Structurizr";

    private final String playwrightWsEndpoint;

    public StructurizrExporter(String playwrightWsEndpoint) throws StructurizrRenderingException {
        this.playwrightWsEndpoint = resolveRemoteUrl(playwrightWsEndpoint);
        if (this.playwrightWsEndpoint == null) {
            // Manually download browser (chrome only) once to avoid file-system checks in further runs
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
            Path workdir = extractHtmlExporterResources(outputDir);

            if (workspaceJsonPath == null) {
                wsContent = WorkspaceUtils.toJson(workspace, true);
            } else {
                log.info("Workspace layout file provided. Using this instead of the Workspace DSL");
                wsContent = Files.readString(workspaceJsonPath);
            }

            // force skip of browser install as installation was done manually in constructor
            // otherwise, all browser instances will be downloaded
            Map<String, String> config = new HashMap<>();
            config.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");

            try (Playwright pw = Playwright.create(new Playwright.CreateOptions().setEnv(config))) {
                try (Browser b = obtainBrowser(pw)) {
                    Page page = loadPage(b, wsContent, workdir);

                    Map<String, String> views = (Map<String, String>) page.evaluate("() => resolveViews()");
                    log.info("Rendering views: {}", views.keySet());
                    if (views.isEmpty()) {
                        throw new StructurizrRenderingException("No views defined in workspace-file. Nothing generated.");
                    } else if (!views.containsKey(viewKey)) {
                        throw new StructurizrRenderingException("No view with key " + viewKey + " in provided workspace-file. Nothing generated.");
                    }

                    // Rendering a diagram this way is expensive as of the browser overhead. Therefore, render all diagrams and rely on caching in later runs.
                    Map<String, Path> result = new HashMap<>();
                    for (Map.Entry<String, String> entry : views.entrySet()) {
                        String hash = HashingUtil.buildHash(workspacePath, workspaceJsonPath, entry.getKey(), getRendererString());
                        Path outputFile = constructOutputFilePath(outputDir, entry.getKey());
                        Path outputHashFile = constructOutputHashFilePath(outputFile, hash);

                        exportView(page, outputFile, outputHashFile, hash, entry.getKey());
                        result.put(entry.getKey(), outputFile);
                    }
                    return result.get(viewKey);
                }
            }
        } catch (Exception e) {
            throw new StructurizrRenderingException("Failed to export workspace to SVG", e);
        }
    }

    private Page loadPage(Browser browser, String wsContent, Path workdir) {
        BrowserContext ctx = browser.newContext(new Browser.NewContextOptions().setViewportSize(1920, 1080));
        Page page = ctx.newPage();

        page.onConsoleMessage(msg -> log.debug(String.format("[console.%s] %s%n", msg.type(), msg.text())));

        page.onPageError(err -> log.warn("[pageerror] " + err));

        mountWorkdirViaRoute(ctx, workdir, wsContent);

        page.navigate(WORKDIR_ORIGIN + "/export.html");

        return page;
    }

    private void mountWorkdirViaRoute(BrowserContext ctx, Path workdir, String wsContent) {
        ctx.route("**/*", route -> {
            String url = route.request().url();

            if (!url.startsWith(WORKDIR_ORIGIN + "/")) {
                route.resume();
                return;
            } else if (url.endsWith("workspace.json")) {
                route.fulfill(new Route.FulfillOptions()
                        .setStatus(200)
                        .setContentType("application/json")
                        .setBody(wsContent));
                return;
            }

            try {
                URI uri = URI.create(url);
                String pathPart = uri.getPath().substring(1);
                Path requested = workdir.resolve(pathPart);

                if (!requested.startsWith(workdir)) {
                    route.fulfill(new Route.FulfillOptions().setStatus(403));
                    return;
                }
                if (!Files.exists(requested) || Files.isDirectory(requested)) {
                    route.fulfill(new Route.FulfillOptions().setStatus(404));
                    return;
                }

                byte[] bytes = Files.readAllBytes(requested);
                String contentType = Files.probeContentType(requested);

                route.fulfill(new Route.FulfillOptions()
                        .setStatus(200)
                        .setContentType(contentType)
                        .setBodyBytes(bytes)
                        .setHeaders(Map.of("Cache-Control", "no-cache")));

            } catch (Exception e) {
                route.fulfill(new Route.FulfillOptions().setStatus(500));
            }
        });
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

    private String resolveRemoteUrl(String playwrightWsEndpoint) {
        if (StringUtils.isNotBlank(playwrightWsEndpoint)) {
            return playwrightWsEndpoint;
        } else if (StringUtils.isNotBlank(System.getenv(ENV_WS_ENDPOINT))) {
            return System.getenv(ENV_WS_ENDPOINT);
        }
        return null;
    }

    private Browser obtainBrowser(Playwright pw) {
        if (StringUtils.isNotBlank(this.playwrightWsEndpoint)) {
            log.info("Connecting to Playwright Browser");
            return pw.chromium().connect(this.playwrightWsEndpoint, new BrowserType.ConnectOptions().setTimeout(30000));
        } else {
            log.info("Launching local Chromium");
            BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions().setHeadless(true);
            return pw.chromium().launch(opts);
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
