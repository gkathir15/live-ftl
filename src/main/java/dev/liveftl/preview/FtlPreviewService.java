package dev.liveftl.preview;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.Alarm;
import dev.liveftl.preview.PreviewMode;
import dev.liveftl.preview.render.FtlTemplateRenderer;
import dev.liveftl.preview.render.PdfTemplatePreviewRenderer;
import dev.liveftl.preview.ui.FtlPreviewPanel;
import dev.liveftl.preview.ui.FtlPreviewPanel.ExportFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class FtlPreviewService implements Disposable {
    private final Project project;
    private final Alarm renderAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
    private final AtomicLong renderSequence = new AtomicLong();
    private final DocumentListener documentListener = new DocumentListener() {
        @Override
        public void documentChanged(@NotNull DocumentEvent event) {
            scheduleRender();
        }
    };

    private Document activeDocument;
    private VirtualFile activeFile;
    private FtlPreviewPanel panel;
    private String latestHtml = "";
    private String latestRenderedContent = "";
    private String latestStatus = "Open an .ftl file to see its preview.";
    private boolean latestRenderSuccessful;
    private PreviewMode previewMode = PreviewMode.HTML;

    public FtlPreviewService(@NotNull Project project) {
        this.project = project;
        project.getMessageBus().connect(this).subscribe(
                FileEditorManagerListener.FILE_EDITOR_MANAGER,
                new FileEditorManagerListener() {
                    @Override
                    public void selectionChanged(@NotNull FileEditorManagerEvent event) {
                        selectFile(event.getNewFile());
                    }
                }
        );
    }

    public void start() {
        VirtualFile[] selectedFiles = FileEditorManager.getInstance(project).getSelectedFiles();
        selectFile(selectedFiles.length == 0 ? null : selectedFiles[0]);
    }

    public void setPanel(@NotNull FtlPreviewPanel panel) {
        this.panel = panel;
        panel.setModeListener(this::setPreviewMode);
        panel.setExportListener(this::exportPreview);
        panel.setPreviewMode(previewMode);
        panel.showPreview(latestHtml, latestStatus);
    }

    private void selectFile(@Nullable VirtualFile file) {
        detachDocument();
        latestRenderedContent = "";
        latestRenderSuccessful = false;
        activeFile = isFtlFile(file) ? file : null;
        previewMode = defaultModeFor(activeFile);
        if (panel != null) {
            panel.setPreviewMode(previewMode);
        }
        if (activeFile == null) {
            updatePreview("", "Select an .ftl file to see its preview.");
            return;
        }

        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("FTL Preview");
        if (toolWindow != null && !toolWindow.isVisible()) {
            toolWindow.show(null);
        }

        activeDocument = FileDocumentManager.getInstance().getDocument(activeFile);
        if (activeDocument == null) {
            updatePreview("", "Unable to read the selected FTL file.");
            return;
        }
        activeDocument.addDocumentListener(documentListener, this);
        scheduleRender();
    }

    private void scheduleRender() {
        VirtualFile file = activeFile;
        Document document = activeDocument;
        if (file == null || document == null) {
            return;
        }

        latestRenderedContent = "";
        latestRenderSuccessful = false;
        String source = document.getText();
        long sequence = renderSequence.incrementAndGet();
        renderAlarm.cancelAllRequests();
        renderAlarm.addRequest(() -> {
            PreviewMode mode = previewMode;
            FtlTemplateRenderer.RenderResult result = FtlTemplateRenderer.render(project, file, source, mode);
            ApplicationManager.getApplication().invokeLater(() -> {
                if (!project.isDisposed() && sequence == renderSequence.get() && file.equals(activeFile)) {
                    latestRenderedContent = result.renderedContent();
                    latestRenderSuccessful = result.status().equals("Preview updated.");
                    updatePreview(result.html(), mode == PreviewMode.PDF
                            ? "PDF: " + result.status()
                            : "HTML: " + result.status());
                }
            });
        }, 250);
    }

    private void setPreviewMode(PreviewMode mode) {
        if (previewMode != mode) {
            previewMode = mode;
            scheduleRender();
        }
    }

    private void updatePreview(@NotNull String html, @NotNull String status) {
        latestHtml = html;
        latestStatus = status;
        if (panel != null) {
            panel.showPreview(html, status);
        }
    }

    private void exportPreview(@NotNull ExportFormat format) {
        String content = latestRenderedContent;
        VirtualFile file = activeFile;
        if (!latestRenderSuccessful || file == null) {
            setStatusMessage("Nothing to export; wait for a successful preview.");
            return;
        }

        String baseName = file.getNameWithoutExtension();
        String extension = switch (format) {
            case HTML -> "html";
            case PDF -> "pdf";
            case PNG -> "png";
        };
        FileSaverDescriptor descriptor = new FileSaverDescriptor(
                "Save Preview",
                "Choose where to save the rendered preview",
                extension
        );
        var wrapper = FileChooserFactory.getInstance()
                .createSaveFileDialog(descriptor, project)
                .save((Path) null, baseName + "." + extension);
        if (wrapper == null) {
            return;
        }

        Path destination = wrapper.getFile().toPath();
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                if (format == ExportFormat.HTML) {
                    Files.writeString(destination, content, StandardCharsets.UTF_8);
                    reportExportSuccess(destination.toString());
                } else if (format == ExportFormat.PDF) {
                    Files.write(destination, PdfTemplatePreviewRenderer.createPdf(content));
                    reportExportSuccess(destination.toString());
                } else {
                    List<byte[]> pages = PdfTemplatePreviewRenderer.renderPngPages(content);
                    if (pages.isEmpty()) {
                        throw new IOException("The rendered PDF contains no pages.");
                    }
                    List<Path> paths = pngPaths(destination, pages.size());
                    for (int index = 0; index < pages.size(); index++) {
                        Files.write(paths.get(index), pages.get(index));
                    }
                    reportExportSuccess(paths.size() == 1
                            ? paths.get(0).toString()
                            : paths.size() + " PNG pages saved beside " + destination);
                }
            } catch (IOException | RuntimeException exception) {
                reportExportError(exception);
            }
        });
    }

    private void reportExportSuccess(String destination) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!project.isDisposed() && panel != null) {
                panel.setStatusMessage("Saved preview to " + destination);
            }
        });
    }

    private void reportExportError(Exception exception) {
        String message = exception.getMessage();
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!project.isDisposed() && panel != null) {
                panel.setStatusMessage("Export error: "
                        + (message == null ? exception.getClass().getSimpleName() : message));
            }
        });
    }

    private static List<Path> pngPaths(Path destination, int pageCount) {
        if (pageCount == 1) {
            return List.of(destination);
        }
        String filename = destination.getFileName().toString();
        String baseName = filename.toLowerCase().endsWith(".png")
                ? filename.substring(0, filename.length() - 4)
                : filename;
        Path parent = destination.getParent();
        if (parent == null) {
            parent = Path.of("");
        }
        Path outputDirectory = parent;
        return java.util.stream.IntStream.rangeClosed(1, pageCount)
                .mapToObj(page -> outputDirectory.resolve(baseName + "-" + page + ".png"))
                .toList();
    }

    private void setStatusMessage(String message) {
        latestStatus = message;
        if (panel != null) {
            panel.setStatusMessage(message);
        }
    }

    private void detachDocument() {
        if (activeDocument != null) {
            activeDocument.removeDocumentListener(documentListener);
            activeDocument = null;
        }
        renderSequence.incrementAndGet();
        renderAlarm.cancelAllRequests();
    }

    private static boolean isFtlFile(@Nullable VirtualFile file) {
        return file != null && "ftl".equalsIgnoreCase(file.getExtension());
    }

    private PreviewMode defaultModeFor(@Nullable VirtualFile file) {
        String basePath = project.getBasePath();
        if (file != null
                && basePath != null
                && "pdf-generator".equalsIgnoreCase(Path.of(basePath).getFileName().toString())) {
            return PreviewMode.PDF;
        }
        return PreviewMode.HTML;
    }

    @Override
    public void dispose() {
        detachDocument();
        panel = null;
    }
}
