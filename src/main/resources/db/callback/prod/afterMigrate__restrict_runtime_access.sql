-- Defaults grant DML to application tables, but never to Flyway's own history.
-- Conditional for other production providers that have not adopted these roles.
DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'edushift_app') THEN
        REVOKE ALL ON edushift.flyway_schema_history FROM edushift_app, PUBLIC;
    END IF;
END
$$;
