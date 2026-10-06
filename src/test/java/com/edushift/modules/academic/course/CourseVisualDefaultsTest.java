package com.edushift.modules.academic.course;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CourseVisualDefaults")
class CourseVisualDefaultsTest {

	@Test
	@DisplayName("MAT → target / blue")
	void mat() {
		var v = CourseVisualDefaults.fromCode("MAT");
		assertThat(v.icon()).isEqualTo("target");
		assertThat(v.color()).isEqualTo("#2563EB");
	}

	@Test
	@DisplayName("unknown code → default book-open")
	void unknown() {
		var v = CourseVisualDefaults.fromCode("XYZ");
		assertThat(v.icon()).isEqualTo(CourseVisualDefaults.DEFAULT_ICON);
		assertThat(v.color()).isEqualTo(CourseVisualDefaults.DEFAULT_COLOR);
	}

	@Test
	@DisplayName("null/blank → default")
	void blank() {
		assertThat(CourseVisualDefaults.fromCode(null).icon())
				.isEqualTo(CourseVisualDefaults.DEFAULT_ICON);
		assertThat(CourseVisualDefaults.fromCode("  ").icon())
				.isEqualTo(CourseVisualDefaults.DEFAULT_ICON);
	}

	@Test
	@DisplayName("EF → dumbbell / pink, and it does not paint EDF")
	void ef() {
		var v = CourseVisualDefaults.fromCode("EF");
		assertThat(v.icon()).isEqualTo("dumbbell");
		assertThat(v.color()).isEqualTo("#DB2777");
		assertThat(CourseVisualDefaults.fromCode("EDF").color())
				.isEqualTo(CourseVisualDefaults.DEFAULT_COLOR);
	}

	@Test
	@DisplayName("COMP is celeste and is not treated as Comunicación")
	void compIsNotComunicacion() {
		assertThat(CourseVisualDefaults.fromCode("COMP").color()).isEqualTo("#22D3EE");
		assertThat(CourseVisualDefaults.fromCode("COMU").color()).isEqualTo("#DC2626");
		assertThat(CourseVisualDefaults.fromCode("PSI").color()).isEqualTo("#84CC16");
		assertThat(CourseVisualDefaults.fromCode("PS").color()).isEqualTo("#F97316");
	}
}
