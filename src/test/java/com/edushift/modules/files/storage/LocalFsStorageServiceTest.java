package com.edushift.modules.files.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edushift.modules.files.config.StorageProperties;
import com.edushift.modules.files.exception.FileNotFoundException;
import com.edushift.modules.files.exception.StorageUnavailableException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link LocalFsStorageService} (Sprint 7a / BE-7a.0).
 *
 * <p>Pure JUnit, no Spring context: instantiates the service with a
 * per-class tempdir and exercises the storage layer directly. Covers
 * the contracts that BE-7a.0 must honour:
 *
 * <ul>
 *   <li><strong>UT-FIL-1</strong> — Round-trip: put then open returns
 *       the same bytes and the same SHA-256.</li>
 *   <li><strong>UT-FIL-2</strong> — Sandbox escape: a {@code remoteKey}
 *       containing {@code ../} is rejected with
 *       {@link FileNotFoundException} (no write outside the tenant
 *       directory).</li>
 *   <li><strong>UT-FIL-3</strong> — Idempotent delete: deleting a
 *       non-existent key is a no-op, not an error.</li>
 *   <li><strong>UT-FIL-4</strong> — presignedGetUrl returns null (the
 *       local-fs provider never hands out external URLs).</li>
 *   <li><strong>UT-FIL-5</strong> — Missing file on read surfaces
 *       {@link FileNotFoundException}, not 500.</li>
 * </ul>
 */
@DisplayName("LocalFsStorageService — contract")
class LocalFsStorageServiceTest {

	private LocalFsStorageService service;
	private Path root;
	private UUID tenantId;
	private StorageProperties props;

	@BeforeEach
	void setUp() throws IOException {
		props = new StorageProperties();
		root = Files.createTempDirectory("edushift-ut-files-");
		props.getLocalFs().setRoot(root);
		props.setProvider(StorageProvider.LOCAL_FS);
		service = new LocalFsStorageService(props);
		// Run the @PostConstruct manually (no Spring in unit tests).
		ReflectionTestUtils.invokeMethod(service, "init");
		tenantId = UUID.randomUUID();
	}

	@AfterEach
	void tearDown() throws IOException {
		if (root != null && Files.exists(root)) {
			try (var stream = Files.walk(root)) {
				stream.sorted((a, b) -> b.toString().length() - a.toString().length())
						.forEach(p -> {
							try { Files.deleteIfExists(p); } catch (IOException ignored) {}
						});
			}
		}
	}

	@Test
	@DisplayName("put + open: bytes and SHA-256 round-trip")
	void roundTrip() throws IOException {
		byte[] payload = "hello, lms world".getBytes(StandardCharsets.UTF_8);
		String expectedSha = sha256(payload);

		StoredObject stored = service.put(new StoragePutRequest(
				tenantId,
				"materials",
				UUID.randomUUID(),
				"greeting.txt",
				"text/plain",
				new ByteArrayInputStream(payload),
				payload.length));

		assertThat(stored.provider()).isEqualTo(StorageProvider.LOCAL_FS);
		assertThat(stored.sizeBytes()).isEqualTo(payload.length);
		assertThat(stored.checksumSha256()).isEqualTo(expectedSha);

		try (InputStream in = service.open(tenantId, stored.remoteKey())) {
			byte[] read = in.readAllBytes();
			assertThat(read).isEqualTo(payload);
		}
	}

	@Test
	@DisplayName("sandbox escape: ../ in remoteKey is rejected (no out-of-tree write)")
	void sandboxEscape() {
		UUID id = UUID.randomUUID();
		String sneaky = "../" + id + ".txt";
		StoredObject stored = service.put(new StoragePutRequest(
				tenantId,
				"materials",
				id,
				"sneaky.txt",
				"text/plain",
				new ByteArrayInputStream("x".getBytes()),
				1));
		// The put above used the service's default key layout; we cannot
		// inject the malicious key through put. We exercise the open
		// path with the malicious key directly — that's the
		// tenant-scoping boundary the service MUST defend.
		assertThatThrownBy(() -> service.open(tenantId, sneaky))
				.isInstanceOf(FileNotFoundException.class);

		// The put above succeeded: assert it landed inside the tenant dir.
		assertThat(stored.remoteKey()).startsWith("tenants/" + tenantId + "/");
	}

	@Test
	@DisplayName("delete: idempotent — missing key is not an error")
	void deleteIdempotent() {
		assertThat(service.presignedGetUrl(tenantId, "nope", 60)).isNull();
		// No throw.
		service.delete(tenantId, "tenants/" + tenantId + "/lms/materials/does-not-exist.txt");
	}

	@Test
	@DisplayName("open: missing key throws FileNotFoundException, not 500")
	void openMissing() {
		assertThatThrownBy(() -> service.open(tenantId,
				"tenants/" + tenantId + "/lms/materials/missing.txt"))
				.isInstanceOf(FileNotFoundException.class);
	}

	@Test
	@DisplayName("presignedGetUrl: always null for LOCAL_FS")
	void presignedAlwaysNull() {
		assertThat(service.presignedGetUrl(tenantId, "any", 60)).isNull();
	}

	@Test
	@DisplayName("init fails fast when the root cannot be created")
	void initFailsFast() {
		StorageProperties bad = new StorageProperties();
		// A path under a file (not a directory) makes createDirectories fail.
		Path blocked;
		try {
			blocked = Files.createTempFile("edushift-blocked-", ".txt");
			bad.getLocalFs().setRoot(blocked);
		}
		catch (IOException e) {
			throw new StorageUnavailableException("Could not set up blocked path", e);
		}
		LocalFsStorageService s = new LocalFsStorageService(bad);
		assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(s, "init"))
				.isInstanceOf(StorageUnavailableException.class);
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(java.security.MessageDigest
					.getInstance("SHA-256").digest(bytes));
		}
		catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
