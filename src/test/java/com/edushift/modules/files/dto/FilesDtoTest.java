package com.edushift.modules.files.dto;
import static org.assertj.core.api.Assertions.assertThat;
import com.edushift.modules.files.entity.FileObject;
import com.edushift.modules.files.storage.StorageProvider;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class FilesDtoTest {
    @Test void fileObjectResponse() { var r = new FileObjectResponse(UUID.randomUUID(), "a.txt", "text/plain", 100L, "hash", "/dl", Instant.now()); assertThat(r.originalName()).isEqualTo("a.txt"); }
    @Test void uploadRequest() { var r = new UploadRequest("materials", "a.txt", "text/plain", 100L); assertThat(r.module()).isEqualTo("materials"); }
    @Test void uploadRequestResponse() { var r = new UploadRequestResponse("FIREBASE", UUID.randomUUID(), "https://u.com", Instant.now(), Map.of("Content-Type", "text/plain")); assertThat(r.provider()).isEqualTo("FIREBASE"); }
    @Test void uploadConfirmation() { var r = new UploadConfirmation(100L, "0".repeat(64)); assertThat(r.sizeBytes()).isEqualTo(100L); }
}
