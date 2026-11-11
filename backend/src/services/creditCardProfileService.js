import crypto from 'crypto';
import { isValidDateOnly } from '../lib/localDate.js';
import { dollarsToCents, moneyFieldsToDollars } from '../lib/money.js';
import { parseId, parseInteger } from '../lib/routeParams.js';

export const BENEFIT_PERIOD_KEY_REGEX = /^(\d{4}(-\d{2}|-Q[1-4]|-H[12])?|CM-\d{4}-\d{2}-\d{2})$/;

const CREDIT_CARD_PROFILE_MONEY_FIELDS = ['annual_fee', 'credit_limit'];

function safeJsonParse(value, fallback) {
  if (typeof value !== 'string') return fallback;
  try {
    return JSON.parse(value);
  } catch {
    return fallback;
  }
}

export function serializeCreditCardProfile(row) {
  if (!row) return null;
  const profile = moneyFieldsToDollars(row, CREDIT_CARD_PROFILE_MONEY_FIELDS);
  profile.authorized_users = safeJsonParse(profile.authorized_users_json, []);
  profile.reward_categories = safeJsonParse(profile.reward_categories_json, []);
  profile.benefits = safeJsonParse(profile.benefits_json, []);
  profile.strategy = safeJsonParse(profile.strategy_json, {});
  delete profile.authorized_users_json;
  delete profile.reward_categories_json;
  delete profile.benefits_json;
  delete profile.strategy_json;
  return profile;
}

export function attachCreditCardProfiles(db, householdId, accounts) {
  if (accounts.length === 0) return accounts;
  const profiles = db
    .prepare('SELECT * FROM credit_card_profiles WHERE household_id = ?')
    .all(householdId)
    .map(serializeCreditCardProfile);
  const profileByAccountId = new Map(
    profiles.map((profile) => [profile.account_id, profile])
  );
  return accounts.map((account) => ({
    ...account,
    credit_card_profile: profileByAccountId.get(account.id) || null
  }));
}

function normalizeOptionalText(value) {
  const text = typeof value === 'string' ? value.trim() : '';
  return text || null;
}

function normalizeOptionalTextLength(value, maxLength = 500) {
  const text = normalizeOptionalText(value);
  return text ? text.slice(0, maxLength) : null;
}

function normalizeOptionalDateOnly(value, fieldName = 'annual_fee_post_date') {
  const text = normalizeOptionalText(value);
  if (!text) return null;
  if (!isValidDateOnly(text)) {
    throw new Error(`${fieldName} must be a valid YYYY-MM-DD date.`);
  }
  return text;
}

function normalizeOptionalCycleDay(value, fieldName) {
  if (value === null || value === undefined || value === '') return null;
  const day = parseInteger(value);
  if (day === null || day < 1 || day > 31) {
    throw new Error(`${fieldName} must be an integer between 1 and 31.`);
  }
  return day;
}

function normalizeOptionalBps(value, fieldName) {
  if (value === null || value === undefined || value === '') return null;
  const bps = parseInteger(value);
  if (bps === null || bps < 0 || bps > 10000) {
    throw new Error(`${fieldName} must be an integer between 0 and 10000.`);
  }
  return bps;
}

function normalizeOptionalMoney(value, fieldName) {
  if (value === null || value === undefined || value === '') return null;
  const number = Number(value);
  if (!Number.isFinite(number) || number < 0) {
    throw new Error(`${fieldName} must be a non-negative number.`);
  }
  return dollarsToCents(number);
}

function finiteNonNegOrNull(value) {
  if (value === null || value === undefined || value === '') return null;
  const n = Number(value);
  return Number.isFinite(n) && n >= 0 ? n : null;
}

function normalizeBenefitUsage(value) {
  if (!Array.isArray(value)) return [];
  return value
    .map((item) => {
      if (!item || typeof item !== 'object') return null;
      const key = String(item.period_key || item.periodKey || '').trim();
      if (!key || key.length > 24 || !BENEFIT_PERIOD_KEY_REGEX.test(key)) return null;
      const amt = Number(item.amount);
      if (!Number.isFinite(amt) || amt < 0) return null;
      let usedOn = null;
      const rawDate = item.used_on ?? item.usedOn;
      if (typeof rawDate === 'string' && isValidDateOnly(rawDate.trim())) {
        usedOn = rawDate.trim();
      }
      return {
        period_key: key,
        amount: Math.round(amt * 100) / 100,
        used_on: usedOn
      };
    })
    .filter(Boolean)
    .slice(-120);
}

function normalizeTextList(value, maxItems = 12, maxLength = 80, keep = 'first') {
  if (!Array.isArray(value)) return [];
  const items = value
    .map((item) => String(item || '').trim())
    .filter(Boolean);
  const sliced = keep === 'last'
    ? (maxItems > 0 ? items.slice(-maxItems) : [])
    : items.slice(0, maxItems);
  return sliced.map((item) => item.slice(0, maxLength));
}

function normalizeRewardCategories(value) {
  if (!Array.isArray(value)) return [];
  return value
    .map((item) => {
      if (typeof item === 'string') {
        const label = item.trim();
        return label ? { label: label.slice(0, 80) } : null;
      }
      if (!item || typeof item !== 'object') return null;
      const label = String(item.label || item.category || item.name || '').trim();
      if (!label) return null;
      const rate = item.rate === null || item.rate === undefined || item.rate === ''
        ? null
        : Number(item.rate);
      const cap = item.cap === null || item.cap === undefined || item.cap === ''
        ? null
        : Number(item.cap);
      return {
        label: label.slice(0, 80),
        category: normalizeOptionalTextLength(item.category, 80),
        rate: Number.isFinite(rate) ? rate : null,
        is_rotating: Boolean(item.is_rotating ?? item.isRotating ?? false),
        cap: Number.isFinite(cap) && cap >= 0 ? cap : null,
        type: normalizeOptionalTextLength(item.type, 40),
        notes: normalizeOptionalTextLength(item.notes || item.note, 180)
      };
    })
    .filter(Boolean)
    .slice(0, 16);
}

function normalizeBenefits(value) {
  if (!Array.isArray(value)) return [];
  return value
    .map((item) => {
      if (typeof item === 'string') {
        const name = item.trim();
        return name ? {
          id: `b_${crypto.randomUUID().slice(0, 12)}`,
          name: name.slice(0, 120),
          title: name.slice(0, 120),
          value: null,
          period_value: null,
          description: null,
          category: null,
          cadence: 'annual',
          anchor: 'calendar',
          usage: [],
          used_this_year: false,
          used_periods: []
        } : null;
      }
      if (!item || typeof item !== 'object') return null;
      const name = String(item.name || item.title || item.label || '').trim();
      if (!name) return null;
      const trimmedName = name.slice(0, 120);

      const rawNumber = item.value === null || item.value === undefined || item.value === ''
        ? null
        : Number(item.value);
      const isNumeric = Number.isFinite(rawNumber) && rawNumber >= 0;
      const numericValue = isNumeric ? rawNumber : null;
      const valueText = !isNumeric && typeof item.value === 'string'
        ? normalizeOptionalTextLength(item.value, 120)
        : normalizeOptionalTextLength(item.value_text || item.valueText, 120);

      const benefit = {
        id: (typeof item.id === 'string' && /^[A-Za-z0-9_-]{1,64}$/.test(item.id)) ? item.id : `b_${crypto.randomUUID().slice(0, 12)}`,
        name: trimmedName,
        title: trimmedName,
        value: numericValue,
        period_value: finiteNonNegOrNull(item.period_value ?? item.periodValue),
        description: normalizeOptionalTextLength(item.description || item.notes, 300),
        category: normalizeOptionalTextLength(item.category, 80),
        cadence: normalizeOptionalTextLength(item.cadence, 32) || 'annual',
        anchor: (item.anchor === 'cardmember_year') ? 'cardmember_year' : 'calendar',
        usage: normalizeBenefitUsage(item.usage),
        used_this_year: Boolean(item.used_this_year ?? item.usedThisYear ?? false),
        used_periods: normalizeTextList(item.used_periods || item.usedPeriods, 120, 24, 'last')
      };
      if (valueText) {
        benefit.value_text = valueText;
      }
      return benefit;
    })
    .filter(Boolean)
    .slice(0, 24);
}

function normalizeStrategy(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return {};
  const cardholderMemberIdRaw = value.cardholder_member_id ?? value.cardholderMemberId;
  const cardholderMemberId = cardholderMemberIdRaw === null || cardholderMemberIdRaw === undefined || cardholderMemberIdRaw === ''
    ? null
    : parseId(cardholderMemberIdRaw);
  const auRaw = value.authorized_user_member_ids ?? value.authorizedUserMemberIds;
  const authorizedUserMemberIds = Array.isArray(auRaw)
    ? Array.from(new Set(auRaw.map(parseId).filter((id) => id !== null && id > 0))).slice(0, 12)
    : [];

  const estVal = value.sub_estimated_value === null || value.sub_estimated_value === undefined || value.sub_estimated_value === ''
    ? null
    : Number(value.sub_estimated_value);
  const targetSpend = value.sub_target_spend === null || value.sub_target_spend === undefined || value.sub_target_spend === ''
    ? null
    : Number(value.sub_target_spend);
  const offsetSpend = Number(value.sub_manual_spend_offset ?? 0);
  const cooldownRaw = value.sub_cooldown_months;
  const cooldown = cooldownRaw === null || cooldownRaw === undefined || cooldownRaw === ''
    ? null
    : parseInteger(cooldownRaw);
  const overrideRaw = value.counts_toward_524_override ?? value.countsToward524Override;

  return {
    cardholder_member_id: cardholderMemberId,
    authorized_user_member_ids: authorizedUserMemberIds,
    cardholder: normalizeOptionalTextLength(value.cardholder, 60),
    opened_date: normalizeOptionalDateOnly(value.opened_date ?? value.openedDate, 'opened_date'),
    closed_date: normalizeOptionalDateOnly(value.closed_date ?? value.closedDate, 'closed_date'),
    is_business_card: Boolean(value.is_business_card ?? value.isBusinessCard ?? false),
    counts_toward_524_override: overrideRaw === null || overrideRaw === undefined ? null : Boolean(overrideRaw),
    product_changed_from: normalizeOptionalTextLength(value.product_changed_from ?? value.productChangedFrom, 120),
    sub_reward_text: normalizeOptionalTextLength(value.sub_reward_text ?? value.subRewardText, 140),
    sub_estimated_value: Number.isFinite(estVal) && estVal >= 0 ? estVal : null,
    sub_target_spend: Number.isFinite(targetSpend) && targetSpend >= 0 ? targetSpend : null,
    sub_deadline_date: normalizeOptionalDateOnly(value.sub_deadline_date ?? value.subDeadlineDate, 'sub_deadline_date'),
    sub_manual_spend_offset: Number.isFinite(offsetSpend) ? offsetSpend : 0,
    sub_completed: Boolean(value.sub_completed ?? value.subCompleted ?? false),
    sub_received_date: normalizeOptionalDateOnly(value.sub_received_date ?? value.subReceivedDate, 'sub_received_date'),
    sub_cooldown_months: cooldown !== null && cooldown >= 0 && cooldown <= 1200 ? cooldown : null
  };
}

function stringifyProfileJson(value, fieldName) {
  const text = JSON.stringify(value);
  if (Buffer.byteLength(text, 'utf8') > 20000) {
    throw new Error(`${fieldName} is too large.`);
  }
  return text;
}

export function normalizeCreditCardProfileBody(body = {}) {
  const authorizedUsers = normalizeTextList(body.authorized_users);
  const rewardCategories = normalizeRewardCategories(body.reward_categories);
  const benefits = normalizeBenefits(body.benefits);
  const strategy = normalizeStrategy(body.strategy);
  const accountName = typeof body.account_name === 'string' ? body.account_name.trim() : null;

  if (!accountName) {
    throw new Error('account_name cannot be empty.');
  }

  return {
    account_name: accountName,
    account_institution: normalizeOptionalTextLength(body.account_institution, 180),
    card_name: normalizeOptionalTextLength(body.card_name, 220),
    issuer_name: normalizeOptionalTextLength(body.issuer_name, 180),
    network: normalizeOptionalTextLength(body.network, 40),
    image_url: normalizeOptionalTextLength(body.image_url, 1000),
    artwork_url: normalizeOptionalTextLength(body.artwork_url ?? body.artworkUrl, 1000),
    annual_fee: normalizeOptionalMoney(body.annual_fee, 'annual_fee'),
    annual_fee_post_date: normalizeOptionalDateOnly(body.annual_fee_post_date, 'annual_fee_post_date'),
    credit_limit: normalizeOptionalMoney(body.credit_limit, 'credit_limit'),
    statement_closing_day: normalizeOptionalCycleDay(body.statement_closing_day, 'statement_closing_day'),
    payment_due_day: normalizeOptionalCycleDay(body.payment_due_day, 'payment_due_day'),
    apr_bps: normalizeOptionalBps(body.apr_bps ?? body.aprBps, 'apr_bps'),
    promo_apr_bps: normalizeOptionalBps(body.promo_apr_bps ?? body.promoAprBps, 'promo_apr_bps'),
    promo_end_date: normalizeOptionalDateOnly(body.promo_end_date ?? body.promoEndDate, 'promo_end_date'),
    authorized_users_json: stringifyProfileJson(authorizedUsers, 'authorized_users'),
    reward_categories_json: stringifyProfileJson(rewardCategories, 'reward_categories'),
    benefits_json: stringifyProfileJson(benefits, 'benefits'),
    strategy_json: stringifyProfileJson(strategy, 'strategy'),
    notes: normalizeOptionalTextLength(body.notes, 2000)
  };
}

export function upsertCreditCardProfile(db, { householdId, accountId, profile }) {
  db.transaction(() => {
    db.prepare(
      `UPDATE accounts
          SET name = ?,
              institution = ?,
              updated_at = datetime('now')
        WHERE id = ?
          AND household_id = ?`
    ).run(profile.account_name, profile.account_institution, accountId, householdId);

    db.prepare(
      `INSERT INTO credit_card_profiles (
         household_id, account_id, card_name, issuer_name, network, image_url, artwork_url,
         annual_fee, annual_fee_post_date, credit_limit,
         statement_closing_day, payment_due_day,
         apr_bps, promo_apr_bps, promo_end_date,
         authorized_users_json, reward_categories_json, benefits_json, strategy_json, notes
       ) VALUES (
         ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
       )
       ON CONFLICT(household_id, account_id) DO UPDATE SET
         card_name = excluded.card_name,
         issuer_name = excluded.issuer_name,
         network = excluded.network,
         image_url = excluded.image_url,
         artwork_url = excluded.artwork_url,
         annual_fee = excluded.annual_fee,
         annual_fee_post_date = excluded.annual_fee_post_date,
         credit_limit = excluded.credit_limit,
         statement_closing_day = excluded.statement_closing_day,
         payment_due_day = excluded.payment_due_day,
         apr_bps = excluded.apr_bps,
         promo_apr_bps = excluded.promo_apr_bps,
         promo_end_date = excluded.promo_end_date,
         authorized_users_json = excluded.authorized_users_json,
         reward_categories_json = excluded.reward_categories_json,
         benefits_json = excluded.benefits_json,
         strategy_json = excluded.strategy_json,
         notes = excluded.notes,
         updated_at = datetime('now')`
    ).run(
      householdId,
      accountId,
      profile.card_name,
      profile.issuer_name,
      profile.network,
      profile.image_url,
      profile.artwork_url,
      profile.annual_fee,
      profile.annual_fee_post_date,
      profile.credit_limit,
      profile.statement_closing_day,
      profile.payment_due_day,
      profile.apr_bps,
      profile.promo_apr_bps,
      profile.promo_end_date,
      profile.authorized_users_json,
      profile.reward_categories_json,
      profile.benefits_json,
      profile.strategy_json,
      profile.notes
    );
  })();
}
