package com.edushift.modules.sessions.learning.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Free-form pedagogical content of a {@link LearningSession}, persisted
 * as Postgres {@code jsonb} via Hibernate's
 * {@code @JdbcTypeCode(SqlTypes.JSON)} (Sprint 5A / BE-5A.4).
 *
 * <p>This is a mutable POJO (not a {@code record}) on purpose: Jackson
 * has the smoothest path with classic getters/setters and a no-args
 * constructor, and the structure is intentionally flexible so the
 * Sprint 7 LMS Core can add fields without breaking the schema.</p>
 *
 * <h3>Fields</h3>
 * <ul>
 *   <li>{@code objective} - what the kids should walk out knowing
 *       (one sentence).</li>
 *   <li>{@code activities} - ordered list of "what we do" steps
 *       (free text strings; future Sprint 7 will upgrade to rich text).</li>
 *   <li>{@code materials} - bullet list of needed assets / resources.</li>
 *   <li>{@code observations} - post-session notes the teacher leaves
 *       once the session is COMPLETED.</li>
 * </ul>
 *
 * <h3>Forward-compatibility</h3>
 * Unknown JSON properties are ignored on deserialisation
 * ({@link JsonIgnoreProperties}) so older rows don't break when newer
 * fields are introduced.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionContent implements Serializable {

	private static final long serialVersionUID = 1L;

	private String objective;

	private List<String> activities = new ArrayList<>();

	private List<String> materials = new ArrayList<>();

	private String observations;
}
