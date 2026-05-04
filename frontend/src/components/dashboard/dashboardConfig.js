export const DASHBOARD_RETIREMENT_PRESETS = {
  conservative: { label: 'Conservative', annualReturn: 0.05, inflation: 0.03 },
  balanced: { label: 'Balanced', annualReturn: 0.07, inflation: 0.025 },
  aggressive: { label: 'Aggressive', annualReturn: 0.085, inflation: 0.0225 }
};

export const DASHBOARD_CARD_DEFS = [
  {
    id: 'accounts',
    title: 'Accounts',
    description: 'Net worth and account balance groups.'
  },
  {
    id: 'top-spending',
    title: 'Top Spending',
    description: 'Largest spending categories this month.'
  },
  {
    id: 'this-month',
    title: 'This Month',
    description: 'Income, expenses, and net cash flow.'
  },
  {
    id: 'budget-pulse',
    title: 'Budget Pulse',
    description: 'Budget usage, pace, and categories to watch.'
  },
  {
    id: 'biggest-transactions',
    title: 'Biggest Monthly Transactions',
    description: 'Top 5 current-month expense transactions with dashboard-only hiding.'
  },
  {
    id: 'subscriptions',
    title: 'Subscriptions / Recurring',
    description: 'Saved subscription items from Upcoming.'
  },
  {
    id: 'uncategorized',
    title: 'Uncategorized Transactions',
    description: 'Recent transactions that need a category.'
  },
  {
    id: 'categorize-recent',
    title: 'Categorize Recent Transactions',
    description: 'Swipe through transactions that need a quick category check.'
  },
  {
    id: 'month-comparison',
    title: 'Month vs Last Month',
    description: 'Spending, income, and net change.'
  },
  {
    id: 'goals-progress',
    title: 'Goals Progress',
    description: 'Overall progress across active goals.'
  },
  {
    id: 'goal-focus',
    title: 'Goal Focus',
    description: 'One goal that deserves attention.'
  },
  {
    id: 'retirement',
    title: 'Retirement Snapshot',
    description: 'Linked retirement balance and contributions.'
  },
  {
    id: 'mha',
    title: 'MHA Tracker Summary',
    description: 'Year-to-date MHA eligible spending.'
  },
  {
    id: 'upcoming',
    title: 'Upcoming',
    description: 'Saved bills, subscriptions, and income.'
  },
  {
    id: 'mortgage',
    title: 'Mortgage Snapshot',
    description: 'Home value, balance, and equity.'
  },
  {
    id: 'recent-activity',
    title: 'Recent Transactions',
    description: 'Latest activity with full transaction actions.'
  }
];

const DEFAULT_DASHBOARD_CARD_IDS = DASHBOARD_CARD_DEFS.map((card) => card.id);

export function normalizeDashboardLayout(saved) {
  const fallback = DEFAULT_DASHBOARD_CARD_IDS.map((id) => ({ id, visible: true }));
  if (!Array.isArray(saved)) return fallback;

  const known = new Set(DEFAULT_DASHBOARD_CARD_IDS);
  const rows = [];
  for (const item of saved) {
    const id = typeof item === 'string' ? item : item?.id;
    if (!known.has(id) || rows.some((row) => row.id === id)) continue;
    rows.push({ id, visible: item?.visible !== false });
  }
  for (const id of DEFAULT_DASHBOARD_CARD_IDS) {
    if (!rows.some((row) => row.id === id)) rows.push({ id, visible: true });
  }
  return rows;
}

export function normalizeIdArray(value) {
  return Array.isArray(value)
    ? Array.from(new Set(value.map(Number).filter(Number.isFinite))).slice(-1000)
    : [];
}

export function normalizeGoalFocusId(value) {
  const id = Number(value);
  return Number.isFinite(id) && id > 0 ? id : null;
}

export function normalizeRetirementPreferences(value) {
  const prefs = value && typeof value === 'object' ? value : {};
  return {
    retirementAge: Number(prefs.retirementAge) || 67,
    presetKey: DASHBOARD_RETIREMENT_PRESETS[prefs.presetKey] ? prefs.presetKey : 'balanced'
  };
}
