package com.edushift.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Standard API response wrapper. Every controller returns an
 * {@code ApiResponse<T>} with a stable shape:
 * <pre>{@code
 * { "success": true, "message": null, "data": T, "meta": { ... }, "timestamp": "..." }
 * }</pre>
 *
 * <p>{@code meta} is only set for paginated endpoints. The FE relies
 * on this shape to handle responses uniformly.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Meta meta,
        Instant timestamp
) {

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(true, null, null, null, Instant.now());
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, null, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, message, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> ok(T data, Meta meta) {
        return new ApiResponse<>(true, null, data, meta, Instant.now());
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, "[" + code + "] " + message, null, null, Instant.now());
    }

    /** Pagination / list metadata. */
    public record Meta(long total, int page, int size, int totalPages) {
        public static <T> Meta of(Page<T> page) {
            return new Meta(
                    page.getTotalElements(),
                    page.getNumber(),
                    page.getSize(),
                    page.getTotalPages());
        }
    }
}
