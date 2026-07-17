package com.edushift.modules.sessions.learning.service;

import com.edushift.modules.sessions.learning.entity.LearningSession;
import com.edushift.modules.sessions.learning.entity.SessionContent;
import com.edushift.modules.sessions.learning.entity.SessionStatus;
import com.edushift.modules.sessions.learning.repository.LearningSessionRepository;
import com.edushift.modules.tenants.service.TenantService;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionsPdfExportService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", new Locale("es", "PE"));
    private static final DateTimeFormatter DATETIME_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", new Locale("es", "PE"));

    private static final Font TITLE_FONT = FontFactory.getFont(
            FontFactory.HELVETICA_BOLD, 18, Font.BOLD);
    private static final Font SUBTITLE_FONT = FontFactory.getFont(
            FontFactory.HELVETICA, 14, Font.BOLD);
    private static final Font BODY_FONT = FontFactory.getFont(
            FontFactory.HELVETICA, 11);
    private static final Font LABEL_FONT = FontFactory.getFont(
            FontFactory.HELVETICA_BOLD, 11);
    private static final Font FOOTER_FONT = FontFactory.getFont(
            FontFactory.HELVETICA, 8);

    private final LearningSessionRepository sessionRepository;
    private final TenantService tenantService;

    @Transactional(readOnly = true)
    public byte[] exportPdf(UUID sessionPublicUuid) {
        LearningSession session = sessionRepository.findByPublicUuid(sessionPublicUuid)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "LearningSession", sessionPublicUuid));

        String tenantName = tenantService.findCurrent().name();

        ByteArrayOutputStream baos = new ByteArrayOutputStream(8192);
        Document document = new Document(PageSize.A4, 36, 36, 40, 40);
        try {
            PdfWriter.getInstance(document, baos);
            document.open();
            buildPdf(document, session, tenantName);
        } catch (DocumentException e) {
            throw new RuntimeException("Error generating session PDF", e);
        } finally {
            document.close();
        }
        return baos.toByteArray();
    }

    private void buildPdf(Document doc, LearningSession session, String tenantName)
            throws DocumentException {

        // Header — school name + document title
        Paragraph header = new Paragraph(tenantName, TITLE_FONT);
        header.setAlignment(Element.ALIGN_CENTER);
        doc.add(header);

        Paragraph docTitle = new Paragraph(
                "Sesión de Aprendizaje", SUBTITLE_FONT);
        docTitle.setAlignment(Element.ALIGN_CENTER);
        docTitle.setSpacingAfter(16);
        doc.add(docTitle);

        // Session info table (2 columns: label + value)
        PdfPTable info = new PdfPTable(2);
        info.setWidthPercentage(100);
        info.setWidths(new float[]{28, 72});
        info.setSpacingAfter(12);

        addInfoRow(info, "Título", session.getTitle());
        addInfoRow(info, "Fecha",
                session.getScheduledDate() != null
                        ? session.getScheduledDate().format(DATE_FMT)
                        : "");
        addInfoRow(info, "Duración",
                session.getDurationMinutes() != null
                        ? session.getDurationMinutes() + " minutos"
                        : "");
        addInfoRow(info, "Estado", formatStatus(session.getStatus()));

        if (session.getTeacherAssignment() != null) {
            var ta = session.getTeacherAssignment();
            if (ta.getTeacher() != null) {
                addInfoRow(info, "Docente",
                        ta.getTeacher().getFirstName() + " " + ta.getTeacher().getLastName());
            }
            if (ta.getCourse() != null) {
                addInfoRow(info, "Curso", ta.getCourse().getName());
            }
            if (ta.getSection() != null) {
                addInfoRow(info, "Sección", ta.getSection().getName());
            }
            if (ta.getAcademicPeriod() != null) {
                addInfoRow(info, "Periodo", ta.getAcademicPeriod().getName());
            }
        }
        if (session.getUnit() != null) {
            addInfoRow(info, "Unidad", session.getUnit().getName());
        }
        doc.add(info);

        // Objective
        Paragraph objLabel = new Paragraph("Objetivo", SUBTITLE_FONT);
        objLabel.setSpacingAfter(4);
        doc.add(objLabel);
        Paragraph objective = new Paragraph(
                session.getObjective() != null ? session.getObjective() : "",
                BODY_FONT);
        objective.setSpacingAfter(12);
        doc.add(objective);

        // Session content (from JSONB)
        SessionContent content = session.getContent();
        if (content != null) {
            // Activities
            if (content.getActivities() != null && !content.getActivities().isEmpty()) {
                Paragraph actLabel = new Paragraph("Actividades", SUBTITLE_FONT);
                actLabel.setSpacingAfter(4);
                doc.add(actLabel);
                for (int i = 0; i < content.getActivities().size(); i++) {
                    String a = content.getActivities().get(i);
                    Paragraph item = new Paragraph(
                            "  " + (i + 1) + ". " + (a != null ? a : ""), BODY_FONT);
                    item.setSpacingAfter(2);
                    doc.add(item);
                }
                doc.add(new Paragraph(" ")); // spacer
            }

            // Materials
            if (content.getMaterials() != null && !content.getMaterials().isEmpty()) {
                Paragraph matLabel = new Paragraph("Materiales", SUBTITLE_FONT);
                matLabel.setSpacingAfter(4);
                doc.add(matLabel);
                for (String m : content.getMaterials()) {
                    Paragraph item = new Paragraph(
                            "  • " + (m != null ? m : ""), BODY_FONT);
                    item.setSpacingAfter(2);
                    doc.add(item);
                }
                doc.add(new Paragraph(" "));
            }

            // Observations
            if (content.getObservations() != null && !content.getObservations().isBlank()) {
                Paragraph obsLabel = new Paragraph("Observaciones", SUBTITLE_FONT);
                obsLabel.setSpacingAfter(4);
                doc.add(obsLabel);
                Paragraph obs = new Paragraph(content.getObservations(), BODY_FONT);
                obs.setSpacingAfter(12);
                doc.add(obs);
            }
        }

        // Competencies
        if (session.getCompetencies() != null && !session.getCompetencies().isEmpty()) {
            Paragraph compLabel = new Paragraph("Competencias", SUBTITLE_FONT);
            compLabel.setSpacingAfter(4);
            doc.add(compLabel);
            session.getCompetencies().forEach(c -> {
                Paragraph item = new Paragraph(
                        "  • " + (c.getName() != null ? c.getName() : ""), BODY_FONT);
                item.setSpacingAfter(2);
                doc.add(item);
            });
            doc.add(new Paragraph(" "));
        }

        // Capacities
        if (session.getCapacities() != null && !session.getCapacities().isEmpty()) {
            Paragraph capLabel = new Paragraph("Capacidades", SUBTITLE_FONT);
            capLabel.setSpacingAfter(4);
            doc.add(capLabel);
            session.getCapacities().forEach(c -> {
                Paragraph item = new Paragraph(
                        "  • " + (c.getName() != null ? c.getName() : ""), BODY_FONT);
                item.setSpacingAfter(2);
                doc.add(item);
            });
        }

        // Footer — generation timestamp
        Paragraph footer = new Paragraph(
                "Generado el "
                        + java.time.Instant.now()
                                .atZone(ZoneId.of("America/Lima"))
                                .format(DATETIME_FMT)
                        + " — EduShift",
                FOOTER_FONT);
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(20);
        doc.add(footer);
    }

    private void addInfoRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, LABEL_FONT));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPaddingBottom(4);
        labelCell.setPaddingRight(8);
        labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(
                value != null ? value : "", BODY_FONT));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPaddingBottom(4);
        table.addCell(valueCell);
    }

    private String formatStatus(SessionStatus status) {
        if (status == null) return "";
        return switch (status) {
            case PLANNED -> "Planificado";
            case IN_PROGRESS -> "En progreso";
            case COMPLETED -> "Completado";
            case CANCELLED -> "Cancelado";
        };
    }
}
