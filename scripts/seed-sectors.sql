-- Fills the sectors table with the US and India sector names already in folio_pro. Safe to run again: it only adds
-- missing names and markets, and never renames or removes a sector. Run it as the owner of the tables:
--   psql -h localhost -U folio_pro -d folio_pro -v ON_ERROR_STOP=1 -f scripts/seed-sectors.sql
BEGIN;

-- Same columns as the Sector entity; Hibernate adds them when the service starts first. Existing rows are US sectors.
ALTER TABLE sectors ADD COLUMN IF NOT EXISTS is_us BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE sectors ADD COLUMN IF NOT EXISTS is_india BOOLEAN NOT NULL DEFAULT FALSE;

-- US: FMP profiles. India: watchlists (organized by sector, owned by folio-portfolio-api and only read here) and the
-- saved Tapetide profiles.
CREATE TEMP TABLE found_sectors ON COMMIT DROP AS
SELECT MIN(TRIM(name)) AS name, LOWER(TRIM(name)) AS name_key, BOOL_OR(us) AS us, BOOL_OR(india) AS india
FROM (
  SELECT sector AS name, TRUE AS us, FALSE AS india FROM fmp_stock_profiles
  UNION ALL
  SELECT name, FALSE, TRUE FROM watchlists_india
  UNION ALL
  SELECT payload::jsonb #>> '{data,company,sector}', FALSE, TRUE FROM tapetide_stock_datasets WHERE dataset = 'profile'
) names
WHERE TRIM(name) <> '' AND LENGTH(TRIM(name)) <= 100
GROUP BY LOWER(TRIM(name));

UPDATE sectors SET is_us = is_us OR found.us, is_india = is_india OR found.india
FROM found_sectors found
WHERE LOWER(sectors.sector_name) = found.name_key;

INSERT INTO sectors (sector_name, is_us, is_india)
SELECT found.name, found.us, found.india
FROM found_sectors found
WHERE NOT EXISTS (SELECT 1 FROM sectors WHERE LOWER(sectors.sector_name) = found.name_key)
ORDER BY found.name;

COMMIT;
