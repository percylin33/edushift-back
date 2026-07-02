package com.edushift.modules.files.error;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
class FileErrorCodesTest {
    @Test void constantsExist() { assertThat(FilesErrorCodes.FILE_NOT_FOUND).isEqualTo("FILE_NOT_FOUND"); assertThat(FilesErrorCodes.FILE_TOO_LARGE).isEqualTo("FILE_TOO_LARGE"); assertThat(FilesErrorCodes.FILE_TYPE_NOT_ALLOWED).isEqualTo("FILE_TYPE_NOT_ALLOWED"); assertThat(FilesErrorCodes.STORAGE_UNAVAILABLE).isEqualTo("STORAGE_UNAVAILABLE"); assertThat(FilesErrorCodes.FILE_CORRUPTED).isEqualTo("FILE_CORRUPTED"); }
}
