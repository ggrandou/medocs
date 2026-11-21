package net.grandou.rappels.ui

import androidx.compose.runtime.Composable

/** "Autorisations" screen opened from "Plus": same panel as the start-up carousel will use. */
@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    SubScreen("Autorisations", onBack) { PermissionsPanel() }
}
