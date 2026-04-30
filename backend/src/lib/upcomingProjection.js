export function summarizeHistorySamples(sampleRows = [], fallbackCents = 0) {
  const totalCents = sampleRows.reduce((sum, sample) => sum + (Number(sample.total_cents) || 0), 0);
  const sampleCount = sampleRows.reduce((sum, sample) => sum + (Number(sample.transaction_count) || 0), 0);
  const fallback = Math.max(0, Number(fallbackCents) || 0);

  return {
    totalCents,
    sampleCount,
    averageCents: sampleCount > 0 ? Math.round(totalCents / sampleCount) : fallback
  };
}
