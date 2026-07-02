package com.edushift.modules.students.service.bulk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BulkImportExceptionTest {

    @Test
    @DisplayName("code + message round-trip")
    void twoArg() {
        var ex = new BulkImportException("BAD_FILE", "xlsx is corrupt");
        assertThat(ex.getCode()).isEqualTo("BAD_FILE");
        assertThat(ex.getMessage()).isEqualTo("xlsx is corrupt");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("three-arg constructor chains the cause")
    void threeArg() {
        var cause = new RuntimeException("io");
        var ex = new BulkImportException("IO_ERROR", "could not read file", cause);
        assertThat(ex.getCode()).isEqualTo("IO_ERROR");
        assertThat(ex.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("is a RuntimeException (so it propagates unchecked)")
    void type() {
        assertThat(new BulkImportException("X", "y")).isInstanceOf(RuntimeException.class);
    }
}