package com.example.mitvplayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import com.example.mitvplayer.data.Item
import com.example.mitvplayer.data.Kind

private const val BROWSE_ROW_LIMIT = 40

/** Películas, estilo plataformas de streaming. */
@Composable
fun MoviesScreen(vm: AppViewModel) {
    val context = LocalContext.current
    BrowseRows(
        vm = vm,
        state = vm.movies,
        memory = vm.movieRows,
        section = NavSection.MOVIES,
        kind = Kind.MOVIES,
        noun = "películas",
        resumeType = "movie",
        entryOf = { movieEntry(vm, context, it) }
    )
}

/** Series, estilo plataformas de streaming. */
@Composable
fun SeriesScreen(vm: AppViewModel) {
    BrowseRows(
        vm = vm,
        state = vm.series,
        memory = vm.seriesRows,
        section = NavSection.SERIES,
        kind = Kind.SERIES,
        noun = "series",
        resumeType = "episode",
        entryOf = { seriesEntry(vm, it) }
    )
}

/**
 * Arriba la portada grande del título enfocado; abajo filas: seguir viendo, recién agregadas, mejor
 * calificadas, mi lista, vistas hace poco y una fila por cada categoría (con "Ver todo" al final).
 * La fila con el foco sube hasta arriba y la portada cambia al título que se va recorriendo.
 */
@Composable
private fun <T : Item> BrowseRows(
    vm: AppViewModel,
    state: BrowseState<T>,
    memory: RowsMemory,
    section: NavSection,
    kind: Kind,
    noun: String,
    resumeType: String,
    entryOf: (T) -> RowEntry
) {
    val context = LocalContext.current
    val playRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { state.ensureLoaded() }

    val newest = state.newestVisible
    val top = state.topRatedVisible
    val categories = state.visibleCategories
    val favorites by remember { derivedStateOf { state.itemsFor(ROW_FAV) } }
    val recents by remember { derivedStateOf { state.itemsFor(ROW_RECENT) } }
    val resume by remember { derivedStateOf { vm.resumeEntries.filter { it.type == resumeType } } }
    val catalog = state.catalog

    val rows = remember(catalog, newest, top, categories, favorites, recents, resume) {
        if (catalog == null) {
            emptyList()
        } else {
            buildList {
                if (resume.isNotEmpty()) {
                    val list = resume.distinctBy { it.key }
                    add(MediaRow("seguir", "Seguir viendo", TileStyle.WIDE, list.size, { list[it].key }, { resumeEntry(vm, context, list[it]) }))
                }
                fun addRow(key: String, title: String, items: List<T>, seeAll: String?) {
                    if (items.isEmpty()) return
                    val list = if (items.size > BROWSE_ROW_LIMIT) items.subList(0, BROWSE_ROW_LIMIT) else items
                    add(
                        MediaRow(
                            key, title, TileStyle.POSTER, list.size, { list[it].id }, { entryOf(list[it]) },
                            onSeeAll = seeAll?.let { id -> { vm.openGrid(kind, id) } }
                        )
                    )
                }
                addRow("nuevas", "Recién agregadas", newest, ROW_ADDED)
                addRow("top", "Las mejor calificadas", top, null)
                addRow("milista", "Mi lista", favorites, ROW_FAV)
                addRow("vistas", "Vistas hace poco", recents, ROW_RECENT)
                for (category in categories) {
                    val items = state.itemsFor(category.id)
                    if (items.isNotEmpty()) addRow("c" + category.id, category.name.trim(), items, category.id)
                }
            }
        }
    }

    val heroState = remember { mutableStateOf(memory.hero) }
    // Al entrar se enfoca la primera tarjeta (o la última donde se estaba).
    LaunchedEffect(rows.isNotEmpty()) {
        if (rows.isEmpty()) return@LaunchedEffect
        if (memory.row == null || rows.none { it.key == memory.row }) {
            memory.focused(rows[0].key, 0)
            memory.listState.scrollToItem(0)
        }
        withFrameNanos { }
        com.example.mitvplayer.QaProbe.ready(noun, "${state.visibleItems.size} elementos, ${rows.size} filas")
        withFrameNanos { }
        if (!memory.requester.tryFocus()) playRequester.tryFocus()
    }
    val featured = remember(rows) { rows.firstOrNull()?.takeIf { it.size > 0 }?.entryAt?.invoke(0)?.hero }

    NavScaffold(
        vm = vm,
        current = section,
        onBackToContent = { memory.requester.tryFocus() || playRequester.tryFocus() }
    ) {
        StreamHero(vm, heroState, featured, playRequester, placeholder = if (kind == Kind.MOVIES) "Películas" else "Series")
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(HERO_HEIGHT))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val error = state.error
                when {
                    catalog == null && error != null -> ErrorWithRetry(message = error, onRetry = { state.retry() })
                    catalog == null -> CenterLoading(
                        text = state.progress ?: "Cargando la lista completa…\nLa primera vez puede tardar un poco."
                    )
                    rows.isEmpty() -> CenterMessage("No hay $noun por ahora.")
                    else -> MediaRows(
                        rows = rows,
                        memory = memory,
                        onFocusEntry = { heroState.value = it.hero },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
