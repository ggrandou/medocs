package net.grandou.medocs.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as TailleCompose
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import net.grandou.medocs.domain.CodeBoite
import net.grandou.medocs.scan.DecodeurCode
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

fun cameraAutorisee(contexte: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(contexte, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** Scan d'un code de boîte : demande la permission caméra (avec explication) puis ouvre l'aperçu. */
@Composable
fun EcranScan(onCode: (CodeBoite) -> Unit, onSaisieManuelle: () -> Unit) {
    val contexte = LocalContext.current
    var autorisee by remember { mutableStateOf(cameraAutorisee(contexte)) }
    var refusee by remember { mutableStateOf(false) }
    val demande = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> autorisee = ok; refusee = !ok }

    if (!autorisee) {
        ExplicationCamera(
            refusee = refusee,
            onAutoriser = { demande.launch(Manifest.permission.CAMERA) },
            onReglages = { contexte.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${contexte.packageName}"))) },
            onSaisieManuelle = onSaisieManuelle,
        )
    } else {
        ApercuScan(onCode, onSaisieManuelle)
    }
}

@Composable
fun ExplicationCamera(refusee: Boolean, onAutoriser: () -> Unit, onReglages: () -> Unit, onSaisieManuelle: (() -> Unit)?) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Scanner la boîte", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        Text(
            "La caméra sert uniquement à lire le code-barres (ou le petit carré Datamatrix) de la boîte, pour retrouver le médicament sans rien taper. " +
                "Aucune image n'est enregistrée ni envoyée : l'application n'a pas d'accès à internet.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (refusee) {
            Text(
                "Autorisation refusée. Tu peux la donner dans les réglages de l'application.",
                Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error,
            )
            Button(onClick = onReglages, Modifier.fillMaxWidth()) { Text("Ouvrir les réglages de l'application") }
            OutlinedButton(onClick = onAutoriser, Modifier.fillMaxWidth()) { Text("Réessayer") }
        } else {
            Button(onClick = onAutoriser, Modifier.fillMaxWidth()) { Text("Autoriser la caméra") }
        }
        onSaisieManuelle?.let { OutlinedButton(onClick = it, Modifier.fillMaxWidth()) { Text("Saisir le numéro à la place") } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ApercuScan(onCode: (CodeBoite) -> Unit, onSaisieManuelle: () -> Unit) {
    val contexte = LocalContext.current
    val proprietaire = LocalLifecycleOwner.current
    val haptique = LocalHapticFeedback.current
    val onCodeActuel by rememberUpdatedState(onCode)
    var camera by remember { mutableStateOf<Camera?>(null) }
    var lampe by remember { mutableStateOf(false) }
    var horsMedicament by remember { mutableStateOf(false) }
    val termine = remember { AtomicBoolean(false) }
    val vue = remember { PreviewView(contexte).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    var fournisseurLie by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val executeur = remember { Executors.newSingleThreadExecutor() }
    val decodeur = remember { DecodeurCode() }
    val principal = remember { Handler(Looper.getMainLooper()) }

    // awaitInstance : l'API suspendante de CameraX (pas de ListenableFuture à manipuler)
    LaunchedEffect(proprietaire) {
        val fournisseur = ProcessCameraProvider.awaitInstance(contexte)
        fournisseurLie = fournisseur
        val apercu = Preview.Builder().build().also { it.setSurfaceProvider(vue.surfaceProvider) }
        val analyse = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build()
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analyse.setAnalyzer(executeur) { image ->
            try {
                if (!termine.get()) {
                    val plan = image.planes[0]
                    val octets = ByteArray(plan.buffer.remaining()).also { plan.buffer.get(it) }
                    val texte = try { decodeur.decoder(octets, plan.rowStride, image.width, image.height, image.imageInfo.rotationDegrees) } catch (e: RuntimeException) { null }
                    if (texte != null) {
                        val code = CodeBoite.depuisTexte(texte)
                        if (code == null) principal.post { horsMedicament = true }
                        else if (termine.compareAndSet(false, true)) principal.post {
                            haptique.performHapticFeedback(HapticFeedbackType.LongPress)
                            onCodeActuel(code)
                        }
                    }
                }
            } finally { image.close() }
        }
        fournisseur.unbindAll()
        camera = fournisseur.bindToLifecycle(proprietaire, CameraSelector.DEFAULT_BACK_CAMERA, apercu, analyse)
    }
    DisposableEffect(Unit) {
        onDispose {
            // La caméra est rendue dès qu'on quitte l'écran (voyant et batterie)
            camera = null
            fournisseurLie?.unbindAll()
            executeur.shutdown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { vue },
            modifier = Modifier.fillMaxSize().semantics {
                contentDescription = "Aperçu de la caméra. Place le code-barres de la boîte devant l'objectif : il est lu automatiquement."
            },
        )
        // Cadre de visée
        Canvas(Modifier.fillMaxSize()) {
            val largeur = size.width * 0.8f
            val hauteur = largeur * 0.6f
            drawRoundRect(
                Color.White, Offset((size.width - largeur) / 2f, (size.height - hauteur) / 2f), TailleCompose(largeur, hauteur),
                CornerRadius(24.dp.toPx()), Stroke(width = 3.dp.toPx()),
            )
        }
        Surface(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (horsMedicament) "Ce code n'est pas celui d'un médicament. Cherche le code-barres ou le petit carré Datamatrix de la boîte."
                    else "Place le code-barres ou le petit carré (Datamatrix) de la boîte dans le cadre.",
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (camera?.cameraInfo?.hasFlashUnit() == true) {
                        OutlinedButton(onClick = { lampe = !lampe; camera?.cameraControl?.enableTorch(lampe) }) {
                            Text(if (lampe) "Éteindre la lampe" else "Allumer la lampe")
                        }
                    }
                    OutlinedButton(onClick = onSaisieManuelle) { Text("Saisir le numéro à la place") }
                }
            }
        }
    }
}
