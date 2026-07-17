package com.edushift.modules.help.service;

import com.edushift.modules.help.config.ManualIndexProperties;
import com.edushift.modules.help.dto.ManualChapter;
import com.edushift.modules.help.dto.ManualIndexEntry;
import com.edushift.modules.help.dto.ManualManifest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Loads and caches the manual index manifest at application startup and
 * resolves chapter content on demand.
 *
 * <h3>Resolution order</h3>
 * <ol>
 *   <li>Filesystem path {@code docs.manuals.manifest-path} (default
 *       {@code docs/manuales/manifest.json} relative to the working dir).
 *       Convenient during local dev because the BE working dir is the
 *       repo root and the manifest lives next to the manuals.</li>
 *   <li>Classpath resource {@code docs.manuals.classpath-resource} (default
 *       {@code /help/manifest.json}). Used in packaged deployments where
 *       the manifest is copied into {@code src/main/resources/help/}.</li>
 * </ol>
 *
 * <h3>Chapter resolution</h3>
 * Chapter files live next to {@code manifest.json} under
 * {@code docs/manuales/<role>/<file>}. The {@code file} argument is
 * matched against an allow-list (README.md + 01..03-*.md) to prevent
 * path-traversal abuse. If a file is missing the service returns an empty
 * {@link Optional}.
 *
 * <h3>Failure modes</h3>
 * <ul>
 *   <li>No manifest found at either location → service logs WARN and caches
 *       an empty list. The endpoint returns 200 with an empty {@code data}.</li>
 *   <li>Manifest exists but is malformed → service logs ERROR and caches
 *       an empty list (the {@code @PostConstruct} swallows the exception).</li>
 *   <li>Chapter file missing → returns {@link Optional#empty()} so the
 *       controller can return 404.</li>
 * </ul>
 */
@Service
public class ManualIndexService {

    private static final Logger log = LoggerFactory.getLogger(ManualIndexService.class);

    /** Allowed chapter filenames (kebab-case, per docs/manuales convention). */
    private static final Set<String> ALLOWED_FILES = Set.of(
            "README.md",
            "01-onboarding-y-acceso.md",
            "02-flujos-esenciales.md",
            "03-autoevaluacion.md"
    );

    /** Map of canonical role key → directory name. The role key is what the
     *  controller receives; the directory name matches the manifest URL. */
    private static final Map<String, String> ROLE_DIRS = Map.of(
            "SUPER_ADMIN", "super-admin",
            "TENANT_ADMIN", "tenant-admin",
            "TEACHER", "docente",
            "STUDENT", "estudiante",
            "PARENT", "padre",
            "STAFF", "personal"
    );

    private final ManualIndexProperties properties;
    private final ObjectMapper objectMapper;

    private volatile Path manualsRoot;
    private volatile List<ManualIndexEntry> cached = Collections.emptyList();

    public ManualIndexService(ManualIndexProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    }

    @PostConstruct
    void loadManifest() {
        String fsPath = properties.getManifestPath();
        if (fsPath != null && !fsPath.isBlank()) {
            Path resolved = resolveFilesystem(fsPath);
            if (resolved != null && Files.exists(resolved)) {
                try (InputStream in = Files.newInputStream(resolved)) {
                    ManualManifest manifest = objectMapper.readValue(in, ManualManifest.class);
                    cached = manifest.manuals() != null ? manifest.manuals() : Collections.emptyList();
                    manualsRoot = resolved.getParent();
                    log.info("Loaded manual index from filesystem path '{}' ({} entries)",
                            resolved.toAbsolutePath(), cached.size());
                    return;
                } catch (IOException ex) {
                    log.error("Failed to read manual index from filesystem path '{}'", resolved, ex);
                    cached = Collections.emptyList();
                    return;
                }
            }
            log.debug("Manifest not found at filesystem path '{}' (resolved='{}'). Trying fallbacks.",
                    fsPath, resolved);
        }

        String cp = properties.getClasspathResource();
        if (cp != null && !cp.isBlank()) {
            String resource = cp.startsWith("/") ? cp.substring(1) : cp;
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
                if (in != null) {
                    ManualManifest manifest = objectMapper.readValue(in, ManualManifest.class);
                    cached = manifest.manuals() != null ? manifest.manuals() : Collections.emptyList();
                    manualsRoot = null;
                    log.info("Loaded manual index from classpath '{}' ({} entries)", resource, cached.size());
                    return;
                }
            } catch (IOException ex) {
                log.error("Failed to read manual index from classpath '{}'", resource, ex);
                cached = Collections.emptyList();
                return;
            }
        }

        log.warn("Manual index manifest not found (filesystem='{}', classpath='{}'). Endpoint will return empty data.",
                fsPath, cp);
        cached = Collections.emptyList();
    }

    public List<ManualIndexEntry> getIndex() {
        return cached;
    }

    /**
     * Resolves a chapter file inside a role's directory.
     *
     * @param role canonical role key (e.g. {@code TENANT_ADMIN})
     * @param file chapter filename (must be in {@link #ALLOWED_FILES})
     * @return the chapter contents, or empty if not found / disallowed
     */
    public Optional<ManualChapter> getChapter(String role, String file) {
        if (role == null || file == null) {
            return Optional.empty();
        }
        String dir = ROLE_DIRS.get(role.toUpperCase(Locale.ROOT));
        if (dir == null || !ALLOWED_FILES.contains(file)) {
            return Optional.empty();
        }
        Path root = manualsRoot;
        if (root == null) {
            log.debug("Chapter requested but manuals root not configured (classpath-only deploy).");
            return Optional.empty();
        }
        Path chapterPath = root.resolve(dir).resolve(file).normalize();
        // Belt-and-suspenders: ensure the resolved path is still under root.
        if (!chapterPath.startsWith(root) || !Files.exists(chapterPath)) {
            return Optional.empty();
        }
        try {
            String content = Files.readString(chapterPath, StandardCharsets.UTF_8);
            String title = entryFor(role).map(ManualIndexEntry::title).orElse(role);
            LocalDate updatedAt = entryFor(role)
                    .map(ManualIndexEntry::updatedAt)
                    .orElse(LocalDate.now(ZoneId.systemDefault()));
            return Optional.of(new ManualChapter(role, dir + "/" + file, title, content, updatedAt));
        } catch (IOException ex) {
            log.error("Failed to read chapter '{}' for role '{}'", file, role, ex);
            return Optional.empty();
        }
    }

    /**
     * Returns the directory name for a role, used by the controller when
     * it needs to enumerate chapters. Empty if the role is unknown.
     */
    public Optional<String> directoryFor(String role) {
        if (role == null) return Optional.empty();
        return Optional.ofNullable(ROLE_DIRS.get(role.toUpperCase(Locale.ROOT)));
    }

    private Optional<ManualIndexEntry> entryFor(String role) {
        return cached.stream()
                .filter(e -> role.equalsIgnoreCase(e.role()))
                .findFirst();
    }

    /**
     * Resolves a manifest path that may be relative to either the JVM working
     * directory or the parent of the working directory. This handles the
     * common case where {@code spring-boot:run} is launched from
     * {@code edushift-back/} and the manifest lives at
     * {@code ../docs/manuales/manifest.json} from the backend's POV.
     */
    private Path resolveFilesystem(String configuredPath) {
        Path direct = Paths.get(configuredPath);
        if (Files.exists(direct)) {
            return direct;
        }
        Path absolute = direct.isAbsolute() ? direct : Paths.get(System.getProperty("user.dir"), configuredPath).toAbsolutePath();
        if (Files.exists(absolute)) {
            return absolute;
        }
        Path sibling = Paths.get(System.getProperty("user.dir")).toAbsolutePath().getParent();
        if (sibling != null) {
            Path fromParent = sibling.resolve(configuredPath);
            if (Files.exists(fromParent)) {
                return fromParent;
            }
        }
        return direct;
    }
}