package com.example.mitvplayer.ui

import android.content.Context
import com.example.mitvplayer.data.Kind
import com.example.mitvplayer.data.LiveChannel
import com.example.mitvplayer.data.Movie
import com.example.mitvplayer.data.ResumeEntry
import com.example.mitvplayer.data.SeriesItem

// Cómo se ve cada cosa en las filas y en la portada grande (Inicio, Películas, Series).

fun movieHero(vm: AppViewModel, context: Context, movie: Movie): HeroItem = HeroItem(
    key = "m" + movie.id,
    kind = HeroKind.MOVIE,
    title = movie.name,
    image = movie.poster,
    rating10 = movie.rating10,
    movie = movie,
    onPlay = { vm.playMovie(context, movie) },
    onInfo = { vm.openMovie(movie) }
)

fun movieEntry(vm: AppViewModel, context: Context, movie: Movie): RowEntry = RowEntry(
    title = movie.name,
    image = movie.poster,
    hero = movieHero(vm, context, movie),
    onClick = { vm.openMovie(movie) },
    rating10 = movie.rating10,
    favorite = vm.isFavorite(Kind.MOVIES, movie.id),
    onLongClick = { vm.toggleFavorite(Kind.MOVIES, movie.id) }
)

fun seriesHero(vm: AppViewModel, series: SeriesItem): HeroItem = HeroItem(
    key = "s" + series.id,
    kind = HeroKind.SERIES,
    title = series.name,
    image = series.cover,
    rating10 = series.rating10,
    series = series,
    onPlay = { vm.openSeries(series) },
    onInfo = { vm.openSeries(series) }
)

fun seriesEntry(vm: AppViewModel, series: SeriesItem): RowEntry = RowEntry(
    title = series.name,
    image = series.cover,
    hero = seriesHero(vm, series),
    onClick = { vm.openSeries(series) },
    rating10 = series.rating10,
    favorite = vm.isFavorite(Kind.SERIES, series.id),
    onLongClick = { vm.toggleFavorite(Kind.SERIES, series.id) }
)

fun resumeEntry(vm: AppViewModel, context: Context, entry: ResumeEntry): RowEntry {
    val hero = HeroItem(
        key = "r" + entry.key,
        kind = HeroKind.RESUME,
        title = entry.title,
        image = entry.image,
        subtitle = listOfNotNull(entry.subtitle, "Vas en ${formatDuration(entry.positionMs / 1000)}").joinToString("  ·  "),
        progress = entry.fraction,
        onPlay = { vm.playResume(context, entry) }
    )
    return RowEntry(
        title = listOfNotNull(entry.title, entry.subtitle).joinToString(" · "),
        image = entry.image,
        hero = hero,
        onClick = { vm.playResume(context, entry) },
        progress = entry.fraction,
        caption = "▶  ${formatDuration(entry.positionMs / 1000)}",
        onLongClick = { vm.removeResume(entry) }
    )
}

/** Un canal en una fila: OK lo abre en pantalla completa con [list] para cambiar de canal. */
fun channelEntry(vm: AppViewModel, context: Context, channel: LiveChannel, list: List<LiveChannel>): RowEntry {
    val play = {
        val index = list.indexOfFirst { it.id == channel.id }
        if (index >= 0) vm.playLive(context, list, index) else vm.playLive(context, listOf(channel), 0)
    }
    val hero = HeroItem(
        key = "c" + channel.id,
        kind = HeroKind.LIVE,
        title = channel.name,
        image = channel.icon,
        subtitle = if (channel.number > 0) "Canal ${channel.number}" else null,
        channel = channel,
        onPlay = play
    )
    return RowEntry(
        title = channel.name,
        image = channel.icon,
        hero = hero,
        onClick = play,
        caption = if (channel.number > 0) channel.number.toString() else "EN VIVO",
        favorite = vm.isFavorite(Kind.LIVE, channel.id),
        onLongClick = { vm.toggleFavorite(Kind.LIVE, channel.id) }
    )
}

/** Texto del botón principal de la portada. */
fun playLabel(hero: HeroItem?): String = when (hero?.kind) {
    HeroKind.SERIES -> "▶  Ver episodios"
    HeroKind.RESUME -> "▶  Continuar"
    HeroKind.LIVE -> "▶  Ver canal"
    else -> "▶  Ver ahora"
}

/** Tipo y número para "Mi lista" de lo que está en la portada (null = no se puede guardar). */
fun favoriteOf(hero: HeroItem?): Pair<Kind, String>? = when {
    hero?.movie != null -> Kind.MOVIES to hero.movie.id
    hero?.series != null -> Kind.SERIES to hero.series.id
    hero?.channel != null -> Kind.LIVE to hero.channel.id
    else -> null
}
