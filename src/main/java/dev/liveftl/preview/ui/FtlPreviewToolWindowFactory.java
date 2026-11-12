package dev.liveftl.preview.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import dev.liveftl.preview.FtlPreviewService;
import org.jetbrains.annotations.NotNull;

public final class FtlPreviewToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        FtlPreviewPanel panel = new FtlPreviewPanel();
        Content content = ContentFactory.getInstance().createContent(panel, "", false);
        content.setDisposer(panel);
        toolWindow.getContentManager().addContent(content);
        project.getService(FtlPreviewService.class).setPanel(panel);
    }
}
