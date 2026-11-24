package com.example.mitvplayer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mitvplayer.R
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
      BoxWithConstraints(Modifier.fillMaxSize()) {
        val heroHeight = heroHeightFor(maxHeight)
        StreamHero(
            vm, heroState, featured, playRequester,
            placeholder = if (kind == Kind.MOVIES) "Películas" else "Series",
            heroHeight = heroHeight
        )
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(heroHeight))
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
}

/** Columnas de la lista completa ("Ver todo"). */
private const val SEE_ALL_COLUMNS = 7

/**
 * "Ver todo" de una fila: la lista completa (una categoría, recién agregadas, mi lista…) en cuadrícula, con el
 * menú lateral y un botón para cambiar el orden. Al volver de una ficha, el foco regresa al mismo póster.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun <T : Item> SeeAllScreen(
    vm: AppViewModel,
    state: BrowseState<T>,
    kind: Kind,
    noun: String,
    posterOf: (T) -> String?,
    onOpen: (T) -> Unit
) {
    val sortRequester = remember { FocusRequester() }
    val items = state.items
    val catalog = state.catalog
    // Póster que tiene el foco (ahí vuelve el foco desde el menú lateral o al regresar de una ficha).
    var focusedIndex by remember { mutableIntStateOf(state.lastFocusedIndex) }

    LaunchedEffect(catalog != null) {
        state.ensureLoaded()
        if (catalog == null) return@LaunchedEffect
        withFrameNanos { }
        com.example.mitvplayer.QaProbe.ready(noun, "${state.items.size} elementos en la lista completa")
        withFrameNanos { }
        withFrameNanos { }
        if (!state.restoreRequester.tryFocus()) sortRequester.tryFocus()
        state.restoring = false
    }

    val title = when (state.selectedId) {
        ROW_ADDED -> "Recién agregadas"
        ROW_FAV -> "Mi lista"
        ROW_RECENT -> "Vistas hace poco"
        ROW_ALL -> if (kind == Kind.MOVIES) "Todas las películas" else "Todas las series"
        else -> catalog?.categories?.firstOrNull { it.id == state.selectedId }?.name?.trim() ?: noun
    }
    val sorts = listOf(SortMode.DEFAULT, SortMode.AZ, SortMode.ZA, SortMode.NEWEST, SortMode.RATING)

    NavScaffold(
        vm = vm,
        current = if (kind == Kind.MOVIES) NavSection.MOVIES else NavSection.SERIES,
        onBackToContent = { state.restoreRequester.tryFocus() || sortRequester.tryFocus() }
    ) {
        Image(
            bitmap = rememberArt(if (kind == Kind.MOVIES) R.drawable.art_movies else R.drawable.art_series),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xEB07060F), Color(0xFA07060F))))
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = CONTENT_START, end = 28.dp, top = 22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        fontStyle = FontStyle.Italic,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (items.size == 1) "1 título" else "${items.size} títulos",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                // Cada OK pasa al siguiente orden.
                HeroButton(
                    text = "⇅  Orden: ${state.sort.label}",
                    onClick = {
                        state.sort = sorts[(sorts.indexOf(state.sort) + 1) % sorts.size]
                        state.lastFocusedIndex = 0
                        focusedIndex = 0
                        state.gridState.requestScrollToItem(0)
                    },
                    compact = true,
                    modifier = Modifier
                        .focusProperties {
                            up = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                        }
                        .focusRequester(sortRequester)
                )
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    catalog == null -> CenterLoading(text = state.progress ?: "Cargando…")
                    items.isEmpty() -> CenterMessage(
                        if (state.selectedId == ROW_FAV) "Aún no tienes nada en tu lista.\nMantén pulsado un póster, o pulsa la tecla Menú, para añadirlo."
                        else "No hay $noun en esta lista."
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(SEE_ALL_COLUMNS),
                        state = state.gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 40.dp, end = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                            val target by remember(index) { derivedStateOf { focusedIndex == index } }
                            val lastColumn = index % SEE_ALL_COLUMNS == SEE_ALL_COLUMNS - 1 || index == items.size - 1
                            PosterCard(
                                title = item.name,
                                imageUrl = posterOf(item),
                                rating10 = item.rating10,
                                favorite = vm.isFavorite(kind, item.id),
                                onClick = { onOpen(item) },
                                onLongClick = { vm.toggleFavorite(kind, item.id) },
                                modifier = Modifier
                                    // ▶ en la última columna no se sale al botón de orden.
                                    .then(if (lastColumn) Modifier.focusProperties { right = FocusRequester.Cancel } else Modifier)
                                    .focusIf(target, state.restoreRequester)
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            state.lastFocusedIndex = index
                                            focusedIndex = index
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}
