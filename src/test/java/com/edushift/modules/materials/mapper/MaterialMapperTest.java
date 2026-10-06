package com.edushift.modules.materials.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.files.service.FileObjectService;
import com.edushift.modules.materials.entity.Material;
import com.edushift.modules.materials.entity.MaterialKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MaterialMapperTest {

	@Mock
	private FileObjectService fileObjectService;

	private MaterialMapper mapper;

	@BeforeEach
	void setUp() {
		mapper = new MaterialMapper(fileObjectService);
	}

	@Test
	void toResponse() {
		var m = new Material();
		m.setTitle("T");
		m.setDescription("D");
		m.setKind(MaterialKind.FILE);
		assertThat(mapper.toResponse(m, null)).isNotNull();
		assertThat(mapper.toResponse(m, null).title()).isEqualTo("T");
	}

	@Test
	void toSummary() {
		var m = new Material();
		m.setTitle("S");
		m.setKind(MaterialKind.VIDEO_LINK);
		assertThat(mapper.toSummary(m).title()).isEqualTo("S");
	}

	@Test
	void toSummaryList() {
		assertThat(mapper.toSummaryList(java.util.List.of())).isEmpty();
	}
}
