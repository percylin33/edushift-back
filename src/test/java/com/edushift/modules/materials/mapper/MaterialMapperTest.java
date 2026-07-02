package com.edushift.modules.materials.mapper;
import static org.assertj.core.api.Assertions.assertThat;
import com.edushift.modules.materials.entity.Material;
import com.edushift.modules.materials.entity.MaterialKind;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class MaterialMapperTest {
    private final MaterialMapper mapper = new MaterialMapper();
    @Test void toResponse() { var m = new Material(); m.setTitle("T"); m.setDescription("D"); m.setKind(MaterialKind.FILE); assertThat(mapper.toResponse(m, null)).isNotNull(); assertThat(mapper.toResponse(m, null).title()).isEqualTo("T"); }
    @Test void toSummary() { var m = new Material(); m.setTitle("S"); m.setKind(MaterialKind.VIDEO_LINK); assertThat(mapper.toSummary(m).title()).isEqualTo("S"); }
    @Test void toSummaryList() { assertThat(mapper.toSummaryList(java.util.List.of())).isEmpty(); }
}
