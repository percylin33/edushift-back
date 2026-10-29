package com.edushift.modules.students.dto;

public record GuardianPortalAccessResponse(Status status) {
	public enum Status {
		LINKED,
		INVITATION_SENT,
		PENDING,
		REVIEW_REQUIRED
	}
}
