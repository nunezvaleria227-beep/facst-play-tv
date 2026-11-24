package com.example.mitvplayer.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.mitvplayer.Config
import com.example.mitvplayer.R
import com.example.mitvplayer.RecordingManager
import com.example.mitvplayer.data.EpgEntry
import com.example.mitvplayer.data.Kind
import com.example.mitvplayer.data.LiveChannel
import kotlinx.coroutines.delay

/** Reproductor pequeño de la vista previa de En vivo. Solo existe mientras se está viendo un canal. */
@androidx.annotation.OptIn(UnstableApi::class)
class LivePreview(private val context: Context) {
    var player by mutableStateOf<ExoPlayer?>(null)
        private set
    var channel by mutableStateOf<LiveChannel?>(null)
        private set
    var failed by mutableStateOf(false)
        private set
    var buffering by mutableStateOf(false)
        private set

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            // El audio no se pudo leer con el decodificador del aparato: se reintenta leyéndolo con FFmpeg.
            if (com.example.mitvplayer.AudioSupport.switchToSoftware(error)) {
                player?.let {
                    it.seekToDefaultPosition()
                    it.prepare()
                }
                return
            }
            failed = true
            buffering = false
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            buffering = playbackState == Player.STATE_BUFFERING
            if (playbackState == Player.STATE_READY) failed = false
        }
    }

    fun play(channel: LiveChannel, url: String) {
        release()
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Config.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val exo = ExoPlayer.Builder(context, com.example.mitvplayer.AudioSupport.renderersFactory(context))
            .setLoadControl(com.example.mitvplayer.FastStart.loadControl())
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
        exo.addListener(listener)
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
        exo.playWhenReady = true
        player = exo
        this.channel = channel
        failed = false
    }

    fun stop() {
        release()
        channel = null
    }

    /** Libera la conexión pero deja el canal elegido (por ejemplo, mientras se graba con una sola conexión). */
    fun pauseKeepingChannel() {
        release()
    }

    private fun release() {
        player?.let {
            it.removeListener(listener)
            it.release()
        }
        player = null
        buffering = false
    }
}

/** Reloj y fecha de la barra superior. */
@Composable
fun ClockText(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(20_000)
        }
    }
    Text(
        text = "${formatClock(now)}    ${formatDateLong(now)}",
        color = TextPrimary,
        fontSize = 13.sp,
        maxLines = 1,
        modifier = modifier
    )
}


private fun emptyMessage(state: BrowseState<LiveChannel>, radio: Boolean): String = when {
    radio && state.catalog != null && state.rows.size <= 3 ->
        "Tu servidor no tiene categorías de radio.\n(Se buscan categorías cuyo nombre diga \"radio\".)"
    state.selectedId == ROW_FAV ->
        "Aún no tienes canales favoritos.\nMantén pulsado un canal, o pulsa la tecla Menú del control, para añadirlo."
    state.selectedId == ROW_RECENT -> "Todavía no has visto ningún canal."
    else -> "No hay canales en esta categoría"
}

/** Tiempo que el foco se queda en un canal antes de verlo en la vista previa (recorrer rápido no abre canales). */
private const val PREVIEW_DELAY_MS = 900L

/** Teclas de números del control (y del teclado numérico) para ir directo a un canal. */
private val ChannelDigits = mapOf(
    Key.Zero to '0', Key.One to '1', Key.Two to '2', Key.Three to '3', Key.Four to '4',
    Key.Five to '5', Key.Six to '6', Key.Seven to '7', Key.Eight to '8', Key.Nine to '9',
    Key.NumPad0 to '0', Key.NumPad1 to '1', Key.NumPad2 to '2', Key.NumPad3 to '3', Key.NumPad4 to '4',
    Key.NumPad5 to '5', Key.NumPad6 to '6', Key.NumPad7 to '7', Key.NumPad8 to '8', Key.NumPad9 to '9'
)

/** Espera antes de pedir la guía de una fila (si se sigue bajando, no se pide). */
private const val GUIDE_ROW_DELAY_MS = 250L

/**
 * TV en vivo, formato nuevo:
 *  - arriba, la vista previa grande y lo que está pasando en el canal (con su avance y lo que sigue);
 *  - en medio, las categorías en una fila (se cambian con ◀ ▶, sin pulsar OK);
 *  - abajo, la guía de canales: número, logo, nombre, programa actual con su barra de avance y el siguiente.
 * Al quedarse un momento en un canal se ve solo en la vista previa; OK lo abre en pantalla completa.
 */
@Composable
fun LiveScreen(vm: AppViewModel, state: BrowseState<LiveChannel>, title: String, radio: Boolean = false) {
    val context = LocalContext.current
    val preview = remember { LivePreview(context) }
    val channelRequester = remember { FocusRequester() }
    val chipRequester = remember { FocusRequester() }
    var focusedId by remember { mutableStateOf(vm.liveFocusId) }
    var resumeTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { state.ensureLoaded() }

    // La vista previa se corta al salir de la pantalla o al abrir otra app (así no se ocupan dos conexiones)
    // y vuelve sola al regresar.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> preview.stop()
                Lifecycle.Event.ON_START -> resumeTick++
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            preview.stop()
        }
    }

    val channels = state.items
    val catalog = state.catalog
    val focused: LiveChannel? = remember(focusedId, catalog) { focusedId?.let { catalog?.byId?.get(it) } }
    // El canal que recibe el foco al entrar o al volver del menú: el último enfocado si está en la lista.
    val targetId = focusedId?.takeIf { id -> channels.any { it.id == id } } ?: channels.firstOrNull()?.id

    LaunchedEffect(focusedId, resumeTick) {
        val channel = focused ?: return@LaunchedEffect
        if (preview.channel?.id == channel.id && preview.player != null) return@LaunchedEffect
        delay(PREVIEW_DELAY_MS)
        if (RecordingManager.current == null) preview.play(channel, vm.liveUrl(channel))
    }

    LaunchedEffect(catalog != null) {
        if (catalog == null) return@LaunchedEffect
        withFrameNanos { }
        com.example.mitvplayer.QaProbe.ready(title, "${channels.size} canales, ${state.rows.size} filas")
        val index = channels.indexOfFirst { it.id == targetId }
        if (index > 2) state.itemsListState.scrollToItem(index - 2)
        withFrameNanos { }
        withFrameNanos { }
        if (!channelRequester.tryFocus()) chipRequester.tryFocus()
    }

    // Números del control: se teclea el número del canal y se va directo a él (aunque esté en otra categoría).
    var typed by remember { mutableStateOf("") }
    var jumpNote by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(typed) {
        if (typed.isEmpty()) return@LaunchedEffect
        delay(1300)
        val number = typed.toIntOrNull()
        typed = ""
        if (number == null || number <= 0) return@LaunchedEffect
        var list = state.items
        var index = list.indexOfFirst { it.number == number }
        if (index < 0 && state.itemsFor(ROW_ALL).any { it.number == number }) {
            state.select(ROW_ALL)
            withFrameNanos { }
            list = state.items
            index = list.indexOfFirst { it.number == number }
        }
        if (index < 0) {
            jumpNote = "No hay canal $number"
            delay(1800)
            jumpNote = null
            return@LaunchedEffect
        }
        val channel = list[index]
        focusedId = channel.id
        vm.liveFocusId = channel.id
        state.itemsListState.scrollToItem((index - 2).coerceAtLeast(0))
        withFrameNanos { }
        withFrameNanos { }
        channelRequester.tryFocus()
    }

    fun openFull(channel: LiveChannel) {
        preview.stop()
        val list = state.items
        val index = list.indexOfFirst { it.id == channel.id }
        if (index >= 0) vm.playLive(context, list, index) else vm.playLive(context, listOf(channel), 0)
    }

    NavScaffold(
        vm = vm,
        current = if (radio) NavSection.MORE else NavSection.LIVE,
        onBackToContent = { channelRequester.tryFocus() || chipRequester.tryFocus() }
    ) {
        Image(
            bitmap = rememberArt(R.drawable.art_live),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xE607060F), Color(0xF707060F))))
        )
        Column(
            Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    val digit = ChannelDigits[event.key] ?: return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyDown && typed.length < 5) {
                        typed += digit
                        jumpNote = null
                    }
                    true
                }
                .padding(start = CONTENT_START, end = 24.dp, top = 16.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(236.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .aspectRatio(16f / 9f)
                ) {
                    LivePreviewBox(
                        preview = preview,
                        channel = focused,
                        modifier = Modifier.fillMaxSize()
                    )
                    val note = if (typed.isNotEmpty()) typed else jumpNote
                    if (note != null) {
                        Text(
                            text = note,
                            color = Color.White,
                            fontSize = if (typed.isNotEmpty()) 34.sp else 14.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .background(Color(0xCC000000), RoundedCornerShape(8.dp))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
                Spacer(Modifier.width(20.dp))
                LiveInfoPanel(
                    vm = vm,
                    channel = focused,
                    title = title,
                    onFullscreen = { focused?.let { openFull(it) } },
                    onGuide = { vm.open(Screen.EpgGrid(state.selectedId, state.selectedName)) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
            CategoryChips(
                state = state,
                requester = chipRequester,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 8.dp)
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                SectionStatus(state, emptyMessage(state, radio)) {
                    LazyColumn(
                        state = state.itemsListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp, end = 8.dp)
                    ) {
                        itemsIndexed(channels, key = { _, c -> c.id }) { _, channel ->
                            GuideRow(
                                vm = vm,
                                channel = channel,
                                playing = preview.channel?.id == channel.id,
                                favorite = vm.isFavorite(Kind.LIVE, channel.id),
                                modifier = Modifier
                                    .then(if (channel.id == targetId) Modifier.focusRequester(channelRequester) else Modifier)
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            focusedId = channel.id
                                            vm.liveFocusId = channel.id
                                        }
                                    },
                                onClick = { openFull(channel) },
                                onLongClick = { vm.toggleFavorite(Kind.LIVE, channel.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Vista previa grande: el video del canal (sin controles) o su logo mientras carga. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun LivePreviewBox(preview: LivePreview, channel: LiveChannel?, modifier: Modifier) {
    val player = preview.player
    val showing = player != null && preview.channel?.id == channel?.id
    val recording = RecordingManager.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF231D3E), Color(0xFF0B0A16)))),
        contentAlignment = Alignment.Center
    ) {
        if (channel?.icon != null) {
            coil.compose.AsyncImage(
                model = channel.icon,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(110.dp)
            )
        }
        if (showing) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        isFocusable = false
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                },
                update = { view -> view.player = player },
                modifier = Modifier.fillMaxSize()
            )
        }
        when {
            showing && preview.failed -> Text(
                "No se pudo ver este canal.\nPulsa OK para abrirlo en pantalla completa.",
                color = TextPrimary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(Color(0xB3000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
            channel == null -> Text(
                "Elige un canal",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            recording != null && player == null -> Text(
                "Grabando ${recording.channelName}…\nLa vista previa está en pausa.",
                color = TextPrimary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            !showing || preview.buffering -> CircularProgressIndicator(
                color = LiveColor,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .size(22.dp),
                strokeWidth = 2.dp
            )
        }
        if (channel != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
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
        }
    }
}

/** Lo que está pasando en el canal enfocado y botones (pantalla completa, favorito, guía completa). */
@Composable
private fun LiveInfoPanel(
    vm: AppViewModel,
    channel: LiveChannel?,
    title: String,
    onFullscreen: () -> Unit,
    onGuide: () -> Unit,
    modifier: Modifier
) {
    val epg by produceState<List<EpgEntry>?>(null, channel?.id) {
        value = channel?.let { vm.epgFor(it.id) }
    }
    var tick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            tick = System.currentTimeMillis()
        }
    }
    val nowSec = tick / 1000
    val list = epg.orEmpty().filter { !it.placeholder }
    val current = list.firstOrNull { nowSec >= it.start && nowSec < it.end }
    val next = list.firstOrNull { it.start >= (current?.end ?: nowSec) && it !== current }
    val recording = RecordingManager.current
    var favTick by remember { mutableIntStateOf(0) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title.uppercase(),
                color = AccentText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.weight(1f))
            ClockText()
        }
        Text(
            text = channel?.let { (if (it.number > 0) "${it.number}  " else "") + it.name } ?: "Elige un canal",
            color = TextPrimary,
            fontSize = 24.sp,
            lineHeight = 27.sp,
            fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (current != null) {
            Text(
                text = current.title,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val span = (current.end - current.start).coerceAtLeast(1)
            val fraction = ((nowSec - current.start).toFloat() / span).coerceIn(0f, 1f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatHm(current.start), color = TextSecondary, fontSize = 10.sp)
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
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
                Text(formatHm(current.end), color = TextSecondary, fontSize = 10.sp)
            }
            if (current.description.isNotBlank()) {
                Text(
                    text = current.description,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else if (channel != null) {
            Text(
                text = if (epg == null) "Cargando la guía…" else "Este canal no tiene guía de programas.",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
        if (next != null) {
            Text(
                text = "Después  ${formatHm(next.start)}   ${next.title}",
                color = TextTertiary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (channel != null) {
                HeroButton("▶  Pantalla completa", onFullscreen, primary = true)
                val favorite = remember(favTick, channel.id) { vm.isFavorite(Kind.LIVE, channel.id) }
                HeroButton(if (favorite) "★  Favorito" else "☆  Favorito", {
                    vm.toggleFavorite(Kind.LIVE, channel.id)
                    favTick++
                })
            }
            HeroButton("▦  Guía completa", onGuide)
            if (recording != null) {
                HeroButton("■  Detener grabación", { RecordingManager.stop() })
            }
        }
    }
}

/** Categorías en una fila: con ◀ ▶ se cambia de categoría al momento (sin pulsar OK). */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CategoryChips(state: BrowseState<LiveChannel>, requester: FocusRequester, modifier: Modifier) {
    val rows = state.rows
    val selectedIndex = rows.indexOfFirst { it.id == state.selectedId }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    var pending by remember { mutableStateOf<String?>(null) }
    // Se espera un instante: recorrer varias categorías seguidas no recalcula la lista en cada una.
    LaunchedEffect(pending) {
        val id = pending ?: return@LaunchedEffect
        delay(220)
        if (id != state.selectedId) state.select(id)
    }
    LazyRow(
        state = listState,
        modifier = modifier.focusRestorer(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 40.dp)
    ) {
        itemsIndexed(rows, key = { _, r -> r.id }) { index, row ->
            var focusedChip by remember { mutableStateOf(false) }
            val selected = row.id == state.selectedId
            val shape = RoundedCornerShape(50)
            Row(
                modifier = Modifier
                    .then(if (index == selectedIndex) Modifier.focusRequester(requester) else Modifier)
                    .onFocusChanged {
                        focusedChip = it.isFocused
                        if (it.isFocused) pending = row.id
                    }
                    .clip(shape)
                    .background(
                        when {
                            focusedChip -> Color.White
                            selected -> AccentColor
                            else -> Color(0x1FFFFFFF)
                        },
                        shape
                    )
                    .clickable { state.select(row.id) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = row.name,
                    color = if (focusedChip) Color.Black else Color.White,
                    fontSize = 12.sp,
                    fontWeight = if (selected || focusedChip) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = row.count.toString(),
                    color = if (focusedChip) Color(0xFF555555) else TextTertiary,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
    }
}

/** Una fila de la guía: número, logo, nombre, programa actual con su avance y el siguiente. */
@Composable
private fun GuideRow(
    vm: AppViewModel,
    channel: LiveChannel,
    playing: Boolean,
    favorite: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val epg by produceState<List<EpgEntry>?>(null, channel.id) {
        // Bajar rápido por la lista no pide la guía de cada canal que solo pasa por la pantalla.
        delay(GUIDE_ROW_DELAY_MS)
        value = vm.epgFor(channel.id)
    }
    val nowSec = System.currentTimeMillis() / 1000
    val list = epg?.filter { !it.placeholder }
    val current = list?.firstOrNull { nowSec >= it.start && nowSec < it.end }
    val next = list?.firstOrNull { it.start >= (current?.end ?: nowSec) && it !== current }
    val progress = current?.let { ((nowSec - it.start).toFloat() / (it.end - it.start).coerceAtLeast(1)).coerceIn(0f, 1f) }

    FocusCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(10.dp),
        scaleOnFocus = 1.015f,
        selected = playing,
        background = Color(0x12FFFFFF),
        focusedBackground = PanelFocusedColor
    ) { focused ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (channel.number > 0) channel.number.toString() else "",
                color = if (focused) Color.White else TextTertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.width(38.dp)
            )
            ChannelLogo(icon = channel.icon, name = channel.name, size = 38.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(0.30f)) {
                Text(
                    text = channel.name,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (playing) {
                    Text("● En la vista previa", color = LiveColor, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(0.42f)) {
                Text(
                    text = current?.title ?: if (epg == null) "" else "En vivo",
                    color = if (focused) Color.White else TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0x22FFFFFF))
                ) {
                    if (progress != null && progress > 0f) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress)
                                .fillMaxHeight()
                                .background(if (focused) LiveColor else AccentColor)
                        )
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text = next?.let { "${formatHm(it.start)}  ${it.title}" }.orEmpty(),
                color = TextTertiary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(0.28f)
            )
            if (favorite) {
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.Star, contentDescription = "Favorito", tint = StarColor, modifier = Modifier.size(15.dp))
            }
        }
    }
}
