-- Default colors for the Peru course catalog (plantilla "Cursos usuales").
-- COMP/PROG are matched before COM so Computación is not painted as Comunicación.
-- PSI is matched before PS so Psicomotricidad is not painted as Personal Social.
-- Short codes (PS, EF, ER, AC, COM) are exact so EDF and other workshops stay untouched.
-- Personal Social and Ciencias Sociales share orange on purpose.

UPDATE edushift.courses
SET color = CASE
        WHEN upper(code) LIKE 'COMP%' OR upper(code) LIKE 'PROG%' THEN '#22D3EE'
        WHEN upper(code) LIKE 'COMU%' OR upper(code) = 'COM' OR upper(code) LIKE 'COM-%' THEN '#DC2626'
        WHEN upper(code) LIKE 'PSI%' THEN '#84CC16'
        WHEN upper(code) = 'PS' OR upper(code) LIKE 'PS-%' THEN '#F97316'
        WHEN upper(code) LIKE 'CYT%' OR upper(code) LIKE 'CCNN%' OR upper(code) LIKE 'CIE%' THEN '#16A34A'
        WHEN upper(code) LIKE 'CCSS%' OR upper(code) LIKE 'HIS%' OR upper(code) LIKE 'GEO%' THEN '#F97316'
        WHEN upper(code) LIKE 'DPCC%' THEN '#0F766E'
        WHEN upper(code) LIKE 'EPT%' THEN '#B45309'
        WHEN upper(code) LIKE 'ING%' THEN '#EAB308'
        WHEN upper(code) = 'EF' OR upper(code) LIKE 'EF-%' THEN '#DB2777'
        WHEN upper(code) = 'ER' OR upper(code) LIKE 'ER-%' OR upper(code) LIKE 'REL%' THEN '#FFFFFF'
        WHEN upper(code) LIKE 'TUT%' THEN '#6366F1'
        WHEN upper(code) = 'AC' OR upper(code) LIKE 'AC-%' OR upper(code) LIKE 'ART%' THEN '#7C3AED'
        WHEN upper(code) LIKE 'MAT%' THEN '#2563EB'
        ELSE color
    END,
    updated_at = NOW()
WHERE deleted = false
  AND (
        upper(code) LIKE 'COMP%'
        OR upper(code) LIKE 'PROG%'
        OR upper(code) LIKE 'COMU%'
        OR upper(code) = 'COM'
        OR upper(code) LIKE 'COM-%'
        OR upper(code) LIKE 'PSI%'
        OR upper(code) = 'PS'
        OR upper(code) LIKE 'PS-%'
        OR upper(code) LIKE 'CYT%'
        OR upper(code) LIKE 'CCNN%'
        OR upper(code) LIKE 'CIE%'
        OR upper(code) LIKE 'CCSS%'
        OR upper(code) LIKE 'HIS%'
        OR upper(code) LIKE 'GEO%'
        OR upper(code) LIKE 'DPCC%'
        OR upper(code) LIKE 'EPT%'
        OR upper(code) LIKE 'ING%'
        OR upper(code) = 'EF'
        OR upper(code) LIKE 'EF-%'
        OR upper(code) = 'ER'
        OR upper(code) LIKE 'ER-%'
        OR upper(code) LIKE 'REL%'
        OR upper(code) LIKE 'TUT%'
        OR upper(code) = 'AC'
        OR upper(code) LIKE 'AC-%'
        OR upper(code) LIKE 'ART%'
        OR upper(code) LIKE 'MAT%'
    );
