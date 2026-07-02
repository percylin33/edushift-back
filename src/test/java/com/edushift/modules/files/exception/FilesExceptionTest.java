package com.edushift.modules.files.exception;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
class FilesExceptionTest {
    @Test void all() { assertThat(new FileNotFoundException("x")).hasMessageContaining("x"); assertThat(new FileTooLargeException(100L, 50L)).hasMessageContaining("100"); assertThat(new FileTypeNotAllowedException("x")).hasMessageContaining("x"); assertThat(new StorageUnavailableException("x")).hasMessageContaining("x"); }
}
