package com.example.mitvplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mitvplayer.data.Kind
import com.example.mitvplayer.data.isAdultCategoryName

/**
 * Elegir las categorías del perfil Niños (desde Ajustes, fuera del perfil).
 * Arriba se elige la sección; cada categoría se marca o desmarca con OK. Las infantiles ya vienen marcadas.
 */
@Composable
fun KidsCategoriesScreen(vm: AppViewModel) {
    var kind by remember { mutableStateOf(Kind.LIVE) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        vm.live.ensureLoaded()
        vm.movies.ensureLoaded()
        vm.series.ensureLoaded()
        withFrameNanos { }
        first.tryRequestFocus()
    }
    val state = when (kind) {
        Kind.LIVE -> vm.live
        Kind.MOVIES -> vm.movies
        else -> vm.series
    }
    val categories = state.catalog?.categories.orEmpty().filter { !isAdultCategoryName(it.name) }

    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar(title = "Categorías del perfil Niños", centerTitle = true, onBack = { vm.back() })
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .align(Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TvButton(
                        text = "En vivo",
                        onClick = { kind = Kind.LIVE },
                        selected = kind == Kind.LIVE,
                        compact = true,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(first)
                    )
                    TvButton(
                        text = "Películas",
                        onClick = { kind = Kind.MOVIES },
                        selected = kind == Kind.MOVIES,
                        compact = true,
                        modifier = Modifier.weight(1f)
                    )
                    TvButton(
                        text = "Series",
                        onClick = { kind = Kind.SERIES },
                        selected = kind == Kind.SERIES,
                        compact = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    "✓ = se ve en el perfil Niños. Pulsa OK para marcar o desmarcar.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                when {
                    state.catalog == null -> CenterLoading(text = "Cargando categorías…")
                    categories.isEmpty() -> CenterMessage("No hay categorías en esta sección.")
                    else -> LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(categories, key = { it.id }) { category ->
                            val allowed = vm.kids.allows(kind, category)
                            TvButton(
                                text = (if (allowed) "✓  " else "     ") + category.name.trim(),
                                onClick = { vm.kids.setAllowed(kind, category, !allowed) },
                                selected = allowed,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
