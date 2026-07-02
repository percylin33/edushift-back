package com.edushift.shared.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class ApiResponseTest {

    @Test
    @DisplayName("ok() creates success response with null data, message, and meta")
    void okCreatesSuccessResponse() {
        var response = ApiResponse.ok();
        assertThat(response.success()).isTrue();
        assertThat(response.data()).isNull();
        assertThat(response.message()).isNull();
        assertThat(response.meta()).isNull();
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("ok(data) creates success response with data, null message and meta")
    void okWithData() {
        var data = List.of("a", "b");
        var response = ApiResponse.ok(data);
        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo(data);
        assertThat(response.message()).isNull();
        assertThat(response.meta()).isNull();
    }

    @Test
    @DisplayName("ok(data, message) creates success response with data and message")
    void okWithDataAndMessage() {
        var data = 42;
        var response = ApiResponse.ok(data, "Operation successful");
        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo(42);
        assertThat(response.message()).isEqualTo("Operation successful");
        assertThat(response.meta()).isNull();
    }

    @Test
    @DisplayName("ok(data, meta) creates success response with data and meta, null message")
    void okWithDataAndMeta() {
        var data = "result";
        var meta = new ApiResponse.Meta(100L, 0, 20, 5);
        var response = ApiResponse.ok(data, meta);
        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo("result");
        assertThat(response.message()).isNull();
        assertThat(response.meta()).isEqualTo(meta);
    }

    @Test
    @DisplayName("error(code, message) creates error response with formatted message")
    void errorCreatesErrorResponse() {
        var response = ApiResponse.error("VALIDATION_ERROR", "Invalid input");
        assertThat(response.success()).isFalse();
        assertThat(response.message()).isEqualTo("[VALIDATION_ERROR] Invalid input");
        assertThat(response.data()).isNull();
        assertThat(response.meta()).isNull();
    }

    @Test
    @DisplayName("timestamp is always set to a non-null Instant")
    void timestampIsAlwaysSet() {
        var before = Instant.now().minusSeconds(1);
        var ok = ApiResponse.ok();
        var error = ApiResponse.error("ERR", "msg");
        var after = Instant.now().plusSeconds(1);
        assertThat(ok.timestamp()).isBetween(before, after);
        assertThat(error.timestamp()).isBetween(before, after);
    }

    @Test
    @DisplayName("Meta.of(Page) extracts pagination metadata from Spring Data Page")
    void metaOfPage() {
        var page = new Page<String>() {
            @Override
            public int getTotalPages() {
                return 3;
            }

            @Override
            public long getTotalElements() {
                return 50L;
            }

            @Override
            public int getNumber() {
                return 1;
            }

            @Override
            public int getSize() {
                return 20;
            }

            @Override
            public int getNumberOfElements() {
                return 20;
            }

            @Override
            public List<String> getContent() {
                return List.of();
            }

            @Override
            public boolean hasContent() {
                return false;
            }

            @Override
            public Sort getSort() {
                return Sort.unsorted();
            }

            @Override
            public boolean isFirst() {
                return false;
            }

            @Override
            public boolean isLast() {
                return false;
            }

            @Override
            public boolean hasNext() {
                return false;
            }

            @Override
            public boolean hasPrevious() {
                return true;
            }

            @Override
            public Pageable nextPageable() {
                return Pageable.unpaged();
            }

            @Override
            public Pageable previousPageable() {
                return Pageable.unpaged();
            }

            @Override
            public <U> Page<U> map(java.util.function.Function<? super String, ? extends U> converter) {
                return null;
            }

            @Override
            public java.util.Iterator<String> iterator() {
                return getContent().iterator();
            }
        };

        var meta = ApiResponse.Meta.of(page);
        assertThat(meta.total()).isEqualTo(50L);
        assertThat(meta.page()).isEqualTo(1);
        assertThat(meta.size()).isEqualTo(20);
        assertThat(meta.totalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("Meta record stores pagination values correctly")
    void metaRecordValues() {
        var meta = new ApiResponse.Meta(200L, 0, 50, 4);
        assertThat(meta.total()).isEqualTo(200L);
        assertThat(meta.page()).isZero();
        assertThat(meta.size()).isEqualTo(50);
        assertThat(meta.totalPages()).isEqualTo(4);
    }
}
