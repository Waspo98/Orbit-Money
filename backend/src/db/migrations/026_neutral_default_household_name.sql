-- 026_neutral_default_household_name.sql
-- Public installs should not inherit the original private household label.
-- Only rename household 1 when it still has the old default value.

UPDATE households
   SET name = 'My Household',
       updated_at = datetime('now')
 WHERE id = 1
   AND name = 'Neal Household';
