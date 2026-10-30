-- =============================================================================
-- V120 — Subscription billing calendar: preferred day (and month for annual)
-- =============================================================================
-- billing_day_of_month: 1–31. If a month is shorter, charge on that month's last day.
-- billing_month: 1–12, used when billing_period = ANNUAL (null for MONTHLY).
-- =============================================================================

ALTER TABLE edushift.subscriptions
    ADD COLUMN IF NOT EXISTS billing_day_of_month smallint,
    ADD COLUMN IF NOT EXISTS billing_month smallint;

COMMENT ON COLUMN edushift.subscriptions.billing_day_of_month IS
    'Preferred day of month (1-31). Clamped to last day when the month is shorter.';
COMMENT ON COLUMN edushift.subscriptions.billing_month IS
    'Preferred calendar month (1-12) for ANNUAL plans; null for MONTHLY.';

-- Backfill from next_billing_at when present
UPDATE edushift.subscriptions
SET billing_day_of_month = EXTRACT(DAY FROM next_billing_at AT TIME ZONE 'UTC')::smallint
WHERE billing_day_of_month IS NULL
  AND next_billing_at IS NOT NULL;

UPDATE edushift.subscriptions
SET billing_month = EXTRACT(MONTH FROM next_billing_at AT TIME ZONE 'UTC')::smallint
WHERE billing_period = 'ANNUAL'
  AND billing_month IS NULL
  AND next_billing_at IS NOT NULL;

UPDATE edushift.subscriptions
SET billing_day_of_month = 1
WHERE billing_day_of_month IS NULL;
