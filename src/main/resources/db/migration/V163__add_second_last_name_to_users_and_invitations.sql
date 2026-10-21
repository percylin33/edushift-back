-- Preserve the full Spanish surname structure on accounts and invitation snapshots.
-- Existing student-linked accounts receive the maternal surname from their student record.
ALTER TABLE edushift.users
    ADD COLUMN second_last_name varchar(100);

ALTER TABLE edushift.user_invitations
    ADD COLUMN second_last_name varchar(100);

UPDATE edushift.users u
SET second_last_name = s.second_last_name
FROM edushift.students s
WHERE s.user_id = u.public_uuid
  AND s.tenant_id = u.tenant_id
  AND s.deleted = false
  AND u.deleted = false
  AND s.second_last_name IS NOT NULL;

COMMENT ON COLUMN edushift.users.second_last_name
    IS 'Optional second surname; kept in sync with the linked student legal name.';
COMMENT ON COLUMN edushift.user_invitations.second_last_name
    IS 'Optional second surname snapshotted from the invited profile.';
