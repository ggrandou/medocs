package org.medocs.rappel.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.medocs.rappel.photos.Photos
import java.io.File

/** Miniature carrée de la photo d'une boîte ; n'occupe aucune place s'il n'y a pas de photo. */
@Composable
fun Vignette(nom: String?, cote: Dp, modifier: Modifier = Modifier) {
    if (nom == null) return
    val contexte = LocalContext.current
    val densite = LocalContext.current.resources.displayMetrics.density
    val image by produceState<ImageBitmap?>(null, nom, cote) {
        value = withContext(Dispatchers.IO) { Photos.charger(contexte, nom, (cote.value * densite).toInt())?.asImageBitmap() }
    }
    image?.let {
        Image(
            it, contentDescription = "Photo de la boîte", contentScale = ContentScale.Crop,
            modifier = modifier.size(cote).clip(RoundedCornerShape(8.dp)),
        )
    }
}

/** Aperçu + boutons pour prendre, choisir ou retirer la photo d'une boîte. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChampPhoto(nom: String?, onChange: (String?) -> Unit) {
    val contexte = LocalContext.current
    val portee = rememberCoroutineScope()
    var erreur by remember { mutableStateOf<String?>(null) }
    val fichierCapture = remember { File(contexte.cacheDir, "captures/capture.jpg").apply { parentFile?.mkdirs() } }
    val uriCapture = remember { FileProvider.getUriForFile(contexte, "${contexte.packageName}.fichiers", fichierCapture) }

    fun traiter(uri: Uri) {
        portee.launch {
            val enregistree = withContext(Dispatchers.IO) { Photos.enregistrer(contexte, uri) }
            if (enregistree != null) { erreur = null; onChange(enregistree) } else erreur = "Image illisible."
            fichierCapture.delete()
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) traiter(uriCapture) }
    val galerie = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) traiter(uri) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Photo de la boîte (facultatif)", style = MaterialTheme.typography.titleSmall)
        Vignette(nom, 120.dp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                try { camera.launch(uriCapture) } catch (e: ActivityNotFoundException) { erreur = "Aucune application photo disponible." }
            }) { Text("Prendre une photo") }
            OutlinedButton(onClick = { galerie.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Galerie") }
            if (nom != null) TextButton(onClick = { onChange(null) }) { Text("Retirer") }
        }
        Text(
            "La photo reste sur ce téléphone, réduite et sans position GPS.",
            style = MaterialTheme.typography.bodySmall,
        )
        erreur?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}
