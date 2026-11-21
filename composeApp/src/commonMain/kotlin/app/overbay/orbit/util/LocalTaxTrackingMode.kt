package app.overbay.orbit.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import app.overbay.orbit.data.model.TaxTrackingLabels
import app.overbay.orbit.data.model.TaxTrackingMode

val LocalTaxTrackingMode = compositionLocalOf { TaxTrackingMode.MHA }

@Composable
fun currentTaxTrackingLabels(): TaxTrackingLabels =
    TaxTrackingLabels.forMode(LocalTaxTrackingMode.current)
