package com.edushift.modules.payments.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.payments.entity.Invoice;
import com.edushift.modules.payments.entity.InvoiceItem;
import com.edushift.shared.branding.PlatformBrandAssets;
import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvoiceReceiptPdfGeneratorTest {

	private final InvoiceReceiptPdfGenerator generator = new InvoiceReceiptPdfGenerator();

	/** Minimal valid 1×1 PNG. */
	private static final byte[] TINY_PNG = Base64.getDecoder().decode(
			"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

	@Test
	void generate_withoutLogo_emitsValidPdf() {
		Invoice invoice = paidInvoice(35_000L);
		byte[] pdf = generator.generate(new InvoiceReceiptPdfGenerator.ReceiptContext(
				"COLEGIO DE PERCY",
				"#1e3a5f",
				null,
				"AINARA VALDERRAMA",
				invoice,
				List.of()));

		assertThat(pdf).isNotEmpty();
		assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
		assertThat(pdf.length).isGreaterThan(800);
	}

	@Test
	void generate_withLogoAndItems_emitsValidPdf() {
		Invoice invoice = paidInvoice(15_000L);
		InvoiceItem item = new InvoiceItem();
		item.setDescription("Pension primaria");
		item.setQuantity(1);
		item.setUnitAmountCents(15_000L);
		item.setLineTotalCents(15_000L);

		byte[] pdf = generator.generate(new InvoiceReceiptPdfGenerator.ReceiptContext(
				"Colegio Demo",
				"#0ea5e9",
				TINY_PNG,
				"Estudiante Demo",
				invoice,
				List.of(item)));

		assertThat(pdf).isNotEmpty();
		assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
		assertThat(pdf.length).isGreaterThan(800);
	}

	@Test
	void formatSoles_formatsMinorUnits() {
		assertThat(InvoiceReceiptPdfGenerator.formatSoles(20_00L)).isEqualTo("S/ 20.00");
		assertThat(InvoiceReceiptPdfGenerator.formatSoles(12_50L)).isEqualTo("S/ 12.50");
		assertThat(InvoiceReceiptPdfGenerator.formatSoles(35_000L)).isEqualTo("S/ 350.00");
	}

	@Test
	void statusEs_mapsPaidToPagado() {
		assertThat(InvoiceReceiptPdfGenerator.statusEs(Invoice.Status.PAID)).isEqualTo("Pagado");
		assertThat(InvoiceReceiptPdfGenerator.statusEs(Invoice.Status.PENDING_REVIEW))
				.isEqualTo("En revisión");
		assertThat(InvoiceReceiptPdfGenerator.statusEs(null)).isEqualTo("—");
	}

	@Test
	void parseColor_acceptsHexAndFallback() {
		assertThat(InvoiceReceiptPdfGenerator.parseColor("#1e3a5f"))
				.isEqualTo(new Color(0x1e, 0x3a, 0x5f));
		assertThat(InvoiceReceiptPdfGenerator.parseColor("abc"))
				.isEqualTo(new Color(0xaa, 0xbb, 0xcc));
		assertThat(InvoiceReceiptPdfGenerator.parseColor(null))
				.isEqualTo(new Color(0x1e, 0x3a, 0x5f));
		assertThat(InvoiceReceiptPdfGenerator.parseColor("not-a-color"))
				.isEqualTo(new Color(0x1e, 0x3a, 0x5f));
	}

	@Test
	void loadLogoBytes_blank_usesPlatformMark() {
		byte[] mark = PlatformBrandAssets.markBytes();
		assertThat(mark).isNotNull();
		assertThat(generator.loadLogoBytes(null)).isEqualTo(mark);
		assertThat(generator.loadLogoBytes("  ")).isEqualTo(mark);
	}

	private static Invoice paidInvoice(long totalCents) {
		Invoice invoice = new Invoice();
		invoice.setPublicUuid(UUID.fromString("cb360e67-246a-46f5-b344-4035630f70b5"));
		invoice.setPeriodLabel("2026-09");
		invoice.setCurrency("PEN");
		invoice.setSubtotalCents(totalCents);
		invoice.setDiscountCents(0);
		invoice.setTaxCents(0);
		invoice.setTotalCents(totalCents);
		invoice.setMoraCents(0);
		invoice.setStatus(Invoice.Status.PAID);
		invoice.setIssuedAt(Instant.parse("2026-09-01T12:00:00Z"));
		invoice.setDueAt(Instant.parse("2026-09-15T23:59:59Z"));
		invoice.setPaidAt(Instant.parse("2026-09-16T19:41:00Z"));
		return invoice;
	}
}
