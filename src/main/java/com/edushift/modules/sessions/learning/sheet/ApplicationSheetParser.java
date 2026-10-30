package com.edushift.modules.sessions.learning.sheet;

import com.edushift.modules.ai.exception.AiParseException;
import com.edushift.modules.ai.util.LlmJsonSanitizer;
import com.edushift.modules.sessions.learning.entity.ApplicationSheetContent;
import com.edushift.modules.sessions.learning.entity.ApplicationSheetKind;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Iterator;
import java.util.Map;

/**
 * Turns one LLM JSON document into a single-grade sheet.
 * A payload that still splits the page by grade ({@code variants},
 * {@code criteriaByGrade}) is rejected.
 */
public final class ApplicationSheetParser {

	private ApplicationSheetParser() {
	}

	public static ParsedApplicationSheet parse(ObjectMapper objectMapper, String raw,
			ApplicationSheetKind kind, String grade) {
		String json = LlmJsonSanitizer.sanitize(raw);
		if (json == null || json.isBlank()) {
			throw new AiParseException("Application sheet output is empty");
		}
		final JsonNode root;
		try {
			root = objectMapper.readTree(json);
		}
		catch (Exception ex) {
			throw new AiParseException("Application sheet output is not JSON", ex);
		}
		if (root == null || !root.isObject()) {
			throw new AiParseException("Application sheet output must be a JSON object");
		}
		if (containsKey(root, "variants") || containsKey(root, "criteriaByGrade")) {
			throw new AiParseException(
					"Application sheet must be for a single grade. Remove variants and criteriaByGrade.");
		}
		String title = text(root, "title");
		ApplicationSheetContent content;
		try {
			content = objectMapper.treeToValue(root, ApplicationSheetContent.class);
		}
		catch (Exception ex) {
			throw new AiParseException("Application sheet output does not match the schema", ex);
		}
		if (kind == ApplicationSheetKind.DIAGNOSTIC && content.getTeacherComment() == null) {
			content.setTeacherComment(Boolean.TRUE);
		}
		String problem = ApplicationSheetRules.validate(kind, grade, title, content).orElse(null);
		if (problem != null) {
			throw new AiParseException("Application sheet output: " + problem);
		}
		return new ParsedApplicationSheet(title.trim(), content);
	}

	private static boolean containsKey(JsonNode node, String key) {
		if (node == null) {
			return false;
		}
		if (node.isObject()) {
			if (node.has(key)) {
				return true;
			}
			Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
			while (fields.hasNext()) {
				if (containsKey(fields.next().getValue(), key)) {
					return true;
				}
			}
			return false;
		}
		if (node.isArray()) {
			for (JsonNode child : node) {
				if (containsKey(child, key)) {
					return true;
				}
			}
		}
		return false;
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value == null || value.isNull() ? null : value.asText();
	}

	public record ParsedApplicationSheet(String title, ApplicationSheetContent content) {
	}
}
