package com.edushift.modules.ai.rag;

public record AiPlatformSourceReviewResponse(
        String extractedTextPreview,
        int indexedChunks,
        boolean previewTruncated
) {
    public record TextChunk(Integer pageNumber, String content) {}
}
