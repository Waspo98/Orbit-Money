package app.overbay.orbit.util

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import java.util.Locale

object FoldableDetector {
    @Volatile
    private var cachedIsFoldable: Boolean? = null

    /**
     * Determines whether the current device is a folding phone / foldable device.
     * Caches the result process-wide and checks zero-IPC build identifiers and lightweight
     * system features before querying SensorManager over Binder IPC.
     */
    fun isFoldableDevice(context: Context): Boolean {
        cachedIsFoldable?.let { return it }
        val detected = detectFoldableHardware(context)
        cachedIsFoldable = detected
        return detected
    }

    private fun detectFoldableHardware(context: Context): Boolean {
        try {
            // 1. Zero-IPC check: known model / product strings for popular foldables
            val model = (Build.MODEL ?: "").uppercase(Locale.ROOT)
            val device = (Build.DEVICE ?: "").uppercase(Locale.ROOT)
            if (model.contains("PIXEL FOLD") ||
                model.contains("PIXEL 9 PRO FOLD") ||
                model.contains("ONEPLUS OPEN") ||
                model.contains("FIND N") ||
                model.contains("RAZR") ||
                model.contains("MIX FOLD") ||
                model.contains("MAGIC V") ||
                model.startsWith("SM-F") || // Samsung Galaxy Z Fold & Z Flip series
                device.contains("FELIX") ||
                device.contains("COMET")
            ) {
                return true
            }

            // 2. Check PackageManager system features
            val pm = context.packageManager
            if (pm != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE)
                ) {
                    return true
                }
                if (pm.hasSystemFeature("com.sec.feature.foldable_dual_screen") ||
                    pm.hasSystemFeature("com.sec.feature.foldable_cover")
                ) {
                    return true
                }
            }

            // 3. Check for hinge angle sensor (targeted check on Android 11+, full scan fallback only on older OS)
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            if (sensorManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (sensorManager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) != null) {
                        return true
                    }
                } else {
                    val allSensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
                    if (allSensors.any { sensor ->
                        sensor.type == 36 ||
                            sensor.stringType.contains("hinge", ignoreCase = true) ||
                            sensor.name.contains("hinge", ignoreCase = true)
                    }) {
                        return true
                    }
                }
            }
        } catch (_: Throwable) {}
        return false
    }
}
