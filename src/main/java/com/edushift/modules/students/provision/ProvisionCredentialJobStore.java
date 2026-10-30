package com.edushift.modules.students.provision;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory TTL store for plaintext credentials after CRED-7 provision.
 * Never writes passwords to the database. Entries expire after 20 minutes.
 */
@Component
public class ProvisionCredentialJobStore {

	private static final long TTL_SECONDS = 20 * 60;

	private record Entry(
			Instant expiresAt,
			String sectionName,
			String academicYear,
			String levelName,
			List<ProvisionCredentialRow> rows
	) {}

	private final Map<UUID, Entry> jobs = new ConcurrentHashMap<>();

	public UUID put(
			String sectionName,
			String academicYear,
			String levelName,
			List<ProvisionCredentialRow> rows
	) {
		purgeExpired();
		UUID jobId = UUID.randomUUID();
		jobs.put(jobId, new Entry(
				Instant.now().plusSeconds(TTL_SECONDS),
				sectionName,
				academicYear,
				levelName,
				List.copyOf(rows)));
		return jobId;
	}

	public Optional<EntryView> get(UUID jobPublicUuid) {
		purgeExpired();
		Entry entry = jobs.get(jobPublicUuid);
		if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
			jobs.remove(jobPublicUuid);
			return Optional.empty();
		}
		return Optional.of(new EntryView(
				entry.sectionName(),
				entry.academicYear(),
				entry.levelName(),
				entry.rows()));
	}

	public record EntryView(
			String sectionName,
			String academicYear,
			String levelName,
			List<ProvisionCredentialRow> rows
	) {}

	private void purgeExpired() {
		Instant now = Instant.now();
		jobs.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
	}
}
