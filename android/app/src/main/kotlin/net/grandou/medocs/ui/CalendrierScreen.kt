package net.grandou.medocs.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.grandou.medocs.R
import net.grandou.medocs.domain.EtatOccurrence
import net.grandou.medocs.domain.StatutJour
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val FORMAT_MOIS = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.FRANCE)
private val FORMAT_DATE_LONGUE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)

/** Aspect d'une pastille : fond, couleur du chiffre, contour éventuel. */
private data class StylePastille(val fond: Color, val texte: Color, val contour: Color? = null)

/**
 * Couleurs des pastilles. Vert, orange et rouge reprennent celles des cartes de l'écran « Aujourd'hui » (pris, en retard, sautée) ;
 * gris = à venir avec du stock, blanc = à venir au-delà du stock. Chaque couleur est doublée d'un libellé (légende, TalkBack).
 */
@Composable
private fun stylePastille(s: StatutJour): StylePastille {
    val sombre = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (s) {
        StatutJour.TOUT_PRIS -> couleursStatut(EtatOccurrence.PRISE, sombre).let { StylePastille(it.first, it.second) }
        StatutJour.PARTIEL -> couleursStatut(EtatOccurrence.EN_RETARD, sombre).let { StylePastille(it.first, it.second) }
        StatutJour.AUCUNE_PRISE -> couleursStatut(EtatOccurrence.SAUTEE, sombre).let { StylePastille(it.first, it.second) }
        StatutJour.A_VENIR_COUVERT ->
            if (sombre) StylePastille(Color(0xFF454A4C), Color(0xFFE6E9EA)) else StylePastille(Color(0xFFD5D9DB), Color(0xFF1B1F20))
        StatutJour.A_VENIR_SANS_STOCK ->
            if (sombre) StylePastille(Color(0xFFF1F3F4), Color(0xFF111415), Color(0xFFF1F3F4)) else StylePastille(Color.White, Color(0xFF1B1F20), MaterialTheme.colorScheme.outline)
        StatutJour.SANS_PRESCRIPTION -> StylePastille(Color.Transparent, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun libelleStatut(s: StatutJour) = when (s) {
    StatutJour.TOUT_PRIS -> "tout pris"
    StatutJour.PARTIEL -> "des prises sautées ou non faites"
    StatutJour.AUCUNE_PRISE -> "aucune prise faite"
    StatutJour.A_VENIR_COUVERT -> "prises prévues, stock suffisant"
    StatutJour.A_VENIR_SANS_STOCK -> "prises prévues, stock insuffisant"
    StatutJour.SANS_PRESCRIPTION -> "aucune prise prévue"
}

/**
 * Onglet « Historique » : calendrier d'un mois, un jour = une pastille colorée selon les prises.
 * Toucher un jour ouvre le détail de ses prises (corrections possibles).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CalendrierScreen(
    mois: YearMonth,
    statuts: Map<LocalDate, StatutJour>,
    aujourdhui: LocalDate,
    onMois: (YearMonth) -> Unit,
    onJour: (LocalDate) -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Historique", Modifier.semantics { heading() }) }) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Navigation de mois en mois
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMois(mois.minusMonths(1)) }) {
                    Icon(painterResource(R.drawable.ic_chevron_gauche), contentDescription = "Mois précédent")
                }
                Text(
                    mois.format(FORMAT_MOIS).replaceFirstChar { it.uppercase() },
                    Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                )
                IconButton(onClick = { onMois(mois.plusMonths(1)) }) {
                    Icon(painterResource(R.drawable.ic_chevron), contentDescription = "Mois suivant")
                }
            }
            if (mois != YearMonth.from(aujourdhui)) {
                TextButton(onClick = { onMois(YearMonth.from(aujourdhui)) }, Modifier.align(Alignment.CenterHorizontally)) { Text("Revenir au mois en cours") }
            }

            // En-tête des jours de la semaine (lundi en premier)
            Row(Modifier.fillMaxWidth()) {
                for (j in DayOfWeek.entries) {
                    Text(
                        j.getDisplayName(TextStyle.NARROW, Locale.FRANCE).uppercase(),
                        Modifier.weight(1f).semantics { contentDescription = j.getDisplayName(TextStyle.FULL, Locale.FRANCE) },
                        style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Grille : les cases vides du début de mois complètent la première semaine
            val decalage = mois.atDay(1).dayOfWeek.value - 1
            val cases = decalage + mois.lengthOfMonth()
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (ligne in 0 until (cases + 6) / 7) {
                Row(Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val n = ligne * 7 + col - decalage + 1
                        if (n < 1 || n > mois.lengthOfMonth()) {
                            Box(Modifier.weight(1f).height(48.dp))
                        } else {
                            val date = mois.atDay(n)
                            Pastille(date, statuts[date] ?: StatutJour.SANS_PRESCRIPTION, date == aujourdhui, Modifier.weight(1f)) { onJour(date) }
                        }
                    }
                }
            }
            }

            Legende()
        }
    }
}

/** Une case du calendrier : zone tactile de 48 dp de haut, pastille ou point au centre, anneau pour aujourd'hui. */
@Composable
private fun Pastille(date: LocalDate, statut: StatutJour, estAujourdhui: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val style = stylePastille(statut)
    val description = buildString {
        append(date.format(FORMAT_DATE_LONGUE))
        if (estAujourdhui) append(", aujourd'hui")
        append(", ").append(libelleStatut(statut))
    }
    Box(
        modifier.height(48.dp).clickable(onClickLabel = "Voir les prises de ce jour", role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(44.dp).then(if (estAujourdhui) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (statut == StatutJour.SANS_PRESCRIPTION) {
                // Point : aucune prise prévue ce jour-là
                Box(Modifier.size(6.dp).clip(CircleShape).background(style.texte.copy(alpha = 0.6f)))
            } else {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(style.fond)
                        .then(if (style.contour != null) Modifier.border(BorderStroke(1.5.dp, style.contour), CircleShape) else Modifier)
                        .clearAndSetSemantics {},
                    contentAlignment = Alignment.Center,
                ) {
                    Text(date.dayOfMonth.toString(), color = style.texte, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legende() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Jours passés", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ElementLegende(StatutJour.TOUT_PRIS, "Tout pris")
            ElementLegende(StatutJour.PARTIEL, "Sautées ou non faites")
            ElementLegende(StatutJour.AUCUNE_PRISE, "Aucune prise faite")
        }
        Text("Jours à venir", Modifier.padding(top = 4.dp).semantics { heading() }, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ElementLegende(StatutJour.A_VENIR_COUVERT, "Prévues, stock suffisant")
            ElementLegende(StatutJour.A_VENIR_SANS_STOCK, "Prévues, au-delà du stock")
        }
        ElementLegende(StatutJour.SANS_PRESCRIPTION, "Aucune prise prévue")
    }
}

@Composable
private fun ElementLegende(statut: StatutJour, libelle: String) {
    val style = stylePastille(statut)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (statut == StatutJour.SANS_PRESCRIPTION) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(style.texte.copy(alpha = 0.6f)))
            } else {
                Box(
                    Modifier.size(18.dp).clip(CircleShape).background(style.fond)
                        .then(if (style.contour != null) Modifier.border(BorderStroke(1.5.dp, style.contour), CircleShape) else Modifier),
                )
            }
        }
        Text(libelle, style = MaterialTheme.typography.bodyMedium)
    }
}
