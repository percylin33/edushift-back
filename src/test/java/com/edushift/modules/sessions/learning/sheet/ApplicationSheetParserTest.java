package com.edushift.modules.sessions.learning.sheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edushift.modules.ai.exception.AiParseException;
import com.edushift.modules.sessions.learning.entity.ApplicationSheetKind;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ApplicationSheetParserTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void acceptsASingleGradeLearningSheet() {
		var parsed = ApplicationSheetParser.parse(
				objectMapper, validLearning(), ApplicationSheetKind.LEARNING, "3.er Grado");
		assertThat(parsed.title()).contains("vacaciones");
		assertThat(parsed.content().getPurpose().getCriterion()).contains("nombres");
		assertThat(parsed.content().getActivities()).hasSize(1);
	}

	@Test
	void rejectsMissingCriterion() {
		String json = validLearning().replace("Identificó nombres y lugares.", "");
		assertThatThrownBy(() -> ApplicationSheetParser.parse(
				objectMapper, json, ApplicationSheetKind.LEARNING, "3.er Grado"))
				.isInstanceOf(AiParseException.class)
				.hasMessageContaining("criterion");
	}

	@Test
	void rejectsItemWithoutKind() {
		String json = validLearning().replace("\"kind\": \"LINE\"", "\"kind\": \"\"");
		assertThatThrownBy(() -> ApplicationSheetParser.parse(
				objectMapper, json, ApplicationSheetKind.LEARNING, "3.er Grado"))
				.isInstanceOf(AiParseException.class)
				.hasMessageContaining("kind");
	}

	@Test
	void rejectsVariantsOfAnotherGrade() {
		String json = """
				{"title":"Ficha mezclada de dos grados","activities":[{"title":"Actividad","variants":[{"grade":"4.to Grado"}]}]}
				""";
		assertThatThrownBy(() -> ApplicationSheetParser.parse(
				objectMapper, json, ApplicationSheetKind.LEARNING, "3.er Grado"))
				.isInstanceOf(AiParseException.class)
				.hasMessageContaining("single grade");
	}

	private static String validLearning() {
		return """
				{
				  "title": "Contamos cómo nos fue en vacaciones",
				  "warmup": {"prompt": "¿Cómo me siento hoy?", "options": ["Alegre", "Nervioso"]},
				  "purpose": {
				    "purpose": "Compartir una anécdota de vacaciones.",
				    "evidence": "Registro oral.",
				    "criterion": "Identificó nombres y lugares."
				  },
				  "activities": [{
				    "title": "Organizo mis recuerdos",
				    "moment": "Antes de hablar",
				    "instruction": "Completa el esquema.",
				    "items": [{"kind": "LINE", "prompt": "¿A quiénes mencionaré?"}]
				  }],
				  "selfAssessment": {
				    "prompt": "Al presentarme",
				    "columns": ["Lo logré", "Necesito ayuda"],
				    "rows": ["Organicé mi historia"]
				  },
				  "reflection": {
				    "questions": [{"kind": "LINE", "prompt": "¿Qué estrategia me funcionó?"}]
				  },
				  "closingMessage": "Tu historia nos ayuda a conocernos."
				}
				""";
	}
}
