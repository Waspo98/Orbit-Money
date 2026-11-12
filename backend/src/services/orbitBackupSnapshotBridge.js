import { centsToDollars } from '../lib/money.js';

function mapSnapshotBenefit(b, idx) {
  const periodCents = b.periodValueCents ?? null;
  const cadence = String(b.cadence || 'ANNUAL').toLowerCase();
  const periodDollars = periodCents === null ? null : centsToDollars(periodCents);
  const months = cadence === 'monthly' ? 1 : cadence === 'quarterly' ? 3 : cadence === 'semi_annual' ? 6 : cadence === 'every_4_years' ? 48 : 12;
  const annualDollars = periodCents === null ? null : centsToDollars(Math.round((periodCents * 12) / months));
  const out = {
    id: b.id || `snap_${idx}`,
    name: b.title || '',
    title: b.title || '',
    value: annualDollars,
    period_value: periodDollars,
    description: b.description || null,
    category: b.category || null,
    cadence,
    anchor: String(b.anchor || 'CALENDAR').toLowerCase(),
    usage: Array.isArray(b.usage)
      ? b.usage.map((u) => ({
          period_key: u.periodKey,
          amount: centsToDollars(u.amountCents || 0),
          used_on: u.usedOn || null
        }))
      : [],
    used_this_year: Boolean(b.usedThisYear),
    used_periods: Array.isArray(b.usedPeriods) ? b.usedPeriods : []
  };
  if (b.valueText) out.value_text = b.valueText;
  return out;
}

function mapSnapshotProfile(p, fallbackId) {
  const strat = p.strategy || {};
  return {
    id: p.id || fallbackId,
    account_id: p.accountId,
    card_name: p.cardName || null,
    issuer_name: p.issuerName || null,
    network: p.network || null,
    image_url: p.imageUrl || null,
    artwork_url: p.artworkUrl || null,
    annual_fee: p.annualFeeCents ?? null,
    annual_fee_post_date: p.annualFeePostDate || null,
    credit_limit: p.creditLimitCents ?? null,
    statement_closing_day: p.statementClosingDay ?? null,
    payment_due_day: p.paymentDueDay ?? null,
    apr_bps: p.aprBps ?? null,
    promo_apr_bps: p.promoAprBps ?? null,
    promo_end_date: p.promoEndDate || null,
    authorized_users_json: JSON.stringify(Array.isArray(p.authorizedUsers) ? p.authorizedUsers : []),
    reward_categories_json: JSON.stringify(
      Array.isArray(p.rewardCategories)
        ? p.rewardCategories.map((r) => ({
            label: r.label || r.category || '',
            category: r.category || null,
            rate: r.multiplier ?? 1.0,
            is_rotating: Boolean(r.isRotating),
            cap: r.capCents != null ? centsToDollars(r.capCents) : null,
            type: r.rateType || 'multiplier',
            notes: r.notes || r.description || null
          }))
        : []
    ),
    benefits_json: JSON.stringify(
      Array.isArray(p.benefits) ? p.benefits.map(mapSnapshotBenefit) : []
    ),
    strategy_json: JSON.stringify({
      cardholder_member_id: strat.cardholderMemberId ?? null,
      authorized_user_member_ids: Array.isArray(strat.authorizedUserMemberIds) ? strat.authorizedUserMemberIds : [],
      cardholder: strat.cardholder || null,
      opened_date: strat.openedDate || null,
      closed_date: strat.closedDate || null,
      is_business_card: Boolean(strat.isBusinessCard),
      counts_toward_524_override: strat.countsToward524Override ?? null,
      product_changed_from: strat.productChangedFrom || null,
      sub_reward_text: strat.subRewardText || null,
      sub_estimated_value: strat.subEstimatedValueCents != null ? centsToDollars(strat.subEstimatedValueCents) : null,
      sub_target_spend: strat.subTargetSpendCents != null ? centsToDollars(strat.subTargetSpendCents) : null,
      sub_deadline_date: strat.subDeadlineDate || null,
      sub_manual_spend_offset: centsToDollars(strat.subManualSpendOffsetCents || 0),
      sub_completed: Boolean(strat.subCompleted),
      sub_received_date: strat.subReceivedDate || null,
      sub_cooldown_months: strat.subCooldownMonths ?? null
    }),
    notes: p.notes || null
  };
}

function mapSnapshotRecurrenceRule(rule, isGiving) {
  let obj = null;
  if (rule && typeof rule === 'object') {
    const rawType = String(rule.type || '');
    if (Array.isArray(rule.days) || rawType.includes('MonthDays') || rawType === 'month_days') {
      obj = { type: 'month_days', days: rule.days || [] };
    } else if (Array.isArray(rule.ordinals) || rawType.includes('MonthWeekdays') || rawType === 'month_weekdays') {
      obj = { type: 'month_weekdays', ordinals: rule.ordinals || [], weekdays: rule.weekdays || [] };
    }
  }
  if (!obj && !isGiving) return null;
  if (obj && isGiving) return JSON.stringify({ ...obj, kind_override: 'giving' });
  if (isGiving) return JSON.stringify({ kind_override: 'giving' });
  return JSON.stringify(obj);
}

export function isOrbitWorkspaceSnapshot(parsed) {
  return Boolean(
    parsed &&
    typeof parsed === 'object' &&
    typeof parsed.schemaVersion === 'number' &&
    (Array.isArray(parsed.accounts) || Array.isArray(parsed.transactions) || Array.isArray(parsed.categories))
  );
}

export function convertWorkspaceSnapshotToOrbitBackup(snap) {
  const accounts = (snap.accounts || []).map((a, idx) => ({
    id: a.id || idx + 1,
    name: a.name || 'Account',
    type: String(a.type || 'OTHER').toLowerCase(),
    institution: a.institution || null,
    account_number_last4: a.accountNumberLast4 || null,
    current_balance: a.currentBalanceCents ?? a.balanceCents ?? 0,
    estimated_value: a.estimatedValueCents ?? null,
    is_property: a.isProperty ? 1 : 0,
    property_name: a.propertyName || null,
    linked_property_account_id: a.linkedPropertyAccountId ?? null,
    closure_prompt_dismissed_at: a.closurePromptDismissedAt || null,
    is_manual: a.isManual === false ? 0 : 1,
    is_archived: a.isArchived ? 1 : 0,
    sort_order: a.sortOrder || 0,
    mha_default_eligible: a.mhaDefaultEligible ? 1 : 0
  }));

  const rawProfiles = Array.isArray(snap.creditCardProfiles) && snap.creditCardProfiles.length > 0
    ? snap.creditCardProfiles
    : (snap.accounts || []).map((a) => a.creditCardProfile).filter(Boolean);
  const creditCardProfiles = rawProfiles.map((p, idx) => mapSnapshotProfile(p, idx + 1));

  const categories = (snap.categories || []).map((c, idx) => ({
    id: c.id || idx + 1,
    name: c.name || 'Category',
    color: c.color || '#888888',
    icon: c.icon || null,
    is_transfer: c.isTransfer ? 1 : 0,
    is_income: c.isIncome ? 1 : 0,
    sort_order: c.sortOrder || 0,
    mha_default_eligible: c.mhaDefaultEligible ? 1 : 0,
    mha_default_ignored: c.mhaDefaultIgnored ? 1 : 0
  }));

  const transactions = (snap.transactions || []).map((t, idx) => ({
    id: t.id || idx + 1,
    account_id: t.accountId,
    date: t.editedDate || t.originalDate,
    original_merchant: t.originalMerchant || '',
    original_description: t.originalDescription || null,
    amount: t.editedAmountCents ?? t.originalAmountCents ?? 0,
    category_id: t.originalCategoryId ?? null,
    is_transfer: t.isTransfer ? 1 : 0,
    transfer_pair_id: t.transferPairId ?? null,
    is_ignored: t.isIgnored ? 1 : 0,
    source: String(t.source || 'manual').toLowerCase(),
    is_pending: t.isPending ? 1 : 0,
    notes: t.notes || null,
    edited_merchant: t.editedMerchant ?? null,
    edited_merchant_source: t.editedMerchantSource ?? null,
    edited_category_id: t.editedCategoryId ?? null,
    edited_category_id_source: t.editedCategoryIdSource ?? null,
    edited_is_transfer: t.editedIsTransfer == null ? null : (t.editedIsTransfer ? 1 : 0),
    edited_is_transfer_source: t.editedIsTransferSource ?? null,
    edited_is_ignored: t.editedIsIgnored == null ? null : (t.editedIsIgnored ? 1 : 0),
    edited_is_ignored_source: t.editedIsIgnoredSource ?? null,
    edited_mha_eligible: t.editedMhaEligible == null ? null : (t.editedMhaEligible ? 1 : 0),
    edited_mha_eligible_source: t.editedMhaEligibleSource ?? null
  }));

  const budgets = (snap.budgets || []).map((b, idx) => ({
    id: b.id || idx + 1,
    category_id: b.categoryId,
    amount: b.amountCents || 0,
    rollover: b.rollover ? 1 : 0
  }));

  const goals = [];
  const goalAllocations = [];
  (snap.goals || []).forEach((g, idx) => {
    const goalId = g.id || idx + 1;
    goals.push({
      id: goalId,
      name: g.name || 'Goal',
      target_amount: g.targetAmountCents || 0,
      current_amount: g.currentAmountCents || 0,
      target_date: g.targetDate || null,
      linked_account_id: g.linkedAccountId ?? null,
      kind: g.kind || 'custom',
      icon: g.icon || null,
      sort_order: g.sortOrder || 0,
      notes: g.notes || null
    });
    (g.allocations || []).forEach((al, aIdx) => {
      goalAllocations.push({
        id: al.id || (goalAllocations.length + aIdx + 1),
        goal_id: goalId,
        account_id: al.accountId,
        allocation_type: String(al.allocationType || 'FIXED').toLowerCase(),
        allocation_percent: al.allocationPercent ?? 0,
        allocation_amount: al.allocationAmountCents || 0,
        reserve_amount: al.reserveAmountCents || 0
      });
    });
  });

  const rules = (snap.rules || []).map((r, idx) => ({
    id: r.id || idx + 1,
    name: r.name || 'Rule',
    priority: r.priority || 0,
    enabled: r.enabled === false ? 0 : 1,
    conditions: JSON.stringify(
      (r.conditions || []).map((c) => ({
        field: String(c.field || 'MERCHANT').toLowerCase(),
        operator: String(c.operator || 'CONTAINS').toLowerCase(),
        value: c.targetId ?? c.value ?? ''
      }))
    ),
    actions: JSON.stringify(
      (r.actions || []).map((act) => {
        const rawType = String(act.type || 'CATEGORIZE').toLowerCase();
        const type = rawType === 'set_category' ? 'categorize' : rawType === 'rename_merchant' ? 'rename' : rawType;
        return {
          type,
          value: act.targetCategoryId ?? act.textValue ?? act.booleanValue ?? act.value ?? ''
        };
      })
    )
  }));

  const upcomingItems = (snap.upcomingItems || []).map((u, idx) => {
    const rawKind = String(u.kind || 'BILL').toLowerCase();
    const isGiving = rawKind === 'giving';
    const rawFreq = String(u.frequencyType || 'MONTHLY').toLowerCase();
    const isQuarterly = rawFreq === 'quarterly';
    return {
      id: u.id || idx + 1,
      name: u.name || u.merchant || 'Recurring',
      kind: isGiving ? 'bill' : (['bill', 'subscription', 'income'].includes(rawKind) ? rawKind : 'bill'),
      source: ['manual', 'transaction', 'suggestion'].includes(String(u.source || '').toLowerCase())
        ? String(u.source).toLowerCase()
        : 'manual',
      merchant: u.merchant || u.name || null,
      amount: u.amountCents || 0,
      direction: u.direction === 'income' ? 'income' : 'expense',
      frequency_type: isQuarterly ? 'custom' : rawFreq,
      frequency_interval: isQuarterly ? 3 : (u.frequencyInterval || 1),
      frequency_unit: isQuarterly ? 'months' : (u.frequencyUnit || 'months'),
      recurrence_rule: mapSnapshotRecurrenceRule(u.recurrenceRule, isGiving),
      amount_strategy: String(u.amountStrategy || 'FIXED').toLowerCase(),
      amount_lookback_months: u.amountLookbackMonths || 6,
      next_date: u.nextDate,
      category_id: u.categoryId ?? null,
      account_id: u.accountId ?? null,
      source_transaction_id: u.sourceTransactionId ?? null,
      status: String(u.status || 'ACTIVE').toLowerCase() === 'paused' ? 'paused' : 'active',
      notes: u.notes || null
    };
  });

  const members = (snap.members || []).map((m, idx) => ({
    id: m.id || idx + 1,
    name: m.name || 'Member',
    role: m.role || 'adult',
    birth_date: m.birthDate || null,
    employment_status: m.employmentStatus || 'employed',
    employer: m.employer || null,
    job_title: m.jobTitle || null,
    gross_income_annual: m.grossIncomeAnnualCents || 0,
    net_pay_per_period: m.netPayPerPeriodCents || 0,
    pay_frequency: m.payFrequency || 'biweekly',
    pay_periods_per_year: m.payPeriodsPerYear ?? 26,
    retirement_account_type: m.retirementAccountType || null,
    employee_contribution_percent: m.employeeContributionPercent ?? 0,
    employee_contribution_annual: m.employeeContributionAnnualCents || 0,
    employer_match_percent: m.employerMatchPercent ?? 0,
    employer_match_limit_percent: m.employerMatchLimitPercent ?? 0,
    employer_match_annual_cap: m.employerMatchAnnualCapCents || 0,
    health_premium_per_month: m.healthPremiumPerMonthCents || 0,
    hsa_contribution_annual: m.hsaContributionAnnualCents || 0,
    dependent_care_fsa_annual: m.dependentCareFsaAnnualCents || 0,
    other_benefits_annual: m.otherBenefitsAnnualCents || 0,
    notes: m.notes || null
  }));

  const incomeRecords = (snap.incomeRecords || []).map((ir, idx) => ({
    id: ir.id || idx + 1,
    member_id: ir.memberId,
    effective_date: ir.effectiveDate,
    gross_income_annual: ir.grossIncomeAnnualCents || 0,
    net_pay_per_period: ir.netPayPerPeriodCents || 0,
    pay_frequency: ir.payFrequency || 'biweekly',
    pay_periods_per_year: ir.payPeriodsPerYear ?? 26,
    employee_contribution_percent: ir.employeeContributionPercent ?? 0,
    employee_contribution_annual: ir.employeeContributionAnnualCents || 0,
    employer_match_percent: ir.employerMatchPercent ?? 0,
    employer_match_limit_percent: ir.employerMatchLimitPercent ?? 0,
    employer_match_annual_cap: ir.employerMatchAnnualCapCents || 0,
    health_premium_per_month: ir.healthPremiumPerMonthCents || 0,
    hsa_contribution_annual: ir.hsaContributionAnnualCents || 0,
    dependent_care_fsa_annual: ir.dependentCareFsaAnnualCents || 0,
    other_benefits_annual: ir.otherBenefitsAnnualCents || 0,
    source: ir.source || 'manual',
    notes: ir.notes || null
  }));

  const retirementLinks = (snap.retirementAccountLinks || []).map((rl, idx) => ({
    id: rl.id || idx + 1,
    member_id: rl.memberId,
    account_id: rl.accountId,
    account_kind: rl.accountKind || 'other'
  }));

  const balanceRecords = (snap.balanceRecords || []).map((br, idx) => ({
    id: br.id || idx + 1,
    account_id: br.accountId,
    record_date: br.recordDate,
    balance: br.balanceCents || 0,
    source: String(br.source || 'MANUAL').toLowerCase()
  }));

  return {
    type: 'orbit_money_backup',
    version: 1,
    exportedAt: snap.exportedAt || new Date().toISOString(),
    household: {
      name: snap.household?.name || 'Orbit Household',
      default_currency: snap.household?.defaultCurrency || 'USD'
    },
    memberships: [],
    tables: {
      accounts,
      credit_card_profiles: creditCardProfiles,
      categories,
      transactions,
      rules,
      budgets,
      goals,
      goal_account_allocations: goalAllocations,
      account_balance_records: balanceRecords,
      household_members: members,
      household_income_records: incomeRecords,
      household_retirement_accounts: retirementLinks,
      upcoming_items: upcomingItems
    }
  };
}
