package com.edushift.modules.help.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the {@code help} module (manual index).
 *
 * <p>Defaults are chosen to work out of the box in dev (working dir = backend
 * module root) and to be overridable in production via env vars.</p>
 */
@ConfigurationProperties(prefix = "docs.manuals")
public class ManualIndexProperties {

    /**
     * Filesystem path to the manifest JSON. The service tries this first,
     * then falls back to the classpath resource {@code /help/manifest.json}.
     * Default points to the repo-relative location used during local dev.
     */
    private String manifestPath = "docs/manuales/manifest.json";

    /**
     * Classpath resource used as fallback when the filesystem path does not exist.
     */
    private String classpathResource = "help/manifest.json";

    public String getManifestPath() {
        return manifestPath;
    }

    public void setManifestPath(String manifestPath) {
        this.manifestPath = manifestPath;
    }

    public String getClasspathResource() {
        return classpathResource;
    }

    public void setClasspathResource(String classpathResource) {
        this.classpathResource = classpathResource;
    }
}