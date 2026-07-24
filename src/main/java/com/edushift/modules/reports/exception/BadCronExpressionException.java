package com.edushift.modules.reports.exception;

import com.edushift.shared.exception.BusinessException;

/**
 * Thrown when a {@code ReportTemplate} cron expression fails Spring's
 * {@code CronExpression.parse(...)}. Surfaces as 400 {@code REPORT_TEMPLATE_BAD_CRON}.
 */
public class BadCronExpressionException extends BusinessException {
	public BadCronExpressionException(String cron) {
		super("REPORT_TEMPLATE_BAD_CRON",
				"Invalid cron expression: '" + cron + "'. Expected Spring format 'sec min hour dom mon dow'.");
	}
}