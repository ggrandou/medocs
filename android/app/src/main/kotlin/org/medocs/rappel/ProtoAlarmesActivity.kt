package org.medocs.rappel

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/** Prototype : programme des alarmes et affiche le journal de leur déclenchement réel. */
class ProtoAlarmesActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var logView: TextView
    private lateinit var modeSwitch: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
        root.addView(TextView(this).apply { text = "Rappel médicaments (proto)"; textSize = 20f })
        fun button(label: String, action: () -> Unit) = Button(this).apply {
            text = label; setOnClickListener { action(); refresh() }
            root.addView(this, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        status = TextView(this).also { root.addView(it) }
        modeSwitch = Switch(this).apply { text = "Mode setAlarmClock (sinon setExactAndAllowWhileIdle)" }
        root.addView(modeSwitch)

        button("Autoriser les notifications") { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1) }
        button("Autoriser les alarmes exactes") {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        }
        for (min in listOf(1, 5, 30, 120)) {
            button("Alarme dans $min min") {
                Scheduler.create(this, min * 60_000L, if (modeSwitch.isChecked) Mode.ALARM_CLOCK else Mode.EXACT)
            }
        }
        button("Effacer le journal") { Store.clearLog(this) }
        button("Rafraîchir") {}

        logView = TextView(this).apply { typeface = android.graphics.Typeface.MONOSPACE; textSize = 11f }
        root.addView(logView)
        // Android 15+ : l'application s'affiche sous les barres système, on décale le contenu.
        val scroll = ScrollView(this).apply { addView(root) }
        @Suppress("DEPRECATION")
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            v.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
            insets
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val pm = getSystemService(PowerManager::class.java)
        val pending = Store.pending(this)
        status.text = buildString {
            appendLine("Alarmes exactes autorisées : ${Scheduler.canExact(this@ProtoAlarmesActivity)}")
            appendLine("Exclue de l'optimisation batterie : ${pm.isIgnoringBatteryOptimizations(packageName)}")
            appendLine("En attente : ${pending.size}" + pending.joinToString("") { "\n  #${it.id} ${it.mode} ${Store.time(it.triggerAt)}" })
        }
        logView.text = Store.logText(this)
    }
}
