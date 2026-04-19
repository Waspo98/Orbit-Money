-- =============================================================================
-- 002_seed_categories.sql
-- =============================================================================
-- Seeds the 32 categories observed in Neal's Rocket Money CSV export.
-- Transfer categories (is_transfer=1) are excluded from budget/spending math.
-- Income categories (is_income=1) are treated as positive cash flow.
-- =============================================================================

INSERT INTO categories (name, color, icon, is_transfer, is_income, sort_order) VALUES
  ('Auto & Transport',      '#5B8DEF', '🚗', 0, 0, 10),
  ('Baby',                  '#F6C454', '👶', 0, 0, 20),
  ('Bills & Utilities',     '#FFB547', '💡', 0, 0, 30),
  ('Cash & Checks',         '#4DB6AC', '💵', 0, 0, 40),
  ('Charitable Donations',  '#EC407A', '💝', 0, 0, 50),
  ('Credit Card Payment',   '#7E57C2', '💳', 1, 0, 60),
  ('Dining & Drinks',       '#FF7043', '🍽️', 0, 0, 70),
  ('Education',             '#42A5F5', '🎓', 0, 0, 80),
  ('Entertainment & Rec.',  '#AB47BC', '🎬', 0, 0, 90),
  ('Family Care',           '#EF5350', '👨‍👩‍👧', 0, 0, 100),
  ('Fees',                  '#8D6E63', '💸', 0, 0, 110),
  ('Fun',                   '#FFA726', '🎉', 0, 0, 120),
  ('Gifts',                 '#F06292', '🎁', 0, 0, 130),
  ('Groceries',             '#66BB6A', '🛒', 0, 0, 140),
  ('Health & Wellness',     '#26A69A', '💪', 0, 0, 150),
  ('Home & Garden',         '#8BC34A', '🏠', 0, 0, 160),
  ('Income',                '#4CAF50', '💰', 0, 1, 170),
  ('Internal Transfers',    '#78909C', '🔄', 1, 0, 180),
  ('Investment',            '#00897B', '📈', 0, 0, 190),
  ('Legal',                 '#455A64', '⚖️', 0, 0, 200),
  ('Loan Payment',          '#5E35B1', '🏦', 0, 0, 210),
  ('Medical',               '#E53935', '🏥', 0, 0, 220),
  ('Personal Care',         '#D81B60', '💆', 0, 0, 230),
  ('Pets',                  '#795548', '🐾', 0, 0, 240),
  ('Savings Transfer',      '#7CB342', '💹', 1, 0, 250),
  ('Shopping',              '#EF6C00', '🛍️', 0, 0, 260),
  ('Software & Tech',       '#5C6BC0', '💻', 0, 0, 270),
  ('Taxes',                 '#546E7A', '🧾', 0, 0, 280),
  ('Travel & Vacation',     '#29B6F6', '✈️', 0, 0, 290),
  ('Uncategorized',         '#9E9E9E', '❓', 0, 0, 999),
  ('Venmo/Marketplace',     '#3D5AFE', '💱', 0, 0, 300),
  ('Work Purchases',        '#607D8B', '💼', 0, 0, 310);
