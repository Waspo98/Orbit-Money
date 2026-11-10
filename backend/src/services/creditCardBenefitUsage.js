export function applyBenefitUsage(db, { householdId, accountId, benefitId, periodKey, amount, usedOn }) {
  return db.transaction(() => {
    const row = db
      .prepare('SELECT * FROM credit_card_profiles WHERE household_id = ? AND account_id = ?')
      .get(householdId, accountId);
    if (!row) return null;

    let benefits = [];
    try {
      benefits = JSON.parse(row.benefits_json || '[]');
    } catch {
      benefits = [];
    }
    if (!Array.isArray(benefits)) benefits = [];

    const benefit = benefits.find((b) => b && b.id === benefitId);
    if (!benefit) return null;

    const currentUsage = Array.isArray(benefit.usage) ? benefit.usage : [];
    const filteredUsage = currentUsage.filter((u) => u && u.period_key !== periodKey);

    if (amount !== null && amount !== undefined && Number.isFinite(amount) && amount > 0) {
      filteredUsage.push({
        period_key: periodKey,
        amount: Math.round(amount * 100) / 100,
        used_on: usedOn || null
      });
    }

    benefit.usage = filteredUsage.slice(-120);

    db.prepare(
      `UPDATE credit_card_profiles
          SET benefits_json = ?,
              updated_at = datetime('now')
        WHERE household_id = ?
          AND account_id = ?`
    ).run(JSON.stringify(benefits), householdId, accountId);

    return db
      .prepare('SELECT * FROM credit_card_profiles WHERE household_id = ? AND account_id = ?')
      .get(householdId, accountId);
  })();
}
