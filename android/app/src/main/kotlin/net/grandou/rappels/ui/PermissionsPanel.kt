package net.grandou.rappels.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import net.grandou.rappels.Permission
import net.grandou.rappels.R

/** Wording and icon of each setting. Shared by the "Autorisations" screen and the future start-up carousel. */
private data class PermissionInfo(val title: String, val why: String, val action: String, @DrawableRes val icon: Int)

private fun Permission.info() = when (this) {
    Permission.NOTIFICATIONS -> PermissionInfo("Notifications", "Sans elles, aucun rappel ne peut s'afficher.", "Activer", R.drawable.ic_perm_notifications)
    Permission.EXACT_ALARMS -> PermissionInfo("Alarmes exactes", "Pour que le rappel sonne à la minute près, même téléphone en veille.", "Autoriser", R.drawable.ic_perm_alarm)
    Permission.BATTERY -> PermissionInfo("Économie d'énergie", "Certains téléphones retardent les rappels. Dans les réglages de l'application, touchez « Batterie » puis choisissez « Sans restriction ».", "Ouvrir les réglages", R.drawable.ic_perm_battery)
}

/**
 * Self-contained block: summary + one row per setting, always up to date (re-read each time the app comes back to the
 * foreground, e.g. after the system settings). No title or scaffold, so it fits in a screen or in a carousel page.
 */
@Composable
fun PermissionsPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    fun open(intent: Intent) {
        try { context.startActivity(intent) } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }
    }

    // Android 13+: system dialog first; once refused for good, it no longer shows, so go to the settings.
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refresh++
        val perm = Permission.notificationsRuntime
        val activity = context.findActivity()
        if (!granted && perm != null && activity != null && !activity.shouldShowRequestPermissionRationale(perm)) {
            open(Permission.NOTIFICATIONS.settingsIntent(context))
        }
    }

    @Suppress("UNUSED_EXPRESSION") refresh // read: recomposes (and re-checks the states) on every change
    val items = Permission.shown.map { it to it.isGranted(context) }
    val todo = items.count { !it.second }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Summary(todo)
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column {
                items.forEachIndexed { i, (p, ok) ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    PermissionRow(p.info(), ok) {
                        val runtime = Permission.notificationsRuntime
                        if (p == Permission.NOTIFICATIONS && runtime != null) notifLauncher.launch(runtime)
                        else open(p.settingsIntent(context))
                    }
                }
            }
        }
    }
}

/** One-line verdict: amber (with an icon and text) when something is left to do, blue when everything is fine. */
@Composable
private fun Summary(todo: Int) {
    val ok = todo == 0
    val container = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
    val content = if (ok) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(container).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(if (ok) R.drawable.ic_check else R.drawable.ic_warning), contentDescription = null, tint = content)
        Text(
            if (ok) "Tout est en ordre : vos rappels sonneront à l'heure."
            else if (todo == 1) "Il reste 1 réglage à faire pour des rappels fiables."
            else "Il reste $todo réglages à faire pour des rappels fiables.",
            color = content, style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun PermissionRow(info: PermissionInfo, granted: Boolean, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            Modifier.size(40.dp).clip(CircleShape)
                .background(if (granted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(info.icon), contentDescription = null, Modifier.size(22.dp),
                tint = if (granted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(info.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(info.why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (granted) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Activé", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            } else {
                FilledTonalButton(onClick = onAction, Modifier.padding(top = 4.dp)) { Text(info.action) }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
