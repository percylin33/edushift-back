package com.edushift.modules.evaluations.gradebook;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CnebLiteralScaleTest {

	@Test
	@DisplayName("A, A, B, AD average to A")
	void blockAverage() {
		BigDecimal sum = CnebLiteralScale.points("A")
				.add(CnebLiteralScale.points("A"))
				.add(CnebLiteralScale.points("B"))
				.add(CnebLiteralScale.points("AD"));
		assertThat(CnebLiteralScale.toLiteral(sum.divide(new BigDecimal("4"))))
				.isEqualTo("A");
	}

	@Test
	@DisplayName("weighted AD/A/B at 34/33/33 returns A")
	void weightedCategories() {
		BigDecimal total = new BigDecimal("4").multiply(new BigDecimal("34"))
				.add(new BigDecimal("3").multiply(new BigDecimal("33")))
				.add(new BigDecimal("2").multiply(new BigDecimal("33")));
		assertThat(CnebLiteralScale.toLiteral(total.divide(new BigDecimal("100"))))
				.isEqualTo("A");
	}

	@Test
	@DisplayName("cuts sit on the half point")
	void cuts() {
		assertThat(CnebLiteralScale.toLiteral(new BigDecimal("3.50"))).isEqualTo("AD");
		assertThat(CnebLiteralScale.toLiteral(new BigDecimal("3.49"))).isEqualTo("A");
		assertThat(CnebLiteralScale.toLiteral(new BigDecimal("2.50"))).isEqualTo("A");
		assertThat(CnebLiteralScale.toLiteral(new BigDecimal("1.50"))).isEqualTo("B");
		assertThat(CnebLiteralScale.toLiteral(new BigDecimal("1.49"))).isEqualTo("C");
	}
}
