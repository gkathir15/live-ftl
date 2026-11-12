package dev.liveftl.preview.render;

import dev.liveftl.preview.PreviewMode;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import freemarker.cache.FileTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.StringTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.Version;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FtlTemplateRenderer {
    private FtlTemplateRenderer() {
    }

    public static RenderResult render(Project project, VirtualFile file, String source, PreviewMode mode) {
        try {
            Path templatePath = Path.of(file.getPath());
            Path projectBase = ReadAction.compute(() -> {
                String basePath = project.getBasePath();
                return basePath == null ? null : Path.of(basePath);
            });
            List<Path> contentRoots = ReadAction.compute(() -> {
                VirtualFile[] roots = ProjectRootManager.getInstance(project).getContentRoots();
                List<Path> paths = new ArrayList<>(roots.length);
                for (VirtualFile root : roots) {
                    paths.add(Path.of(root.getPath()));
                }
                return paths;
            });
            Path templateRoot = projectBase != null && templatePath.startsWith(projectBase) ? projectBase
                    : contentRoots.stream()
                    .filter(templatePath::startsWith)
                    .max(Comparator.comparingInt(Path::getNameCount))
                    .orElse(templatePath.getParent());
            if (templateRoot == null) {
                return RenderResult.error("Cannot determine the template directory.");
            }
            String templateName = templateRoot.relativize(templatePath).toString()
                    .replace(FileSystems.getDefault().getSeparator(), "/");

            Configuration configuration = new Configuration(new Version("2.3.34"));
            configuration.setDefaultEncoding(StandardCharsets.UTF_8.name());
            configuration.setTemplateExceptionHandler(
                    freemarker.template.TemplateExceptionHandler.RETHROW_HANDLER);
            configuration.setLogTemplateExceptions(false);
            configuration.setWrapUncheckedExceptions(true);

            StringTemplateLoader editedTemplate = new StringTemplateLoader();
            editedTemplate.putTemplate(templateName, source);
            configuration.setTemplateLoader(new MultiTemplateLoader(new TemplateLoader[]{
                    editedTemplate,
                    new FileTemplateLoader(templateRoot.toFile())
            }));

            Template template = configuration.getTemplate(templateName);
            StringWriter output = new StringWriter();
            template.process(VariableNameTemplateModel.root(), output);
            String html = output.toString();
            return RenderResult.success(
                    mode == PreviewMode.PDF ? PdfTemplatePreviewRenderer.render(html) : html,
                    html
            );
        } catch (IOException | TemplateException | RuntimeException exception) {
            String message = exception.getMessage();
            return RenderResult.error(message == null ? exception.getClass().getSimpleName() : message);
        }
    }

    public record RenderResult(String html, String renderedContent, String status) {
        private static RenderResult success(String html, String renderedContent) {
            return new RenderResult(html, renderedContent, "Preview updated.");
        }

        private static RenderResult error(String message) {
            return new RenderResult("", "", "Render error: " + message);
        }
    }
}
