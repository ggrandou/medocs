package net.grandou.medocs.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.grandou.medocs.R
import net.grandou.medocs.data.Medicament
import net.grandou.medocs.domain.TypeMedicament
import net.grandou.medocs.domain.TypesMedicament

private fun dessin(t: TypeMedicament): Int = when (t) {
    TypeMedicament.COMPRIME -> R.drawable.ic_med_comprime
    TypeMedicament.GELULE -> R.drawable.ic_med_gelule
    TypeMedicament.LIQUIDE -> R.drawable.ic_med_liquide
    TypeMedicament.GOUTTES -> R.drawable.ic_med_gouttes
    TypeMedicament.INJECTION -> R.drawable.ic_med_injection
    TypeMedicament.SACHET -> R.drawable.ic_med_sachet
    TypeMedicament.TUBE -> R.drawable.ic_med_tube
    TypeMedicament.SUPPOSITOIRE -> R.drawable.ic_med_suppositoire
    TypeMedicament.SPRAY -> R.drawable.ic_med_spray
    TypeMedicament.GENERIQUE -> R.drawable.ic_med_boite
}

/** Couleurs d'une tuile : fond et dessin viennent de la même paire du thème, donc lisibles en clair comme en sombre. */
@Composable
private fun couleursDe(t: TypeMedicament): Pair<Color, Color> {
    val c = MaterialTheme.colorScheme
    return when (t) {
        TypeMedicament.COMPRIME, TypeMedicament.GOUTTES, TypeMedicament.SUPPOSITOIRE -> c.primaryContainer to c.onPrimaryContainer
        TypeMedicament.GELULE, TypeMedicament.SACHET, TypeMedicament.SPRAY -> c.tertiaryContainer to c.onTertiaryContainer
        TypeMedicament.LIQUIDE, TypeMedicament.INJECTION, TypeMedicament.TUBE -> c.secondaryContainer to c.onSecondaryContainer
        TypeMedicament.GENERIQUE -> c.surfaceVariant to c.onSurfaceVariant
    }
}

/** Tuile à l'icône du type de médicament (comprimé, gélule, liquide…) : décorative, le nom est lu à côté. */
@Composable
fun TuileTypeMedicament(type: TypeMedicament, cote: Dp, modifier: Modifier = Modifier) {
    val (fond, trait) = couleursDe(type)
    Box(modifier.size(cote).clip(RoundedCornerShape(8.dp)).background(fond)) {
        Icon(painterResource(dessin(type)), contentDescription = null, tint = trait, modifier = Modifier.fillMaxSize().padding(cote * 0.14f))
    }
}

/**
 * Visuel d'un médicament : sa photo si elle existe, sinon l'icône de son type. La tuile est toujours dessinée,
 * la photo la recouvre une fois chargée : la mise en page ne bouge donc jamais.
 */
@Composable
fun VisuelMedicament(m: Medicament, cote: Dp, modifier: Modifier = Modifier) {
    val type = remember(m.forme, m.unite, m.nom) { TypesMedicament.deduire(m.forme, m.unite, m.nom) }
    Box(modifier.size(cote)) {
        TuileTypeMedicament(type, cote)
        Vignette(m.photoPath, cote)
    }
}
