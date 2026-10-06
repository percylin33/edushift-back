-- V108: optional visual identity for academic courses (color + icon).
-- Both columns nullable; create-path defaults applied in application layer.

ALTER TABLE edushift.courses
	ADD COLUMN IF NOT EXISTS color varchar(7),
	ADD COLUMN IF NOT EXISTS icon varchar(64);

COMMENT ON COLUMN edushift.courses.color IS
	'Optional hex color (#RRGGBB) for FE chips/avatars. Null → app default by code.';
COMMENT ON COLUMN edushift.courses.icon IS
	'Optional Lucide-style icon key (FE icons.registry). Null → app default by code.';

-- Backfill known seed / common codes (case-insensitive on code).
UPDATE edushift.courses
SET
	color = CASE upper(code)
		WHEN 'MAT' THEN '#3B82F6'
		WHEN 'COMU' THEN '#F59E0B'
		WHEN 'COM' THEN '#F59E0B'
		WHEN 'CCNN' THEN '#10B981'
		WHEN 'CIE' THEN '#10B981'
		WHEN 'CCSS' THEN '#8B5CF6'
		WHEN 'HIS' THEN '#8B5CF6'
		WHEN 'ING' THEN '#06B6D4'
		WHEN 'EF' THEN '#EF4444'
		WHEN 'EDF' THEN '#EF4444'
		WHEN 'PROG' THEN '#6366F1'
		WHEN 'COMP' THEN '#6366F1'
		WHEN 'ART' THEN '#EC4899'
		WHEN 'MUS' THEN '#EC4899'
		WHEN 'VAL' THEN '#14B8A6'
		ELSE color
	END,
	icon = CASE upper(code)
		WHEN 'MAT' THEN 'target'
		WHEN 'COMU' THEN 'book-open'
		WHEN 'COM' THEN 'book-open'
		WHEN 'CCNN' THEN 'sparkles'
		WHEN 'CIE' THEN 'sparkles'
		WHEN 'CCSS' THEN 'globe'
		WHEN 'HIS' THEN 'globe'
		WHEN 'ING' THEN 'globe'
		WHEN 'EF' THEN 'award'
		WHEN 'EDF' THEN 'award'
		WHEN 'PROG' THEN 'layers'
		WHEN 'COMP' THEN 'layers'
		WHEN 'ART' THEN 'palette'
		WHEN 'MUS' THEN 'palette'
		WHEN 'VAL' THEN 'award'
		ELSE icon
	END
WHERE deleted = false
	AND (
		upper(code) IN (
			'MAT', 'COMU', 'COM', 'CCNN', 'CIE', 'CCSS', 'HIS',
			'ING', 'EF', 'EDF', 'PROG', 'COMP', 'ART', 'MUS', 'VAL'
		)
	);
