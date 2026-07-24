package com.edushift.modules.ai.rag;

import com.edushift.modules.academic.competency.entity.Capacity;
import com.edushift.modules.academic.competency.entity.Competency;
import com.edushift.modules.academic.competency.repository.CapacityRepository;
import com.edushift.modules.academic.competency.repository.CompetencyRepository;
import com.edushift.modules.academic.course.entity.Course;
import com.edushift.modules.academic.course.repository.CourseRepository;
import com.edushift.shared.multitenancy.TenantContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant-scoped RAG (Retrieval-Augmented Generation) context for the AI
 * generators (Sprint cierre-A / B11).
 *
 * <p><b>Why BM25 in-memory</b> — the closure plan defers pgvector to a
 * later sprint; until then we keep things simple with a deterministic
 * BM25 scoring over the course's competencies and capacities. The
 * corpus per query is tiny (≤ a few dozen documents) so an in-memory
 * implementation is acceptable for MVP latency.</p>
 *
 * <p><b>Multi-tenant</b> — every lookup goes through the tenant-scoped
 * repositories; cross-tenant corpora never mix.</p>
 *
 * <p><b>Source documents</b> (per query):
 * <ol>
 *   <li>{@link Competency} rows for the course (name + description).</li>
 *   <li>{@link Capacity} rows for those competencies (name +
 *       description).</li>
 * </ol>
 * The top-K documents (default 8) are returned as
 * {@link RagSnippet} records; the caller prepends them to the LLM
 * prompt as a {@code [CONTEXT]} block.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagContextService {

	private static final int DEFAULT_TOP_K = 8;
	private static final double BM25_K1 = 1.2;
	private static final double BM25_B = 0.75;

	private final CourseRepository courseRepository;
	private final CompetencyRepository competencyRepository;
	private final CapacityRepository capacityRepository;

	@Transactional(readOnly = true)
	public List<RagSnippet> retrieveForCourse(UUID courseId, String query, int topK) {
		UUID tenantId = TenantContext.currentRequired();
		int limit = Math.max(1, Math.min(topK, 32));

		Course course = courseRepository.findByPublicUuid(courseId)
				.orElseThrow(() -> new com.edushift.shared.exception.ResourceNotFoundException(
						"Course", courseId.toString()));

		List<Doc> corpus = new ArrayList<>();
		List<Competency> competencies = competencyRepository.findAllByCourseOrderByDisplayOrderAsc(course);
		corpus.addAll(competencyDocs(competencies));
		corpus.addAll(capacityDocs(capacityRepository.findAllByCompetencyIn(competencies)));

		if (corpus.isEmpty()) {
			log.debug("[rag] empty corpus for tenant={} course={}", tenantId, courseId);
			return List.of();
		}
		List<String> queryTerms = tokenize(query == null ? "" : query);
		if (queryTerms.isEmpty()) {
			return corpus.stream()
					.limit(limit)
					.map(d -> new RagSnippet(d.id, d.kind, d.title, d.text, 0.0))
					.toList();
		}
		List<RagSnippet> ranked = bm25Rank(corpus, queryTerms, limit);
		log.debug("[rag] tenant={} course={} queryTerms={} corpus={} topK={}",
				tenantId, courseId, queryTerms.size(), corpus.size(), ranked.size());
		return ranked;
	}

	@Transactional(readOnly = true)
	public List<RagSnippet> retrieveForCourse(UUID courseId, String query) {
		return retrieveForCourse(courseId, query, DEFAULT_TOP_K);
	}

	// -----------------------------------------------------------------
	// Corpus loaders
	// -----------------------------------------------------------------

	private static List<Doc> competencyDocs(List<Competency> all) {
		List<Doc> out = new ArrayList<>(all.size());
		for (Competency c : all) {
			String text = joinNonBlank(c.getName(), c.getDescription());
			if (text.isBlank()) continue;
			out.add(new Doc(
					"competency:" + c.getPublicUuid(),
					"COMPETENCY",
					c.getCode() + " — " + c.getName(),
					text));
		}
		return out;
	}

	private static List<Doc> capacityDocs(List<Capacity> all) {
		List<Doc> out = new ArrayList<>(all.size());
		for (Capacity c : all) {
			String text = joinNonBlank(c.getName(), c.getDescription());
			if (text.isBlank()) continue;
			out.add(new Doc(
					"capacity:" + c.getPublicUuid(),
					"CAPACITY",
					c.getCode() + " — " + c.getName(),
					text));
		}
		return out;
	}

	// -----------------------------------------------------------------
	// BM25 (deterministic, in-memory, English + Spanish friendly)
	// -----------------------------------------------------------------

	private List<RagSnippet> bm25Rank(List<Doc> corpus, List<String> queryTerms, int topK) {
		Map<String, Integer> df = new HashMap<>();
		List<List<String>> tokenisedCorpus = new ArrayList<>(corpus.size());
		for (Doc d : corpus) {
			List<String> toks = tokenize(d.text + " " + d.title);
			tokenisedCorpus.add(toks);
			for (String t : toks) {
				df.merge(t, 1, Integer::sum);
			}
		}
		int N = corpus.size();
		double avgDl = tokenisedCorpus.stream().mapToInt(List::size).average().orElse(1.0);

		List<Scored> scored = new ArrayList<>(corpus.size());
		for (int i = 0; i < corpus.size(); i++) {
			Doc d = corpus.get(i);
			List<String> toks = tokenisedCorpus.get(i);
			int dl = toks.size();
			Map<String, Integer> tf = new HashMap<>();
			for (String t : toks) tf.merge(t, 1, Integer::sum);

			double score = 0.0;
			for (String qt : queryTerms) {
				int f = tf.getOrDefault(qt, 0);
				if (f == 0) continue;
				int docFreq = df.getOrDefault(qt, 1);
				double idf = Math.log(1 + (N - docFreq + 0.5) / (docFreq + 0.5));
				double norm = f * (BM25_K1 + 1)
						/ (f + BM25_K1 * (1 - BM25_B + BM25_B * dl / Math.max(1.0, avgDl)));
				score += idf * norm;
			}
			if (score > 0) {
				scored.add(new Scored(d, score));
			}
		}
		scored.sort(Comparator.comparingDouble((Scored s) -> s.score).reversed());
		List<RagSnippet> out = new ArrayList<>(Math.min(topK, scored.size()));
		for (int i = 0; i < Math.min(topK, scored.size()); i++) {
			Scored s = scored.get(i);
			out.add(new RagSnippet(s.doc.id, s.doc.kind, s.doc.title, truncate(s.doc.text, 400), s.score));
		}
		return out;
	}

	// -----------------------------------------------------------------
	// Token / text helpers
	// -----------------------------------------------------------------

	static List<String> tokenize(String text) {
		if (text == null || text.isBlank()) return List.of();
		String[] parts = text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+");
		List<String> out = new ArrayList<>(parts.length);
		for (String p : parts) {
			if (p.length() >= 2) out.add(p);
		}
		return out;
	}

	private static String joinNonBlank(String... parts) {
		StringBuilder sb = new StringBuilder();
		for (String p : parts) {
			if (p == null || p.isBlank()) continue;
			if (sb.length() > 0) sb.append(' ');
			sb.append(p.trim());
		}
		return sb.toString();
	}

	private static String truncate(String s, int max) {
		if (s == null) return "";
		return s.length() <= max ? s : s.substring(0, max) + "...";
	}

	// -----------------------------------------------------------------
	// DTOs
	// -----------------------------------------------------------------

	/** A retrieved chunk fed to the LLM prompt. */
	public record RagSnippet(
			String id,
			String kind,
			String title,
			String text,
			double score
	) {}

	private record Doc(String id, String kind, String title, String text) {}
	private record Scored(Doc doc, double score) {}
}