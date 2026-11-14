package net.grandou.medocs.ui

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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.grandou.medocs.photos.Photos
import java.io.File

/**
 * Miniature carrée de la photo d'une boîte ; n'occupe aucune place s'il n'y a pas de photo.
 * [description] null = image décorative (ignorée par TalkBack, parce que le nom du médicament est déjà lu à côté).
 */
@Composable
fun Vignette(nom: String?, cote: Dp, modifier: Modifier = Modifier, description: String? = null) {
    if (nom == null) return
    val contexte = LocalContext.current
    val densite = LocalContext.current.resources.displayMetrics.density
    val image by produceState<ImageBitmap?>(null, nom, cote) {
        value = withContext(Dispatchers.IO) { Photos.charger(contexte, nom, (cote.value * densite).toInt())?.asImageBitmap() }
    }
    image?.let {
        Image(
            it, contentDescription = description, contentScale = ContentScale.Crop,
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
    // Dès que l'application déclare la permission CAMERA, Android la EXIGE aussi pour la capture par l'appli photo du système
    // (sinon SecurityException) : on la demande donc avant de lancer la capture.
    fun lancerCapture() {
        try { camera.launch(uriCapture) } catch (e: ActivityNotFoundException) { erreur = "Aucune application photo disponible." }
    }
    val demandeCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) lancerCapture() else erreur = "Autorisation de la caméra refusée : choisis plutôt une photo dans la galerie."
    }
    val galerie = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) traiter(uri) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Photo de la boîte (facultatif)", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
        Vignette(nom, 120.dp, description = "Aperçu de la photo de la boîte")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                if (cameraAutorisee(contexte)) lancerCapture() else demandeCamera.launch(android.Manifest.permission.CAMERA)
            }) { Text("Prendre une photo") }
            OutlinedButton(onClick = { galerie.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Galerie") }
            if (nom != null) TextButton(onClick = { onChange(null) }) { Text("Retirer") }
        }
        Text(
            "La photo reste sur ce téléphone, réduite et sans position GPS.",
            style = MaterialTheme.typography.bodySmall,
        )
        // liveRegion : TalkBack annonce l'erreur dès qu'elle apparaît
        erreur?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}
