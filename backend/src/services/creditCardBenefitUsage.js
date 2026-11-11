function kotlinUnsignedHashBase36(str) {
  let h = 0;
  for (let i = 0; i < str.length; i += 1) {
    h = (Math.imul(31, h) + str.charCodeAt(i)) | 0;
  }
  return (h >>> 0).toString(36);
}

function cadencePeriodMonths(cadence) {
  const norm = String(cadence || 'annual').toLowerCase();
  if (norm === 'monthly') return 1;
  if (norm === 'quarterly') return 3;
  if (norm === 'semi_annual') return 6;
  if (norm === 'every_4_years') return 48;
  return 12;
}

function upgradeLegacyBenefit(benefit, index, fallbackYear) {
  if (!benefit || typeof benefit !== 'object') return benefit;
  const rawId = typeof benefit.id === 'string' ? benefit.id.trim() : '';
  const usedPeriods = Array.isArray(benefit.used_periods)
    ? benefit.used_periods
    : Array.isArray(benefit.usedPeriods)
      ? benefit.usedPeriods
      : [];
  const usedThisYear = Boolean(benefit.used_this_year ?? benefit.usedThisYear ?? false);

  if (rawId && usedPeriods.length === 0 && !usedThisYear) {
    return benefit;
  }

  const title = String(benefit.title || benefit.name || '').trim();
  const resolvedId = rawId || `legacy-${index}-${kotlinUnsignedHashBase36(title.toLowerCase())}`;

  const months = cadencePeriodMonths(benefit.cadence);
  const rawPeriodVal = benefit.period_value ?? benefit.periodValue;
  const rawLegacyVal = Number(benefit.value);
  const resolvedPeriodVal = Number.isFinite(Number(rawPeriodVal)) && Number(rawPeriodVal) >= 0
    ? Number(rawPeriodVal)
    : Number.isFinite(rawLegacyVal) && rawLegacyVal >= 0
      ? (months >= 12 ? rawLegacyVal : Math.round((rawLegacyVal / Math.max(1, 12 / months)) * 100) / 100)
      : null;

  const baseUsage = Array.isArray(benefit.usage) ? [...benefit.usage] : [];
  const existingKeys = new Set(baseUsage.map((u) => u && u.period_key).filter(Boolean));
  for (const pk of usedPeriods) {
    if (typeof pk === 'string' && pk.trim() && !existingKeys.has(pk.trim())) {
      baseUsage.push({ period_key: pk.trim(), amount: resolvedPeriodVal ?? 0, used_on: null });
      existingKeys.add(pk.trim());
    }
  }
  if (usedPeriods.length === 0 && usedThisYear && String(benefit.cadence || 'annual').toLowerCase() === 'annual') {
    const yr = String(fallbackYear);
    if (!existingKeys.has(yr)) {
      baseUsage.push({ period_key: yr, amount: resolvedPeriodVal ?? 0, used_on: null });
    }
  }

  return {
    ...benefit,
    id: resolvedId,
    period_value: resolvedPeriodVal,
    usage: baseUsage,
    used_this_year: false,
    used_periods: []
  };
}

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

    const fallbackYear = /^\d{4}/.test(String(periodKey || ''))
      ? String(periodKey).slice(0, 4)
      : new Date().getUTCFullYear();
    benefits = benefits.map((b, idx) => upgradeLegacyBenefit(b, idx, fallbackYear));

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
