package com.edushift.modules.files.entity;
import static org.assertj.core.api.Assertions.assertThat;
import com.edushift.modules.files.storage.StorageProvider;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class FilesEntityTest {
    @Test void setters() { var f = new FileObject(); f.setPublicUuid(UUID.randomUUID()); f.setProvider(StorageProvider.LOCAL_FS); f.setRemoteKey("k"); f.setOriginalName("a.txt"); f.setContentType("text/plain"); f.setSizeBytes(100L); f.setChecksumSha256("0".repeat(64)); f.setStatus(FileUploadStatus.READY); assertThat(f.getOriginalName()).isEqualTo("a.txt"); assertThat(f.getProvider()).isEqualTo(StorageProvider.LOCAL_FS); }
}
