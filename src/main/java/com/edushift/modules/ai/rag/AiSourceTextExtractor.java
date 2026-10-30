package com.edushift.modules.ai.rag;

import com.edushift.shared.exception.BadRequestException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiSourceTextExtractor {

    private static final int MAX_PAGES = 1000;
    private static final int MAX_TEXT_CHARS = 5_000_000;
    private static final int MIN_PAGE_TEXT_CHARS_BEFORE_OCR = 40;

    private final AiPdfOcrService pdfOcr;

    public List<PageText> extract(byte[] bytes, String contentType) {
        return extractWithDiagnostics(bytes, contentType).pages();
    }

    public ExtractionResult extractWithDiagnostics(byte[] bytes, String contentType) {
        String mime = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT)
                .split(";", 2)[0].trim();
        try {
            ExtractionResult result = switch (mime) {
                case "application/pdf" -> extractPdf(bytes);
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                        asResult(extractDocx(bytes));
                case "text/plain" -> asResult(List.of(new PageText(null,
                        new String(bytes, StandardCharsets.UTF_8))));
                case "text/html" -> asResult(List.of(new PageText(null,
                        Jsoup.parse(new String(bytes, StandardCharsets.UTF_8)).text())));
                default -> throw new BadRequestException(
                        "AI_SOURCE_UNSUPPORTED", "Solo se admiten PDF, DOCX, TXT y HTML.");
            };
            if (result.pages().stream().mapToLong(p -> p.text().length()).sum() > MAX_TEXT_CHARS) {
                throw textTooLarge();
            }
            return result;
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof BadRequestException requestException) throw requestException;
            throw new BadRequestException("AI_SOURCE_INVALID",
                    "No se pudo extraer texto del archivo.", ex);
        }
    }

    private ExtractionResult extractPdf(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.getNumberOfPages() > MAX_PAGES) {
                throw new BadRequestException("AI_SOURCE_TOO_LARGE",
                        "El PDF supera el límite de 1000 páginas.");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            // Extract the document once. Calling getText(document) once per page
            // makes PDFBox walk the page tree repeatedly and can turn large PDFs
            // into a quadratic-time job. A form-feed separator preserves page
            // boundaries while keeping extraction linear in the document size.
            stripper.setPageEnd("\f");
            String[] pageTexts = stripper.getText(document).split("\f", -1);
            List<PageText> pages = new ArrayList<>();
            List<Integer> ocrPages = new ArrayList<>();
            List<Integer> ocrFailedPages = new ArrayList<>();
            List<Integer> ocrSkippedPages = new ArrayList<>();
            List<Integer> unreadablePages = new ArrayList<>();
            int chars = 0;
            int pageCount = document.getNumberOfPages();
            int digitalTextPages = 0;
            int ocrAttempts = 0;
            long ocrStartedAt = System.nanoTime();
            for (int p = 0; p < pageCount; p++) {
                String text = (p < pageTexts.length ? pageTexts[p] : "").trim();
                if (!text.isBlank()) digitalTextPages++;
                chars += text.length();
                if (chars > MAX_TEXT_CHARS) throw textTooLarge();

                if (text.length() < MIN_PAGE_TEXT_CHARS_BEFORE_OCR) {
                    boolean withinOcrBudget = System.nanoTime() - ocrStartedAt
                            < pdfOcr.maxDuration().toNanos();
                    if (pdfOcr.isEnabled() && ocrAttempts < pdfOcr.maxPages() && withinOcrBudget) {
                        ocrAttempts++;
                        try {
                            String recognized = pdfOcr.recognize(document, p);
                            if (!recognized.isBlank()) {
                                text = text.isBlank() ? recognized : text + "\n" + recognized;
                                ocrPages.add(p + 1);
                                chars += recognized.length();
                                if (chars > MAX_TEXT_CHARS) throw textTooLarge();
                            } else {
                                unreadablePages.add(p + 1);
                            }
                        } catch (BadRequestException ex) {
                            throw ex;
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            unreadablePages.add(p + 1);
                            for (int remaining = p + 1; remaining < pageCount; remaining++) {
                                if ((remaining < pageTexts.length ? pageTexts[remaining] : "").isBlank()) {
                                    unreadablePages.add(remaining + 1);
                                }
                            }
                            break;
                        } catch (IOException | RuntimeException ex) {
                            ocrFailedPages.add(p + 1);
                        }
                    } else if (!pdfOcr.isEnabled() || ocrAttempts >= pdfOcr.maxPages()
                            || !withinOcrBudget) {
                        ocrSkippedPages.add(p + 1);
                        unreadablePages.add(p + 1);
                    }
                }
                if (!text.isBlank()) pages.add(new PageText(p + 1, text));
            }
            return new ExtractionResult(pages, pageCount, digitalTextPages,
                    List.copyOf(ocrPages), List.copyOf(ocrFailedPages), List.copyOf(ocrSkippedPages),
                    List.copyOf(unreadablePages));
        }
    }

    private static ExtractionResult asResult(List<PageText> pages) {
        int textPages = (int) pages.stream().filter(page -> !page.text().isBlank()).count();
        return new ExtractionResult(pages, pages.size(), textPages, List.of(), List.of(), List.of(), List.of());
    }

    private List<PageText> extractDocx(byte[] bytes) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            String text = extractor.getText();
            if (text.length() > MAX_TEXT_CHARS) throw textTooLarge();
            return List.of(new PageText(null, text));
        }
    }

    private static BadRequestException textTooLarge() {
        return new BadRequestException("AI_SOURCE_TOO_LARGE",
                "El texto extraído supera el límite de 5 millones de caracteres.");
    }

    public record PageText(Integer pageNumber, String text) {}

    public record ExtractionResult(List<PageText> pages, int totalPages, int digitalTextPages,
                                   List<Integer> ocrPages, List<Integer> ocrFailedPages,
                                   List<Integer> ocrSkippedPages,
                                   List<Integer> unreadablePages) {}
}
