package com.edushift.modules.sessions.learning.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LearningEntityTest {

    @Test
    @DisplayName("SessionStatus values and transitions")
    void sessionStatus() {
        assertThat(SessionStatus.PLANNED).isEqualTo(SessionStatus.valueOf("PLANNED"));
        assertThat(SessionStatus.valueOf("IN_PROGRESS")).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(SessionStatus.valueOf("COMPLETED")).isEqualTo(SessionStatus.COMPLETED);
        assertThat(SessionStatus.valueOf("CANCELLED")).isEqualTo(SessionStatus.CANCELLED);
    }

    @Test
    @DisplayName("SessionStatus.isTerminal")
    void terminalStates() {
        assertThat(SessionStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(SessionStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(SessionStatus.PLANNED.isTerminal()).isFalse();
        assertThat(SessionStatus.IN_PROGRESS.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("SessionStatus.canTransitionTo")
    void validTransitions() {
        assertThat(SessionStatus.PLANNED.canTransitionTo(SessionStatus.IN_PROGRESS)).isTrue();
        assertThat(SessionStatus.PLANNED.canTransitionTo(SessionStatus.CANCELLED)).isTrue();
        assertThat(SessionStatus.PLANNED.canTransitionTo(SessionStatus.COMPLETED)).isFalse();

        assertThat(SessionStatus.IN_PROGRESS.canTransitionTo(SessionStatus.COMPLETED)).isTrue();
        assertThat(SessionStatus.IN_PROGRESS.canTransitionTo(SessionStatus.CANCELLED)).isTrue();
        assertThat(SessionStatus.IN_PROGRESS.canTransitionTo(SessionStatus.PLANNED)).isFalse();

        assertThat(SessionStatus.COMPLETED.canTransitionTo(SessionStatus.PLANNED)).isFalse();
        assertThat(SessionStatus.COMPLETED.canTransitionTo(SessionStatus.CANCELLED)).isFalse();

        assertThat(SessionStatus.CANCELLED.canTransitionTo(SessionStatus.PLANNED)).isFalse();
    }

    @Test
    @DisplayName("SessionStatus.canTransitionTo with null/self")
    void invalidTransitions() {
        assertThat(SessionStatus.PLANNED.canTransitionTo(null)).isFalse();
        assertThat(SessionStatus.PLANNED.canTransitionTo(SessionStatus.PLANNED)).isFalse();
    }

    @Test
    @DisplayName("SessionContent defaults")
    void sessionContentDefaults() {
        var c = new SessionContent();
        assertThat(c.getActivities()).isNotNull();
        assertThat(c.getMaterials()).isNotNull();
    }

    @Test
    @DisplayName("SessionContent all-args constructor")
    void sessionContentAllArgs() {
        var c = new SessionContent("Obj", List.of("Act"), List.of("Mat"), "Obs");
        assertThat(c.getObjective()).isEqualTo("Obj");
        assertThat(c.getActivities()).containsExactly("Act");
    }

    @Test
    @DisplayName("LearningSession defaults status to PLANNED")
    void sessionDefaults() {
        var s = new LearningSession();
        assertThat(s.getStatus()).isEqualTo(SessionStatus.PLANNED);
    }

    @Test
    @DisplayName("LearningSession markDeleted/restore")
    void sessionLifecycle() {
        var s = new LearningSession();
        s.markDeleted();
        assertThat(s.getDeletedAt()).isNotNull();
        s.restore();
        assertThat(s.getDeletedAt()).isNull();
    }
}
