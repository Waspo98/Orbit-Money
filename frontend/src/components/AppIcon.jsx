const ICON_PATHS = {
  accounts: new URL('../assets/icons/accounts.svg', import.meta.url).href,
  budgets: new URL('../assets/icons/budgets.svg', import.meta.url).href,
  categories: new URL('../assets/icons/categorymanager.svg', import.meta.url).href,
  dashboard: new URL('../assets/icons/dashboard.svg', import.meta.url).href,
  goals: new URL('../assets/icons/goals.svg', import.meta.url).href,
  household: new URL('../assets/icons/household.svg', import.meta.url).href,
  housing: new URL('../assets/icons/housingcalculator.svg', import.meta.url).href,
  mha: new URL('../assets/icons/mhatracker.svg', import.meta.url).href,
  networth: new URL('../assets/icons/networth.svg', import.meta.url).href,
  retirement: new URL('../assets/icons/retirementcalculator.svg', import.meta.url).href,
  rules: new URL('../assets/icons/rules.svg', import.meta.url).href,
  settings: new URL('../assets/icons/settings.svg', import.meta.url).href,
  spending: new URL('../assets/icons/spendingtrends.svg', import.meta.url).href,
  transactions: new URL('../assets/icons/transactions.svg', import.meta.url).href,
  upcoming: new URL('../assets/icons/upcoming.svg', import.meta.url).href
};

export default function AppIcon({ name, className = '', label }) {
  const path = ICON_PATHS[name];

  if (!path) {
    return (
      <span className={className} aria-hidden={label ? undefined : true}>
        {name}
      </span>
    );
  }

  return (
    <span
      className={`app-icon ${className}`.trim()}
      style={{ '--icon-url': `url("${path}")` }}
      aria-hidden={label ? undefined : true}
      aria-label={label}
      role={label ? 'img' : undefined}
    />
  );
}
