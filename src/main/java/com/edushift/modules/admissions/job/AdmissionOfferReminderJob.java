package com.edushift.modules.admissions.job;

import org.springframework.stereotype.Component;

/** Compatibility entry point. Family confirmation reminders are no longer scheduled or sent. */
@Component
public class AdmissionOfferReminderJob {
    public void processReminders() {
        // Retained for callers compiled against the previous workflow.
    }
}
