package com.example.mitvplayer.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.example.mitvplayer.data.Kind
import com.example.mitvplayer.data.LiveChannel
import com.example.mitvplayer.data.Movie
import com.example.mitvplayer.data.SeriesItem

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Categorías que salen como filas en el Inicio (las primeras con suficientes títulos). */
private const val HOME_MOVIE_CATEGORIES = 4
private const val HOME_SERIES_CATEGORIES = 3
private const val ROW_LIMIT = 40

/**
 * Inicio de Facst Play TV, estilo plataformas de streaming: arriba la portada grande del título enfocado
 * (imagen de fondo, datos, de qué trata y botones) y abajo filas que se recorren con el control:
 * continuar viendo, en vivo ahora, estrenos, series nuevas, mejor calificadas, mi lista y categorías.
 */
@Composable
fun HomeScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val memory = vm.homeRows
    val playRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        vm.live.ensureLoaded()
        vm.movies.ensureLoaded()
        vm.series.ensureLoaded()
    }

    // Todo viene de listas ya calculadas; solo cambia cuando cambia de verdad (no en cada movimiento del foco).
    val newMovies = vm.movies.newestVisible
    val topMovies = vm.movies.topRatedVisible
    val newSeries = vm.series.newestVisible
    val resume by remember { derivedStateOf { vm.resumeEntries } }
    val liveVisible = vm.live.visibleItems
    val recentLive by remember { derivedStateOf { vm.live.itemsFor(ROW_RECENT) } }
    val favMovies by remember { derivedStateOf { vm.movies.itemsFor(ROW_FAV) } }
    val favSeries by remember { derivedStateOf { vm.series.itemsFor(ROW_FAV) } }
    val movieCategories = vm.movies.visibleCategories
    val seriesCategories = vm.series.visibleCategories

    val rows = remember(
        newMovies, topMovies, newSeries, resume, liveVisible, recentLive, favMovies, favSeries,
        movieCategories, seriesCategories
    ) {
        buildList {
            if (resume.isNotEmpty()) {
                val list = resume.distinctBy { it.key }
                add(MediaRow("continuar", "Continuar viendo", TileStyle.WIDE, list.size, { list[it].key }, { resumeEntry(vm, context, list[it]) }))
            }
            val channels: List<LiveChannel> = (recentLive.ifEmpty { liveVisible }).take(ROW_LIMIT)
            if (channels.isNotEmpty()) {
                val zap = if (recentLive.isNotEmpty()) recentLive else liveVisible
                add(
                    MediaRow(
                        "envivo", if (recentLive.isNotEmpty()) "En vivo · tus canales" else "En vivo ahora",
                        TileStyle.CHANNEL, channels.size, { channels[it].id },
                        { channelEntry(vm, context, channels[it], zap) },
                        onSeeAll = { vm.go(Screen.Live) }
                    )
                )
            }
            if (newMovies.isNotEmpty()) add(movieRow(vm, context, "estrenos", "Estrenos de películas", newMovies) { vm.openGrid(Kind.MOVIES, ROW_ADDED) })
            if (newSeries.isNotEmpty()) add(seriesRow(vm, "seriesnuevas", "Series nuevas", newSeries) { vm.openGrid(Kind.SERIES, ROW_ADDED) })
            if (topMovies.isNotEmpty()) add(movieRow(vm, context, "top", "Las mejor calificadas", topMovies, null))
            if (favMovies.isNotEmpty()) add(movieRow(vm, context, "favpeliculas", "Mi lista · películas", favMovies) { vm.openGrid(Kind.MOVIES, ROW_FAV) })
            if (favSeries.isNotEmpty()) add(seriesRow(vm, "favseries", "Mi lista · series", favSeries) { vm.openGrid(Kind.SERIES, ROW_FAV) })
            movieCategories.asSequence()
                .map { it to vm.movies.itemsFor(it.id) }
                .filter { it.second.size >= 6 }
                .take(HOME_MOVIE_CATEGORIES)
                .forEach { (category, items) ->
                    add(movieRow(vm, context, "mc" + category.id, category.name.trim(), items) { vm.openGrid(Kind.MOVIES, category.id) })
                }
            seriesCategories.asSequence()
                .map { it to vm.series.itemsFor(it.id) }
                .filter { it.second.size >= 6 }
                .take(HOME_SERIES_CATEGORIES)
                .forEach { (category, items) ->
                    add(seriesRow(vm, "sc" + category.id, "Series · ${category.name.trim()}", items) { vm.openGrid(Kind.SERIES, category.id) })
                }
        }
    }

    // Lo destacado al entrar: el estreno más nuevo con póster.
    val featured = remember(newMovies) { newMovies.firstOrNull { it.poster != null }?.let { movieHero(vm, context, it) } }
    // Lo que muestra la portada (se lee solo dentro de la portada: mover el foco no redibuja las filas).
    val heroState = remember { mutableStateOf(memory.hero) }

    // Al entrar el foco va a "Ver ahora"; al volver de una ficha o del reproductor, a donde se estaba.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        if (!memory.requester.tryFocus()) playRequester.tryFocus()
    }

    NavScaffold(
        vm = vm,
        current = NavSection.HOME,
        onBackToContent = { memory.requester.tryFocus() || playRequester.tryFocus() },
        exitOnBack = true
    ) {
      BoxWithConstraints(Modifier.fillMaxSize()) {
        val heroHeight = heroHeightFor(maxHeight)
        StreamHero(vm, heroState, featured, playRequester, heroHeight = heroHeight)
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(heroHeight))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (rows.isEmpty()) {
                    val error = vm.movies.error ?: vm.live.error
                    if (error != null && vm.movies.catalog == null && vm.live.catalog == null) {
                        ErrorWithRetry(message = error, onRetry = { vm.refreshAll() })
                    } else {
                        CenterLoading(text = vm.movies.progress ?: "Cargando…")
                    }
                } else {
                    MediaRows(
                        rows = rows,
                        memory = memory,
                        onFocusEntry = { heroState.value = it.hero },
                        modifier = Modifier.fillMaxSize(),
                        upFromFirstRow = playRequester
                    )
                }
            }
        }
      }
    }

    UpdateDialogs(vm)

    // Aviso del dueño de la app (mantenimiento, promociones…), una vez por aparato.
    val notice = vm.noticeToShow
    val updatePrompt = vm.update is UpdateState.Downloading || (vm.update is UpdateState.Available && !vm.updateDismissed)
    if (notice != null && !updatePrompt) {
        OptionsDialog(
            title = notice.title,
            message = notice.message,
            options = listOf(Opt("Entendido") { }),
            onDismiss = { vm.dismissNotice() }
        )
    }
}

/**
 * Portada grande con sus botones (▶ Ver ahora, ⓘ Más información, + Mi lista) para lo que está en [heroState]
 * o, si no hay nada enfocado todavía, para [featured]. También la usan Películas y Series.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun StreamHero(
    vm: AppViewModel,
    heroState: MutableState<HeroItem?>,
    featured: HeroItem?,
    playRequester: FocusRequester,
    placeholder: String = "Facst Play TV",
    heroHeight: Dp = HERO_HEIGHT
) {
    var favTick by remember { mutableIntStateOf(0) }
    val shown = heroState.value ?: featured
    val details = rememberHeroDetails(vm, shown)
    val info = shown?.onInfo
    val fav = favoriteOf(shown)
    // ▲ desde los botones no hace nada; ▶ en el último tampoco (no se brinca a otra parte de la pantalla).
    fun Modifier.edge(last: Boolean): Modifier = focusProperties {
        up = FocusRequester.Cancel
        if (last) right = FocusRequester.Cancel
    }
    Box(Modifier.fillMaxSize()) {
        HeroBackground(shown, details, height = heroHeight + 90.dp)
        HeroText(
            hero = shown,
            details = details,
            placeholder = placeholder,
            modifier = Modifier
                .height(heroHeight)
                .fillMaxWidth()
        ) {
            HeroButton(
                text = playLabel(shown),
                onClick = { shown?.onPlay?.invoke() },
                primary = true,
                modifier = Modifier
                    .edge(last = info == null && fav == null)
                    .focusRequester(playRequester)
            )
            if (info != null) {
                HeroButton("ⓘ  Más información", info, Modifier.edge(last = fav == null))
            }
            if (fav != null) {
                val inList = remember(favTick, fav) { vm.isFavorite(fav.first, fav.second) }
                HeroButton(
                    if (inList) "✓  En mi lista" else "+  Mi lista",
                    {
                        vm.toggleFavorite(fav.first, fav.second)
                        favTick++
                    },
                    Modifier.edge(last = true)
                )
            }
        }
    }
}

private fun movieRow(
    vm: AppViewModel,
    context: Context,
    key: String,
    title: String,
    items: List<Movie>,
    onSeeAll: (() -> Unit)?
): MediaRow {
    val list = if (items.size > ROW_LIMIT) items.subList(0, ROW_LIMIT) else items
    return MediaRow(key, title, TileStyle.POSTER, list.size, { list[it].id }, { movieEntry(vm, context, list[it]) }, onSeeAll)
}

private fun seriesRow(
    vm: AppViewModel,
    key: String,
    title: String,
    items: List<SeriesItem>,
    onSeeAll: (() -> Unit)?
): MediaRow {
    val list = if (items.size > ROW_LIMIT) items.subList(0, ROW_LIMIT) else items
    return MediaRow(key, title, TileStyle.POSTER, list.size, { list[it].id }, { seriesEntry(vm, list[it]) }, onSeeAll)
}
