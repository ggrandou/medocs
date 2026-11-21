package net.grandou.rappels

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import net.grandou.rappels.reminders.ReminderEngine
import net.grandou.rappels.ui.AppTheme
import net.grandou.rappels.ui.Root
import net.grandou.rappels.ui.isDark

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = Settings(this)
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        val build = "$version (${BuildConfig.GIT_SHA})"
        setContent {
            var theme by remember { mutableStateOf(settings.theme) }
            var persistent by remember { mutableStateOf(settings.persistentReminders) }
            val dark = isDark(theme)
            // System bar icons follow the chosen theme, not only the device's
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            AppTheme(dark) {
                Root(
                    build,
                    theme, { theme = it; settings.theme = it },
                    persistent, {
                        persistent = it
                        settings.persistentReminders = it
                        ReminderEngine.launchSync(this, refreshStyle = true) // notifications already shown change style
                    },
                )
            }
        }
    }

    /** Back in front: catch up (late alarm, notifications cleared, permission just granted in the settings). */
    override fun onResume() {
        super.onResume()
        ReminderEngine.launchSync(this)
    }
}
