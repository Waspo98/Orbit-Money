package app.overbay.orbit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.overbay.orbit.theme.OrbitSprings
import app.overbay.orbit.util.LocalOrbitHaptics

/**
 * Scroll-reactive Material 3 Expressive Floating Action Button.
 * Disappears smoothly when scrolling down and springs back into view immediately when scrolling up.
 */
@Composable
fun OrbitFloatingActionButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Add"
) {
    val haptics = LocalOrbitHaptics.current
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleIn(animationSpec = OrbitSprings.FloatSpatialDefault) +
            fadeIn(animationSpec = OrbitSprings.FloatEffectsFast),
        exit = scaleOut(animationSpec = OrbitSprings.FloatSpatialFast) +
            fadeOut(animationSpec = OrbitSprings.FloatEffectsFast)
    ) {
        FloatingActionButton(
            onClick = {
                haptics.tick()
                onClick()
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 6.dp,
                pressedElevation = 10.dp
            ),
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = contentDescription,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
