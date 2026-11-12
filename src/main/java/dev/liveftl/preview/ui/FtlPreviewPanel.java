package dev.liveftl.preview.ui;

import com.intellij.openapi.Disposable;
import com.intellij.icons.AllIcons;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.util.ui.JBUI;
import dev.liveftl.preview.PreviewMode;

import javax.swing.JButton;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JToggleButton;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Consumer;

public final class FtlPreviewPanel extends JPanel implements Disposable {
    public enum ExportFormat {
        HTML,
        PDF,
        PNG
    }

    private static final String BROWSER_DEFAULTS =
            "<style>:root{color-scheme:light;background-color:#fff}body{background-color:#fff;margin:8px}</style>";
    private static final Pattern HEAD_TAG = Pattern.compile("(?i)<head(?:\\s[^>]*)?>");
    private static final Pattern HTML_TAG = Pattern.compile("(?i)<html(?:\\s[^>]*)?>");

    private final JLabel status = new JLabel("Open an .ftl file to see its preview.");
    private final JBCefBrowser browser = new JBCefBrowser();
    private final JToggleButton htmlModeButton = new JToggleButton("HTML");
    private final JToggleButton pdfModeButton = new JToggleButton("PDF");
    private final JButton saveButton = new JButton(AllIcons.Actions.MenuSaveall);
    private Consumer<PreviewMode> modeListener = mode -> { };
    private Consumer<ExportFormat> exportListener = format -> { };

    public FtlPreviewPanel() {
        super(new BorderLayout());
        setBorder(JBUI.Borders.empty(8));
        JPanel header = new JPanel(new BorderLayout());
        JPanel modeControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        ButtonGroup modes = new ButtonGroup();
        modes.add(htmlModeButton);
        modes.add(pdfModeButton);
        htmlModeButton.setSelected(true);
        htmlModeButton.addActionListener(event -> setPreviewMode(PreviewMode.HTML, true));
        pdfModeButton.addActionListener(event -> setPreviewMode(PreviewMode.PDF, true));
        modeControls.add(htmlModeButton);
        modeControls.add(pdfModeButton);
        header.add(modeControls, BorderLayout.WEST);
        status.setBorder(JBUI.Borders.emptyLeft(8));
        header.add(status, BorderLayout.CENTER);
        saveButton.setToolTipText("Save preview as HTML, PDF, or PNG");
        saveButton.setFocusable(false);
        saveButton.addActionListener(event -> showSaveMenu());
        header.add(saveButton, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        browser.getComponent().setBackground(Color.WHITE);
        add(browser.getComponent(), BorderLayout.CENTER);
    }

    public void setModeListener(Consumer<PreviewMode> listener) {
        modeListener = listener;
    }

    public void setExportListener(Consumer<ExportFormat> listener) {
        exportListener = listener;
    }

    public void setPreviewMode(PreviewMode mode) {
        setPreviewMode(mode, false);
    }

    private void setPreviewMode(PreviewMode mode, boolean notify) {
        htmlModeButton.setSelected(mode == PreviewMode.HTML);
        pdfModeButton.setSelected(mode == PreviewMode.PDF);
        if (notify) {
            modeListener.accept(mode);
        }
    }

    public void showPreview(String html, String message) {
        setStatusMessage(message);
        browser.loadHTML(withBrowserDefaults(html.isEmpty() ? "<html><body></body></html>" : html));
    }

    public void setStatusMessage(String message) {
        status.setText(message);
    }

    private void showSaveMenu() {
        JPopupMenu menu = new JPopupMenu();
        addExportItem(menu, "HTML...", ExportFormat.HTML);
        addExportItem(menu, "PDF...", ExportFormat.PDF);
        addExportItem(menu, "PNG...", ExportFormat.PNG);
        menu.show(saveButton, 0, saveButton.getHeight());
    }

    private void addExportItem(JPopupMenu menu, String label, ExportFormat format) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(event -> exportListener.accept(format));
        menu.add(item);
    }

    private static String withBrowserDefaults(String html) {
        Matcher head = HEAD_TAG.matcher(html);
        if (head.find()) {
            return html.substring(0, head.end()) + BROWSER_DEFAULTS + html.substring(head.end());
        }

        Matcher document = HTML_TAG.matcher(html);
        if (document.find()) {
            return html.substring(0, document.end()) + BROWSER_DEFAULTS + html.substring(document.end());
        }
        return BROWSER_DEFAULTS + html;
    }

    @Override
    public void dispose() {
        browser.dispose();
    }
}
