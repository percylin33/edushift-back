package com.edushift.modules.materials.error;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
class MaterialErrorCodesTest {
    @Test void constantsExist() { assertThat(MaterialsErrorCodes.MATERIAL_NOT_FOUND).isEqualTo("MATERIAL_NOT_FOUND"); assertThat(MaterialsErrorCodes.SECTION_NOT_FOUND).isEqualTo("SECTION_NOT_FOUND"); assertThat(MaterialsErrorCodes.INCONSISTENT_PAYLOAD).isEqualTo("INCONSISTENT_PAYLOAD"); assertThat(MaterialsErrorCodes.RECORD_EMPTY_PATCH).isEqualTo("RECORD_EMPTY_PATCH"); }
}
