-- =============================================================================
-- 017_goal_sort_order.sql
-- =============================================================================
-- Adds user-controlled ordering for savings goals.
-- =============================================================================

ALTER TABLE goals ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0;

UPDATE goals
   SET sort_order = (
     SELECT COUNT(*)
       FROM goals g2
      WHERE datetime(g2.updated_at) > datetime(goals.updated_at)
         OR (g2.updated_at = goals.updated_at AND g2.id > goals.id)
   );

CREATE INDEX idx_goals_sort_order
  ON goals(sort_order ASC, id ASC);
