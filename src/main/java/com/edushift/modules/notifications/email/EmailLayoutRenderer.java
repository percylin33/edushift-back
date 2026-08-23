package com.edushift.modules.notifications.email;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Wraps email body fragments in a responsive, table-based HTML shell
 * with tenant branding (logo + primary color).
 *
 * <p>Email clients require inline CSS and table layouts — avoid flexbox/grid.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailLayoutRenderer {

	private static final String LAYOUT_PATH = "email/layout.html";

	private final EmailBrandingResolver brandingResolver;
	private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

	/**
	 * Wrap a body fragment using current-tenant branding.
	 *
	 * @param bodyHtml  HTML fragment (no full document required)
	 * @param preheader preview text for inbox clients; may be null
	 */
	public String wrapBody(String bodyHtml, String preheader) {
		return wrap(bodyHtml, preheader, brandingResolver.current());
	}

	public String wrap(String bodyHtml, String preheader, EmailBrandingContext branding) {
		EmailBrandingContext ctx = branding == null
				? EmailBrandingContext.edushiftDefault(null)
				: branding;
		String layout = load(LAYOUT_PATH);
		String logoBlock = buildLogoBlock(ctx);
		return layout
				.replace("{{preheader}}", escapeText(preheader == null ? "" : preheader))
				.replace("{{tenantName}}", escapeText(ctx.nameOrDefault()))
				.replace("{{primaryColor}}", escapeText(ctx.primaryOrDefault()))
				.replace("{{logoBlock}}", logoBlock)
				.replace("{{body}}", bodyHtml == null ? "" : bodyHtml)
				.replace("{{footerLine}}", escapeText(footerLine(ctx)));
	}

	/** Render a classpath HTML fragment with simple {@code {{key}}} replaces. */
	public String renderFragment(String classpathRelative, java.util.Map<String, String> values) {
		String html = load("email/" + classpathRelative);
		if (values != null) {
			for (var e : values.entrySet()) {
				html = html.replace("{{" + e.getKey() + "}}", e.getValue() == null ? "" : e.getValue());
			}
		}
		return html;
	}

	private static String buildLogoBlock(EmailBrandingContext ctx) {
		String url = ctx.logoUrl();
		String name = escapeText(ctx.nameOrDefault());
		if (url != null && !url.isBlank()) {
			return """
					<img src="%s" alt="%s" width="140" style="display:block;max-width:140px;height:auto;border:0;outline:none;text-decoration:none" />
					""".formatted(escapeAttr(url), name);
		}
		return """
				<span style="font-size:20px;font-weight:700;color:%s;letter-spacing:-0.02em">%s</span>
				""".formatted(escapeText(ctx.primaryOrDefault()), name);
	}

	private static String footerLine(EmailBrandingContext ctx) {
		if (ctx.slug() != null && !ctx.slug().isBlank()) {
			return "Enviado por " + ctx.nameOrDefault() + " · EduShift";
		}
		return "Enviado por EduShift";
	}

	private String load(String path) {
		return cache.computeIfAbsent(path, p -> {
			try {
				ClassPathResource res = new ClassPathResource(p);
				try (InputStream in = res.getInputStream()) {
					return new String(in.readAllBytes(), StandardCharsets.UTF_8);
				}
			} catch (IOException e) {
				log.error("[EmailLayout] failed to load classpath resource {}", p, e);
				return "{{body}}";
			}
		});
	}

	private static String escapeText(String s) {
		if (s == null) return "";
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
				.replace("\"", "&quot;");
	}

	private static String escapeAttr(String s) {
		return escapeText(s).replace("'", "&#39;");
	}
}
