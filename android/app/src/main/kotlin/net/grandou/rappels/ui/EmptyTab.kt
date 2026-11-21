package net.grandou.rappels.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Tab still empty: only the title bar is shown. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmptyTab(title: String) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text(title) }) },
    ) { padding -> Box(Modifier.fillMaxSize().padding(padding)) }
}
