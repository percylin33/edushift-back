package com.edushift.modules.materials.entity;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class MaterialEntityTest {
    @Test void setters() { var m = new Material(); m.setTitle("T"); m.setDescription("D"); m.setKind(MaterialKind.FILE); m.setFilePublicUuid(UUID.randomUUID()); assertThat(m.getTitle()).isEqualTo("T"); assertThat(m.getKind()).isEqualTo(MaterialKind.FILE); }
}
