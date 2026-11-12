package dev.liveftl.preview.render;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.xhtmlrenderer.pdf.ITextRenderer;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public final class PdfTemplatePreviewRenderer {
    private static final float PREVIEW_DPI = 96;

    private PdfTemplatePreviewRenderer() {
    }

    static String render(String html) throws IOException {
        byte[] pdf = createPdf(html);
        return renderPdfPreview(pdf);
    }

    public static byte[] createPdf(String html) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.createPDF(output);
            return output.toByteArray();
        } catch (RuntimeException exception) {
            throw new IOException("Flying Saucer could not render this template: " + exception.getMessage(), exception);
        }
    }

    public static java.util.List<byte[]> renderPngPages(String html) throws IOException {
        return renderPngPages(createPdf(html), 150);
    }

    private static String renderPdfPreview(byte[] pdf) throws IOException {
        java.util.List<byte[]> pages = renderPngPages(pdf, PREVIEW_DPI);
        StringBuilder preview = new StringBuilder("""
                <!doctype html>
                <html><head><meta charset="UTF-8">
                <style>
                :root{color-scheme:light;background:#73777b}
                body{margin:0;padding:24px 0;background:#73777b}
                img{display:block;max-width:calc(100vw - 32px);height:auto;width:auto;margin:0 auto 24px;background:#fff;box-shadow:0 2px 12px #2228}
                </style></head><body>
                """);
        for (int page = 0; page < pages.size(); page++) {
            preview.append("<img alt=\"PDF page ")
                    .append(page + 1)
                    .append("\" src=\"data:image/png;base64,")
                    .append(Base64.getEncoder().encodeToString(pages.get(page)))
                    .append("\">");
        }
        return preview.append("</body></html>").toString();
    }

    private static java.util.List<byte[]> renderPngPages(byte[] pdf, float dpi) throws IOException {
        try (PDDocument document = PDDocument.load(pdf)) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            java.util.List<byte[]> pages = new java.util.ArrayList<>(document.getNumberOfPages());
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                try (ByteArrayOutputStream imageOutput = new ByteArrayOutputStream()) {
                    if (!ImageIO.write(pdfRenderer.renderImageWithDPI(page, dpi), "png", imageOutput)) {
                        throw new IOException("No PNG encoder is available.");
                    }
                    pages.add(imageOutput.toByteArray());
                }
            }
            return pages;
        }
    }
}
