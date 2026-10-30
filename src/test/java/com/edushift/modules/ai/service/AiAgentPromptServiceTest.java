package com.edushift.modules.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.edushift.modules.ai.entity.AiAgentType;
import com.edushift.modules.ai.rag.AiSourceDocument;
import com.edushift.modules.ai.rag.AiSourceSearchService;
import com.edushift.modules.notifications.entity.Announcement.AudienceType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AiAgentPromptServiceTest {

    @Test
    void instructsAgentToAnswerNaturallyWithoutDumpingRagMetadata() {
        AiSourceSearchService sources = mock(AiSourceSearchService.class);
        when(sources.search(anyString(), eq(AiSourceDocument.Audience.TEACHER), eq(6)))
                .thenReturn(List.of());
        AiAgentPromptService service = new AiAgentPromptService(
                sources, null, null, null, null);

        var context = service.build(AiAgentType.TEACHER, null, null, "hola");

        assertThat(context.systemPrompt())
                .contains("responde brevemente")
                .contains("no los copies")
                .contains("No generes tablas vacías")
                .contains("La interfaz presenta las fuentes consultadas por separado")
                .contains("(No se encontró ninguna fuente documental autorizada.)");
    }

    @Test
    void includesOnlyChildScopedAnnouncements() {
        Set<String> grades = Set.of("grade-a");
        Set<String> sections = Set.of("section-a");
        Set<String> courses = Set.of(); // no schedule for the child

        assertThat(AiAgentPromptService.matchesAnnouncement(
                AudienceType.SCHOOL, List.of(), grades, sections, courses)).isTrue();
        assertThat(AiAgentPromptService.matchesAnnouncement(
                AudienceType.GRADE, List.of("grade-a"), grades, sections, courses)).isTrue();
        assertThat(AiAgentPromptService.matchesAnnouncement(
                AudienceType.SECTION, List.of("section-a"), grades, sections, courses)).isTrue();
        assertThat(AiAgentPromptService.matchesAnnouncement(
                AudienceType.GRADE, List.of("grade-b"), grades, sections, courses)).isFalse();
        assertThat(AiAgentPromptService.matchesAnnouncement(
                AudienceType.SECTION, List.of("section-b"), grades, sections, courses)).isFalse();
        assertThat(AiAgentPromptService.matchesAnnouncement(
                AudienceType.COURSE, List.of("course-a"), grades, sections, courses)).isFalse();
        assertThat(AiAgentPromptService.matchesAnnouncement(
                AudienceType.USER, List.of("parent-a"), grades, sections, courses)).isFalse();
    }
}
