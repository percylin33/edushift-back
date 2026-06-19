-- Reset password to canonical EduShift2026! hash
-- (same hash used by DevDataInitializer for sentinel-replaced passwords)
UPDATE edushift.users
SET password_hash = '$2a$12$D2tV1QwrmIuDnbbI/tMLHOSgmL041bZB6qZonL9vhjizS1/OrsFm2',
    updated_at = now()
WHERE email IN ('admin@tecnosur.edushift.pe', 'p3r.valderrama@gmail.com')
  AND deleted = false
RETURNING email, substring(password_hash, 1, 30) || '...' AS new_hash;
