package com.edushift.modules.admissions.dto;

import java.util.UUID;

/** Minimal public acknowledgment; does not echo submitted contact details. */
public record PublicInquiryReceipt(UUID reference, String status) {}
