package com.edushift.modules.materials.dto;
import static org.assertj.core.api.Assertions.assertThat;
import com.edushift.modules.materials.entity.MaterialKind;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class MaterialDtoTest {
    @Test void createUploadRequest() { var r = new CreateUploadMaterialRequest("T", "D"); assertThat(r.title()).isEqualTo("T"); }
    @Test void createLinkRequest() { var r = new CreateLinkMaterialRequest("T", "D", MaterialKind.VIDEO_LINK, "https://youtube.com/v"); assertThat(r.externalUrl()).contains("youtube"); }
    @Test void response() { var r = new MaterialResponse(UUID.randomUUID(), UUID.randomUUID(), "T", "D", MaterialKind.FILE, null, null, UUID.randomUUID(), Instant.now(), null); assertThat(r.title()).isEqualTo("T"); }
    @Test void summary() { var r = new MaterialSummary(UUID.randomUUID(), "T", MaterialKind.VIDEO_LINK, 100L, UUID.randomUUID(), Instant.now()); assertThat(r.title()).isEqualTo("T"); }
    @Test void updateRequest() { var r = new UpdateMaterialRequest("T", "D", null, null); assertThat(r.title()).isEqualTo("T"); }
}
