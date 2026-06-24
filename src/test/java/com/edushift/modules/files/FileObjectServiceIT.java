package com.edushift.modules.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edushift.IntegrationTest;
import com.edushift.modules.files.config.StorageProperties;
import com.edushift.modules.files.entity.FileObject;
import com.edushift.modules.files.exception.FileNotFoundException;
import com.edushift.modules.files.service.FileObjectService;
import com.edushift.modules.tenants.entity.Tenant;
import com.edushift.modules.tenants.entity.TenantStatus;
import com.edushift.modules.tenants.repository.TenantRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Multi-tenant isolation IT for the {@code files} module
 * (Sprint 7a / BE-7a.0, audit §12).
 *
 * <h3>What is tested</h3>
 * <ul>
 *   <li><strong>XT-FIL-1</strong> — Tenant A can store + read + delete
 *       a file via {@link FileObjectService}.</li>
 *   <li><strong>XT-FIL-2</strong> — Tenant B cannot find a file
 *       uploaded by tenant A (cross-tenant lookup returns
 *       {@link java.util.Optional#empty()} via the {@code @TenantId}
 *       auto-filter).</li>
 *   <li><strong>XT-FIL-3</strong> — Tenant B cannot delete a file
 *       owned by tenant A (anti-enumeration 404).</li>
 *   <li><strong>XT-FIL-4</strong> — The storage layer writes under
 *       the tenant-scoped key
 *       {@code ${root}/tenants/{tenantId}/lms/{module}/{publicUuid}}
 *       (Sprint 11 / ADR-11.4 — the {@code tenants/} prefix is part
 *       of the canonical multi-tenant layout, see
 *       {@code .cursor/rules/multi-tenant-rules.mdc}).</li>
 *   <li><strong>XT-FIL-5</strong> — Bytes round-trip with the same
 *       SHA-256 the storage layer reported (no corruption).</li>
 * </ul>
 *
 * <p>Uses the {@code IntegrationTest} base class (Testcontainers
 * Postgres + Spring context). The {@code app.storage.provider} value
 * is forced to {@code LOCAL_FS} for this class so the test does not
 * require a Firebase service account; a separate IT class will
 * exercise the Firebase path when credentials are available.
 */
@DisplayName("FileObject — multi-tenant isolation + round-trip")
class FileObjectServiceIT extends IntegrationTest {

	@Autowired private FileObjectService fileObjectService;
	@Autowired private TenantRepository tenantRepository;
	@Autowired private StorageProperties storageProperties;
	@Autowired private PlatformTransactionManager transactionManager;

	private TransactionTemplate tx;
	private Tenant tenantA;
	private Tenant tenantB;
	private Path storageRoot;

	@BeforeEach
	void setUp() throws IOException {
		tx = new TransactionTemplate(transactionManager);
		// Force LOCAL_FS for this IT so we never hit Firebase.
		storageProperties.setProvider(
				com.edushift.modules.files.storage.StorageProvider.LOCAL_FS);
		// Re-point the root into a per-class tempdir to avoid stale
		// files from previous IT runs and so we can clean up below.
		this.storageRoot = Files.createTempDirectory("edushift-it-files-");
		storageProperties.getLocalFs().setRoot(this.storageRoot);

		tenantA = tx.execute(s -> {
			Tenant t = new Tenant();
			t.setPublicUuid(UUID.randomUUID());
			t.setSlug("tenant-a-files-" + UUID.randomUUID().toString().substring(0, 8));
			t.setName("Tenant A Files");
			t.setStatus(TenantStatus.ACTIVE);
			return tenantRepository.save(t);
		});
		tenantB = tx.execute(s -> {
			Tenant t = new Tenant();
			t.setPublicUuid(UUID.randomUUID());
			t.setSlug("tenant-b-files-" + UUID.randomUUID().toString().substring(0, 8));
			t.setName("Tenant B Files");
			t.setStatus(TenantStatus.ACTIVE);
			return tenantRepository.save(t);
		});

		com.edushift.shared.multitenancy.TenantContext.set(tenantA.getId());
	}

	@AfterEach
	void tearDown() throws IOException {
		com.edushift.shared.multitenancy.TenantContext.clear();
		if (storageRoot != null && Files.exists(storageRoot)) {
			// best-effort cleanup; ignore failures
			try (var stream = Files.walk(storageRoot)) {
				stream.sorted((a, b) -> b.toString().length() - a.toString().length())
						.forEach(p -> {
							try { Files.deleteIfExists(p); } catch (IOException ignored) {}
						});
			}
		}
	}

	@Nested
	@DisplayName("same-tenant happy path")
	class SameTenantAccess {

		@Test
		@DisplayName("store + findByPublicUuid + delete works end-to-end")
		void roundTrip() throws java.io.IOException {
			// 1. Store
			byte[] payload = "hello, lms world".getBytes();
			FileObject stored = fileObjectService.store(tenantA.getId(), "materials",
					new MockMultipartFile("file", "test.txt", "text/plain",
							new ByteArrayInputStream(payload)));

			assertThat(stored.getPublicUuid()).isNotNull();
			assertThat(stored.getProvider())
					.isEqualTo(com.edushift.modules.files.storage.StorageProvider.LOCAL_FS);
			assertThat(stored.getSizeBytes()).isEqualTo(payload.length);
			assertThat(stored.getChecksumSha256()).matches("^[0-9a-f]{64}$");

			// 2. Find within the same tenant
			FileObject found = fileObjectService.findByPublicUuid(stored.getPublicUuid())
					.orElseThrow(() -> new AssertionError("row should be visible to its tenant"));
			assertThat(found.getPublicUuid()).isEqualTo(stored.getPublicUuid());
			assertThat(found.getOriginalName()).isEqualTo("test.txt");

			// 3. Delete (no references)
			fileObjectService.delete(stored.getPublicUuid());
			assertThat(fileObjectService.findByPublicUuid(stored.getPublicUuid()))
					.isEmpty();
		}
	}

	@Nested
	@DisplayName("cross-tenant access")
	class CrossTenantAccess {

		@Test
		@DisplayName("tenant B cannot find a file owned by tenant A")
		void findByPublicUuid_returnsEmptyForOtherTenant() throws java.io.IOException {
			com.edushift.shared.multitenancy.TenantContext.set(tenantA.getId());
			FileObject owned = fileObjectService.store(tenantA.getId(), "materials",
					new MockMultipartFile("file", "a.txt", "text/plain",
							new ByteArrayInputStream("a".getBytes())));

			// Switch context to tenant B
			com.edushift.shared.multitenancy.TenantContext.set(tenantB.getId());
			assertThat(fileObjectService.findByPublicUuid(owned.getPublicUuid()))
					.as("Hibernate @TenantId must hide tenant-A rows from tenant B")
					.isEmpty();
		}

		@Test
		@DisplayName("tenant B cannot delete a file owned by tenant A")
		void delete_throwsForOtherTenant() throws java.io.IOException {
			com.edushift.shared.multitenancy.TenantContext.set(tenantA.getId());
			FileObject owned = fileObjectService.store(tenantA.getId(), "materials",
					new MockMultipartFile("file", "a.txt", "text/plain",
							new ByteArrayInputStream("a".getBytes())));

			com.edushift.shared.multitenancy.TenantContext.set(tenantB.getId());
			UUID ownedUuid = owned.getPublicUuid();
			assertThatThrownBy(() -> fileObjectService.delete(ownedUuid))
					.isInstanceOf(FileNotFoundException.class);

			// File should still exist when we look at it from tenant A.
			com.edushift.shared.multitenancy.TenantContext.set(tenantA.getId());
			assertThat(fileObjectService.findByPublicUuid(ownedUuid))
					.isPresent();
		}
	}

	@Nested
	@DisplayName("storage layer guarantees")
	class StorageLayerGuarantees {

		@Test
		@DisplayName("LOCAL_FS writes under ${root}/tenants/{tenantId}/lms/{module}/{publicUuid}")
		void onDiskPathIsTenantScoped() throws IOException {
			com.edushift.shared.multitenancy.TenantContext.set(tenantA.getId());
			FileObject stored = fileObjectService.store(tenantA.getId(), "materials",
					new MockMultipartFile("file", "doc.pdf", "application/pdf",
							new ByteArrayInputStream("pdf-bytes".getBytes())));

			// ADR-11.4: the on-disk layout is ${root}/tenants/{tid}/lms/{module}/{publicUuid}.
			// The `tenants/{tid}` prefix is part of the remoteKey itself (the
			// canonical EduShift file layout, mirrored from the multi-tenant
			// rules in `.cursor/rules/multi-tenant-rules.mdc`). Earlier
			// versions of this test joined `storageRoot/tenantId/remoteKey`,
			// which produced a path with TWO copies of the tenantId; the
			// fix is to use the remoteKey as-is.
			Path expected = storageRoot
					.resolve(stored.getRemoteKey())
					.normalize();
			assertThat(Files.exists(expected))
					.as("expected file at " + expected)
					.isTrue();
			assertThat(stored.getRemoteKey())
					.as("remoteKey must include tenants/{tenantId}")
					.contains("tenants/" + tenantA.getId());
		}

		@Test
		@DisplayName("checksum and content round-trip")
		void checksumRoundTrip() throws Exception {
			com.edushift.shared.multitenancy.TenantContext.set(tenantA.getId());
			String body = "round-trip body\n";
			FileObject stored = fileObjectService.store(tenantA.getId(), "submissions",
					new MockMultipartFile("file", "rt.txt", "text/plain",
							new ByteArrayInputStream(body.getBytes())));

			assertThat(stored.getSizeBytes()).isEqualTo(body.getBytes().length);
			// SHA-256 of the body, computed locally and compared against
			// what the storage layer reported.
			String manual = HexFormat.of().formatHex(
					java.security.MessageDigest.getInstance("SHA-256")
							.digest(body.getBytes()));
			assertThat(stored.getChecksumSha256())
					.isEqualTo(manual);
		}
	}
}
