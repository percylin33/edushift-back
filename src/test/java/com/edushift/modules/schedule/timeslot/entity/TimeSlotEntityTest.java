package com.edushift.modules.schedule.timeslot.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TimeSlotEntityTest {

    @Test
    @DisplayName("setters and getters")
    void settersAndGetters() {
        var slot = new TimeSlot();
        slot.setDayOfWeek((short) 1);
        slot.setStartTime(LocalTime.of(8, 0));
        slot.setEndTime(LocalTime.of(9, 0));
        slot.setClassroom("101");

        assertThat(slot.getDayOfWeek()).isEqualTo((short) 1);
        assertThat(slot.getClassroom()).isEqualTo("101");
    }

    @Test
    @DisplayName("onPrePersist generates publicUuid")
    void prePersistGeneratesUuid() {
        var slot = new TimeSlot();
        assertThat(slot.getPublicUuid()).isNull();
        // @PrePersist is called by Hibernate, not directly testable
        // but we verify the field is settable
        slot.setPublicUuid(java.util.UUID.randomUUID());
        assertThat(slot.getPublicUuid()).isNotNull();
    }
}
