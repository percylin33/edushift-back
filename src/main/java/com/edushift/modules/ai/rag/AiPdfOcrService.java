package com.edushift.modules.ai.rag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Runs OCR for sparse PDF pages using the local Tesseract installation. */
@Component
public class AiPdfOcrService {

    private static final Pattern SAFE_LANGUAGES = Pattern.compile("[a-zA-Z0-9_+.-]+");

    private final boolean enabled;
    private final String command;
    private final String languages;
    private final int dpi;
    private final int maxPages;
    private final Duration timeout;
    private final Duration maxDuration;

    public AiPdfOcrService(
            @Value("${app.ai.source-ocr.enabled:true}") boolean enabled,
            @Value("${app.ai.source-ocr.command:tesseract}") String command,
            @Value("${app.ai.source-ocr.languages:spa+eng}") String languages,
            @Value("${app.ai.source-ocr.dpi:250}") int dpi,
            @Value("${app.ai.source-ocr.max-pages:100}") int maxPages,
            @Value("${app.ai.source-ocr.timeout-seconds:20}") long timeoutSeconds,
            @Value("${app.ai.source-ocr.max-duration-seconds:180}") long maxDurationSeconds) {
        this.enabled = enabled;
        this.command = command;
        this.languages = languages;
        this.dpi = dpi;
        this.maxPages = maxPages;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.maxDuration = Duration.ofSeconds(maxDurationSeconds);
        if (command.isBlank() || !SAFE_LANGUAGES.matcher(languages).matches()
                || dpi < 100 || dpi > 400 || maxPages < 1 || maxPages > 1000
                || timeoutSeconds < 1 || timeoutSeconds > 120
                || maxDurationSeconds < 1 || maxDurationSeconds > 1800) {
            throw new IllegalArgumentException("Invalid PDF OCR configuration");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int maxPages() {
        return maxPages;
    }

    public Duration maxDuration() {
        return maxDuration;
    }

    public String recognize(PDDocument document, int zeroBasedPage) throws IOException,
            InterruptedException {
        Path workDirectory = Files.createTempDirectory("edushift-ai-ocr-");
        try {
            Path image = workDirectory.resolve("page.png");
            Path output = workDirectory.resolve("page.txt");
            Path error = workDirectory.resolve("tesseract.log");
            var cropBox = document.getPage(zeroBasedPage).getCropBox();
            float scale = dpi / 72.0f;
            double estimatedPixels = cropBox.getWidth() * scale * cropBox.getHeight() * scale;
            double maxPixels = 16_000_000d;
            if (estimatedPixels > maxPixels) {
                scale *= (float) Math.sqrt(maxPixels / estimatedPixels);
            }
            boolean rendered = javax.imageio.ImageIO.write(
                    new PDFRenderer(document).renderImage(zeroBasedPage, scale), "png", image.toFile());
            if (!rendered) throw new IOException("PNG renderer is unavailable");

            Process process = new ProcessBuilder(command, image.toString(), "stdout",
                    "-l", languages, "--psm", "3")
                    .redirectOutput(output.toFile())
                    .redirectError(error.toFile())
                    .start();
            boolean completed;
            try {
                completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                throw ex;
            }
            if (!completed) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
                throw new IOException("Tesseract exceeded its page timeout");
            }
            if (process.exitValue() != 0) {
                String details = Files.exists(error) ? Files.readString(error).trim() : "";
                throw new IOException("Tesseract exited with code " + process.exitValue()
                        + (details.isBlank() ? "" : ": " + details));
            }
            return Files.exists(output) ? Files.readString(output).trim() : "";
        } finally {
            try (var paths = Files.walk(workDirectory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // Temporary OCR files are best-effort cleanup after the job.
                    }
                });
            } catch (IOException ignored) {
                // Temporary OCR files are best-effort cleanup after the job.
            }
        }
    }
}
