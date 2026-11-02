package com.example.mitvplayer.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.mitvplayer.R
import com.example.mitvplayer.RecordingManager
import com.example.mitvplayer.data.AccountInfo
import com.example.mitvplayer.data.EpgEntry
import com.example.mitvplayer.data.LiveChannel
import com.example.mitvplayer.data.ResumeEntry
import kotlinx.coroutines.delay

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Una tarjeta de una fila de pósters. */
private class Tile(
    val key: String,
    val title: String,
    val image: String?,
    val rating10: Float,
    val onClick: () -> Unit
)

/**
 * Inicio de Facst Play TV: pestañas arriba, el último canal en vivo en grande (vista previa sin sonido),
 * los canales recientes al lado y abajo filas para seguir viendo y descubrir lo nuevo.
 */
@Composable
fun HomeScreen(vm: AppViewModel) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var confirmExit by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    val heroRequester = remember { FocusRequester() }
    val preview = remember { LivePreview(context) }

    LaunchedEffect(Unit) {
        vm.live.ensureLoaded()
        vm.movies.ensureLoaded()
        vm.series.ensureLoaded()
    }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(15_000)
        }
    }

    // La vista previa se apaga al salir de Inicio o al mandar la app al fondo (libera la conexión).
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) preview.stop()
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            preview.stop()
        }
    }

    val channels = vm.live.visibleItems
    val byId = remember(channels) { channels.associateBy { it.id } }
    val recentIds = vm.library?.recentLive?.ids.orEmpty()
    val recent = remember(recentIds, byId) { recentIds.mapNotNull { byId[it] } }
    val suggested = if (recent.isNotEmpty()) recent else channels.take(6)
    var heroChannel by remember { mutableStateOf<LiveChannel?>(null) }
    val hero = heroChannel ?: suggested.firstOrNull()

    // Programa actual y siguiente del canal grande.
    var epg by remember(hero?.id) { mutableStateOf<List<EpgEntry>>(emptyList()) }
    LaunchedEffect(hero?.id) {
        val ch = hero ?: return@LaunchedEffect
        epg = vm.epgFor(ch.id)
    }
    // Se espera un momento antes de reproducir, para no abrir un canal por cada canal que se recorre.
    LaunchedEffect(hero?.id) {
        val ch = hero ?: return@LaunchedEffect
        delay(700)
        if (RecordingManager.current == null) preview.play(ch, vm.liveUrl(ch))
    }
    // Sin sonido en Inicio: el sonido sale al abrir el canal en pantalla completa.
    LaunchedEffect(preview.player) { preview.player?.volume = 0f }

    val movies = vm.movies.visibleItems
    val seriesItems = vm.series.visibleItems
    val newMovies = remember(movies) { movies.sortedByDescending { it.added }.take(24) }
    val newSeries = remember(seriesItems) { seriesItems.sortedByDescending { it.added }.take(24) }
    val resume = vm.resumeEntries

    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        heroRequester.tryRequestFocus()
    }

    fun openFull(channel: LiveChannel) {
        preview.stop()
        val list = vm.live.visibleItems
        val index = list.indexOfFirst { it.id == channel.id }
        if (index >= 0) vm.playLive(context, list, index) else vm.playLive(context, listOf(channel), 0)
    }

    AppBackground(dim = 0.15f) {
        Column(Modifier.fillMaxSize()) {
            TopTabs(
                vm = vm,
                now = now,
                onSearch = { vm.open(Screen.Search(SearchScope.ALL)) },
                onSettings = { vm.open(Screen.Settings) },
                onMore = { showMore = true },
                onExit = { confirmExit = true }
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 40.dp, end = 40.dp, top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LiveHero(
                        channel = hero,
                        epg = epg,
                        preview = preview,
                        loading = vm.live.loading && channels.isEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(heroRequester),
                        onOpen = { hero?.let { openFull(it) } ?: vm.open(Screen.Live) }
                    )
                    RecentChannels(
                        channels = suggested,
                        title = if (recent.isNotEmpty()) "Canales recientes" else "Canales",
                        selectedId = hero?.id,
                        onFocusChannel = { heroChannel = it },
                        onOpen = { openFull(it) },
                        onSeeAll = { vm.open(Screen.Live) },
                        modifier = Modifier.width(290.dp)
                    )
                }
                if (resume.isNotEmpty()) {
                    ContinueRow(resume) {
                        preview.stop()
                        vm.playResume(context, it)
                    }
                }
                if (newMovies.isNotEmpty()) {
                    PosterRow(
                        "Estrenos de la semana",
                        newMovies.map { m -> Tile("m" + m.id, m.name, m.poster, m.rating10) { vm.openMovie(m) } }
                    )
                }
                if (newSeries.isNotEmpty()) {
                    PosterRow(
                        "Series nuevas",
                        newSeries.map { s -> Tile("s" + s.id, s.name, s.cover, s.rating10) { vm.openSeries(s) } }
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    UpdateDialogs(vm)

    if (showMore) {
        OptionsDialog(
            title = "Más opciones",
            options = listOf(
                Opt("Continuar viendo") { vm.open(Screen.Continue) },
                Opt("Pantalla múltiple") { vm.open(Screen.Multi) },
                Opt("Catch up (programas que ya pasaron)") { vm.open(Screen.CatchUp) },
                Opt("Radio") { vm.open(Screen.Radio) },
                Opt("Grabaciones") { vm.open(Screen.Recordings) },
                Opt("Avisos") { vm.open(Screen.Notifications) },
                Opt("Mi cuenta") { vm.open(Screen.Account) },
                Opt("Cancelar") { }
            ),
            onDismiss = { showMore = false }
        )
    }

    if (confirmExit) {
        OptionsDialog(
            title = "¿Qué quieres hacer?",
            options = listOf(
                Opt("Cerrar sesión") { vm.logout() },
                Opt("Salir de la aplicación") { context.findActivity()?.finish() },
                Opt("Cancelar") { }
            ),
            onDismiss = { confirmExit = false }
        )
    }
}

// ------------------------------------------------------------------ barra de arriba

@Composable
private fun TopTabs(
    vm: AppViewModel,
    now: Long,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onMore: () -> Unit,
    onExit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 40.dp, end = 40.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppLogo(Modifier.height(40.dp))
        Spacer(Modifier.width(18.dp))
        TabChip("Inicio", active = true) { }
        TabChip("En vivo") { vm.open(Screen.Live) }
        TabChip("Películas") { vm.open(Screen.Movies) }
        TabChip("Series") { vm.open(Screen.SeriesList) }
        TabChip("Guía") { vm.open(Screen.Epg) }
        TabChip("Más") { onMore() }
        Spacer(Modifier.weight(1f))
        AccountChip(vm.account, now)
        Spacer(Modifier.width(10.dp))
        IconAction(onClick = onSearch, size = 34.dp) {
            Icon(Icons.Filled.Search, contentDescription = "Buscar", tint = TextPrimary, modifier = Modifier.size(20.dp))
        }
        IconAction(onClick = onSettings, size = 34.dp) {
            Icon(Icons.Filled.Settings, contentDescription = "Ajustes", tint = TextPrimary, modifier = Modifier.size(20.dp))
        }
        IconAction(onClick = onExit, size = 34.dp) {
            GlyphIcon(Glyph.POWER, Modifier.size(20.dp), TextPrimary)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = formatClock(now),
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic
        )
    }
}

@Composable
private fun TabChip(label: String, active: Boolean = false, onClick: () -> Unit) {
    FocusCard(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        scaleOnFocus = 1.06f,
        background = if (active) AccentColor else Color.Transparent,
        focusedBackground = if (active) AccentColor else PanelFocusedColor
    ) { focused ->
        Text(
            text = label,
            color = if (active || focused) Color.White else TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun AccountChip(account: AccountInfo?, now: Long) {
    val expiresAt = account?.expiresAt
    val daysLeft = expiresAt?.let { (it * 1000 - now) / 86_400_000L }
    val warn = daysLeft != null && daysLeft <= 7
    val text = when {
        expiresAt == null || daysLeft == null -> "Cuenta activa"
        daysLeft <= 0 -> "Tu cuenta venció"
        warn -> "Vence en $daysLeft días"
        else -> "Vence ${formatDateLong(expiresAt * 1000)}"
    }
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(PanelColor)
            .border(1.dp, LineColor, shape)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (warn) Color(0xFFFF6B6B) else Color(0xFF3DDC84))
        )
        Spacer(Modifier.width(6.dp))
        Text(text, color = TextSecondary, fontSize = 9.sp, maxLines = 1)
    }
}

// ------------------------------------------------------------------ canal en vivo grande

@Composable
private fun LiveHero(
    channel: LiveChannel?,
    epg: List<EpgEntry>,
    preview: LivePreview,
    loading: Boolean,
    modifier: Modifier,
    onOpen: () -> Unit
) {
    val nowSecs = System.currentTimeMillis() / 1000
    val current = epg.firstOrNull { it.start <= nowSecs && nowSecs < it.end }
    val next = epg.firstOrNull { it.start >= (current?.end ?: nowSecs) && it !== current }
    FocusCard(
        onClick = onOpen,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        scaleOnFocus = 1.02f,
        background = PanelColor,
        focusedBackground = PanelColor
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
        ) {
            val player = preview.player
            if (player != null && preview.channel?.id == channel?.id) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            isFocusable = false
                        }
                    },
                    update = { view -> view.player = player },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    bitmap = rememberArt(R.drawable.art_live),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (preview.buffering || loading) {
                CircularProgressIndicator(
                    color = LiveColor,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(28.dp)
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(LiveBadgeColor)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("EN VIVO", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (channel == null) "Elige un canal" else "Sigue donde lo dejaste",
                    color = TextPrimary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0x8C000000))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF207060F))))
                    .padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (channel != null) {
                    Text(
                        text = (if (channel.number > 0) "${channel.number} · " else "") + channel.name,
                        color = AccentText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = current?.title ?: channel?.name ?: "TV en vivo",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (current != null) {
                    val span = (current.end - current.start).coerceAtLeast(1)
                    val fraction = ((nowSecs - current.start).toFloat() / span).coerceIn(0f, 1f)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatHm(current.start), color = TextSecondary, fontSize = 9.sp)
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0x2EFFFFFF))
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(fraction.coerceAtLeast(0.02f))
                                    .fillMaxHeight()
                                    .background(LiveColor)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(formatHm(current.end), color = TextSecondary, fontSize = 9.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (next != null) {
                        Text(
                            text = "DESPUÉS",
                            color = TextPrimary,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x1AFFFFFF))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${formatHm(next.start)} · ${next.title}",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Text("OK pantalla completa", color = TextSecondary, fontSize = 9.sp)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ canales recientes

@Composable
private fun RecentChannels(
    channels: List<LiveChannel>,
    title: String,
    selectedId: String?,
    onFocusChannel: (LiveChannel) -> Unit,
    onOpen: (LiveChannel) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.weight(1f)
            )
        }
        channels.take(5).forEach { ch ->
            FocusCard(
                onClick = { onOpen(ch) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) onFocusChannel(ch) },
                shape = RoundedCornerShape(12.dp),
                scaleOnFocus = 1.03f,
                selected = ch.id == selectedId,
                background = PanelColor,
                focusedBackground = PanelFocusedColor
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ChannelLogo(ch.icon, ch.name, 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = ch.name,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (ch.number > 0) {
                            Text("Canal ${ch.number}", color = TextTertiary, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
        FocusCard(
            onClick = onSeeAll,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            scaleOnFocus = 1.03f,
            background = Color.Transparent,
            focusedBackground = PanelFocusedColor
        ) {
            Text(
                text = "Ver todos los canales  ›",
                color = AccentText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
            )
        }
    }
}

// ------------------------------------------------------------------ filas

@Composable
private fun RowTitle(title: String, subtitle: String? = null) {
    Row(
        modifier = Modifier.padding(start = 40.dp, top = 14.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
        if (subtitle != null) {
            Spacer(Modifier.width(10.dp))
            Text(subtitle, color = TextTertiary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ContinueRow(entries: List<ResumeEntry>, onPlay: (ResumeEntry) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        RowTitle("Continuar viendo", "Retoma donde te quedaste")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(entries, key = { it.key }) { entry ->
                FocusCard(
                    onClick = { onPlay(entry) },
                    modifier = Modifier.width(176.dp),
                    shape = RoundedCornerShape(10.dp),
                    scaleOnFocus = 1.05f
                ) {
                    Column {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                        ) {
                            PosterImage(url = entry.image, title = entry.title, modifier = Modifier.fillMaxSize())
                            Text(
                                text = "Continuar ${formatDuration(entry.positionMs / 1000)}",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 6.dp, bottom = 8.dp)
                                    .background(Color(0x99000000), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                            Box(
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .background(Color(0x33FFFFFF))
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(entry.fraction.coerceIn(0.02f, 1f))
                                        .fillMaxHeight()
                                        .background(LiveColor)
                                )
                            }
                        }
                        Text(
                            text = entry.title,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PosterRow(title: String, tiles: List<Tile>) {
    Column(Modifier.fillMaxWidth()) {
        RowTitle(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tiles, key = { it.key }) { tile ->
                PosterCard(
                    title = tile.title,
                    imageUrl = tile.image,
                    rating10 = tile.rating10,
                    favorite = false,
                    onClick = tile.onClick,
                    modifier = Modifier.width(96.dp)
                )
            }
        }
    }
}
