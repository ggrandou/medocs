package net.grandou.medocs.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.grandou.medocs.domain.TypeMedicament
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Dessine les dix icônes de type de médicament, en clair et en sombre (captures pour relecture). */
@OptIn(ExperimentalTestApi::class, ExperimentalLayoutApi::class)
@RunWith(AndroidJUnit4::class)
class IconesMedicamentsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun planche(sombre: Boolean) {
        rule.setContent {
            AppTheme(sombre = sombre) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        for (t in TypeMedicament.entries) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                TuileTypeMedicament(t, 72.dp)
                                Text(t.name.lowercase(), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dossier = File(ctx.getExternalFilesDir(null), "captures").apply { mkdirs() }
        File(dossier, "icones-${if (sombre) "sombre" else "clair"}.png").outputStream().use {
            assertTrue(rule.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test fun plancheClaire() = planche(false)
    @Test fun plancheSombre() = planche(true)
}
