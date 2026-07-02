package com.edushift.modules.materials.exception;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
class MaterialExceptionsTest {
    @Test void all() { assertThat(new MaterialNotFoundException("x")).hasMessageContaining("x"); assertThat(new InconsistentPayloadException("x")).hasMessageContaining("x"); assertThat(new RecordEmptyPatchException()).hasMessageContaining("field"); assertThat(new SectionNotFoundException("x")).hasMessageContaining("x"); }
}
