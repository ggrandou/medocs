package net.grandou.medocs.ui

import android.content.res.Configuration
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.grandou.medocs.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Le choix clair/sombre de l'onglet Plus force le mode nuit de l'activité, quel que soit le réglage de l'appareil. */
@RunWith(AndroidJUnit4::class)
class ModeApparenceTest {
    private val prefs get() = ApplicationProvider.getApplicationContext<android.content.Context>().getSharedPreferences("ui", android.content.Context.MODE_PRIVATE)

    @After fun nettoyer() { prefs.edit().remove("theme").apply() }

    private fun nuitAvec(theme: String): Int {
        prefs.edit().putString("theme", theme).commit()
        var nuit = -1
        ActivityScenario.launch(MainActivity::class.java).use { it.onActivity { a -> nuit = a.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK } }
        return nuit
    }

    @Test fun sombreForceLeModeNuit() = assertEquals(Configuration.UI_MODE_NIGHT_YES, nuitAvec("sombre"))
    @Test fun clairForceLeModeJour() = assertEquals(Configuration.UI_MODE_NIGHT_NO, nuitAvec("clair"))
}
