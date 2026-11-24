package com.example.mitvplayer.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.mitvplayer.R
import com.example.mitvplayer.data.LiveChannel
import com.example.mitvplayer.data.Movie
import com.example.mitvplayer.data.SeriesItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ============================================================================ medidas

/** Ancho del menú lateral cerrado (solo íconos) y abierto (con nombres). */
val NAV_COLLAPSED: Dp = 64.dp
val NAV_EXPANDED: Dp = 200.dp

/** Margen izquierdo del contenido (deja libre el menú cerrado). */
val CONTENT_START: Dp = 84.dp

/** Alto de la portada grande de arriba (Inicio, Películas, Series) en una tele normal (540 dp de alto). */
val HERO_HEIGHT: Dp = 286.dp

/** Lo que necesita una fila completa de pósters debajo de la portada. */
private val ROW_SPACE: Dp = 216.dp
private val HERO_MIN_HEIGHT: Dp = 200.dp

/** Alto de la portada para esta pantalla: si la pantalla es más bajita, se achica para que quepa una fila entera. */
fun heroHeightFor(screenHeight: Dp): Dp = (screenHeight - ROW_SPACE).coerceIn(HERO_MIN_HEIGHT, HERO_HEIGHT)

/** Intenta poner el foco; false si ese elemento no está en pantalla. */
fun FocusRequester.tryFocus(): Boolean =
    try {
        requestFocus()
        true
    } catch (e: IllegalStateException) {
        false
    }

// ============================================================================ menú lateral

enum class NavSection(val label: String) {
    SEARCH("Buscar"),
    HOME("Inicio"),
    LIVE("En vivo"),
    MOVIES("Películas"),
    SERIES("Series"),
    GUIDE("Guía"),
    PROFILE("Perfil"),
    MORE("Más"),
    SETTINGS("Ajustes")
}

/** Ventanas que se abren desde el menú lateral. */
private sealed interface NavDialog {
    data object More : NavDialog
    data object Profile : NavDialog
    data object Exit : NavDialog
    data class Pin(val message: String, val then: () -> Unit) : NavDialog
}

/**
 * Pantalla principal con el menú lateral encima, a la izquierda (el contenido va debajo, de orilla a orilla).
 *
 * Desde cualquier lugar, ◀ entra al menú (cae en la sección actual) y el menú se abre mostrando los nombres;
 * ▶ regresa exactamente a donde se estaba ([onBackToContent]). Elegir una sección cambia de pantalla al momento,
 * sin ir apilando pantallas: Atrás desde cualquier sección vuelve al Inicio.
 */
@Composable
fun NavScaffold(
    vm: AppViewModel,
    current: NavSection,
    onBackToContent: () -> Boolean,
    exitOnBack: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    var dialog by remember { mutableStateOf<NavDialog?>(null) }
    if (exitOnBack) {
        androidx.activity.compose.BackHandler { dialog = NavDialog.Exit }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        Box(Modifier.fillMaxSize(), content = content)
        SideNav(
            vm = vm,
            current = current,
            onBackToContent = onBackToContent,
            onDialog = { dialog = it }
        )
    }
    NavDialogs(vm, dialog) { dialog = it }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SideNav(
    vm: AppViewModel,
    current: NavSection,
    onBackToContent: () -> Boolean,
    onDialog: (NavDialog) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (expanded) NAV_EXPANDED else NAV_COLLAPSED, label = "menu")
    val currentItem = remember { FocusRequester() }
    val kids = vm.kids.active

    fun go(section: NavSection) {
        when (section) {
            NavSection.SEARCH -> vm.open(Screen.Search(SearchScope.ALL))
            NavSection.HOME -> vm.go(Screen.Home)
            NavSection.LIVE -> vm.go(Screen.Live)
            NavSection.MOVIES -> vm.go(Screen.Movies)
            NavSection.SERIES -> vm.go(Screen.SeriesList)
            NavSection.GUIDE -> vm.go(Screen.Epg)
            NavSection.PROFILE -> onDialog(NavDialog.Profile)
            NavSection.MORE -> onDialog(NavDialog.More)
            // Con el perfil Niños puesto, Ajustes pide el PIN (ahí se cambia el control parental).
            NavSection.SETTINGS -> if (kids) {
                onDialog(NavDialog.Pin("Para abrir Ajustes.") { vm.open(Screen.Settings) })
            } else {
                vm.open(Screen.Settings)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(width)
            .background(
                Brush.horizontalGradient(
                    if (expanded) {
                        listOf(Color(0xFA06050E), Color(0xF206050E), Color(0xE606050E))
                    } else {
                        listOf(Color(0xE606050E), Color(0x9906050E), Color(0x0006050E))
                    }
                )
            )
            .onFocusChanged { expanded = it.hasFocus }
            // Al menú solo se entra con ◀ (y cae en la sección en la que se está); ▲ ▼ desde la pantalla no lo abren.
            .focusProperties { enter = { if (it == FocusDirection.Left) currentItem else FocusRequester.Cancel } }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) onBackToContent() else false
            }
            .focusGroup()
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(start = 2.dp, bottom = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (expanded) {
                AppLogo(Modifier.height(34.dp))
            } else {
                Image(
                    bitmap = rememberArt(R.drawable.ic_launcher),
                    contentDescription = null,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(9.dp))
                )
            }
        }
        val sections = NavSection.entries.filter { it != NavSection.SETTINGS }
        sections.forEach { section ->
            NavItem(
                section = section,
                label = if (section == NavSection.PROFILE && kids) "Niños" else section.label,
                active = section == current || (section == NavSection.PROFILE && kids),
                expanded = expanded,
                requester = if (section == current) currentItem else null,
                onClick = { go(section) }
            )
        }
        Spacer(Modifier.weight(1f))
        NavItem(
            section = NavSection.SETTINGS,
            label = NavSection.SETTINGS.label,
            active = current == NavSection.SETTINGS,
            expanded = expanded,
            requester = if (current == NavSection.SETTINGS) currentItem else null,
            onClick = { go(NavSection.SETTINGS) }
        )
    }
}

@Composable
private fun NavItem(
    section: NavSection,
    label: String,
    active: Boolean,
    expanded: Boolean,
    requester: FocusRequester?,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val tint = when {
        focused -> Color.White
        active -> AccentText
        else -> TextTertiary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(
                when {
                    focused -> AccentColor
                    active -> Color(0x26FFFFFF)
                    else -> Color.Transparent
                },
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            when (section) {
                NavSection.SEARCH -> Icon(Icons.Filled.Search, null, tint = tint, modifier = Modifier.size(21.dp))
                NavSection.HOME -> Icon(Icons.Filled.Home, null, tint = tint, modifier = Modifier.size(21.dp))
                NavSection.LIVE -> GlyphIcon(Glyph.TV, Modifier.size(21.dp), tint)
                NavSection.MOVIES -> GlyphIcon(Glyph.CLAPPER, Modifier.size(21.dp), tint)
                NavSection.SERIES -> GlyphIcon(Glyph.SERIES, Modifier.size(21.dp), tint)
                NavSection.GUIDE -> GlyphIcon(Glyph.EPG, Modifier.size(21.dp), tint)
                NavSection.PROFILE -> Icon(Icons.Filled.Face, null, tint = tint, modifier = Modifier.size(21.dp))
                NavSection.MORE -> GlyphIcon(Glyph.GRID, Modifier.size(21.dp), tint)
                NavSection.SETTINGS -> Icon(Icons.Filled.Settings, null, tint = tint, modifier = Modifier.size(21.dp))
            }
        }
        if (expanded) {
            Spacer(Modifier.width(14.dp))
            Text(
                text = label,
                color = tint,
                fontSize = 14.sp,
                fontWeight = if (active || focused) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun NavDialogs(vm: AppViewModel, dialog: NavDialog?, onDialog: (NavDialog?) -> Unit) {
    val context = LocalContext.current
    when (dialog) {
        null -> Unit
        NavDialog.More -> OptionsDialog(
            title = "Más opciones",
            options = listOf(
                Opt("Continuar viendo") { vm.open(Screen.Continue) },
                Opt("Pantalla múltiple") { vm.open(Screen.Multi) },
                Opt("Catch up (programas que ya pasaron)") { vm.open(Screen.CatchUp) },
                Opt("Radio") { vm.open(Screen.Radio) },
                Opt("Grabaciones") { vm.open(Screen.Recordings) },
                Opt("Avisos") { vm.open(Screen.Notifications) },
                Opt("Mi cuenta") { vm.open(Screen.Account) },
                Opt("Salir de la aplicación") { onDialog(NavDialog.Exit) }
            ),
            onDismiss = { onDialog(null) }
        )
        NavDialog.Exit -> OptionsDialog(
            title = "¿Qué quieres hacer?",
            options = listOf(
                Opt("Salir de la aplicación") { context.findActivity()?.finish() },
                Opt("Cerrar sesión") { vm.logout() },
                Opt("Cancelar") { }
            ),
            onDismiss = { onDialog(null) }
        )
        NavDialog.Profile -> when {
            vm.kids.active -> OptionsDialog(
                title = "Perfil Niños",
                message = "Solo se ven las categorías infantiles. Para salir hace falta el PIN.",
                options = listOf(
                    Opt("Salir del perfil Niños (PIN)") {
                        onDialog(NavDialog.Pin("Para salir del perfil Niños.") { vm.setKidsMode(false) })
                    },
                    Opt("Cancelar") { }
                ),
                onDismiss = { onDialog(null) }
            )
            !vm.parental.hasPin -> OptionsDialog(
                title = "Perfil Niños",
                message = "Muestra solo las categorías infantiles (caricaturas, Disney, niños, familia…). " +
                    "Primero pon un PIN en Ajustes → Control parental, para que los niños no puedan salir solos.",
                options = listOf(
                    Opt("Ir a Ajustes") { vm.open(Screen.Settings) },
                    Opt("Cancelar") { }
                ),
                onDismiss = { onDialog(null) }
            )
            else -> OptionsDialog(
                title = "Perfil",
                message = "En el perfil Niños solo se ven las categorías infantiles. Para salir hará falta el PIN.",
                options = listOf(
                    Opt("Cambiar a perfil Niños") { vm.setKidsMode(true) },
                    Opt("Seguir en el perfil principal") { }
                ),
                onDismiss = { onDialog(null) }
            )
        }
        is NavDialog.Pin -> PinDialog(
            title = "Escribe tu PIN",
            message = dialog.message,
            onSubmit = { pin ->
                if (vm.parental.verify(pin)) {
                    onDialog(null)
                    dialog.then()
                    null
                } else {
                    val wait = vm.parental.waitSeconds()
                    if (wait > 0) "Demasiados intentos. Espera $wait s." else "PIN incorrecto"
                }
            },
            onDismiss = { onDialog(null) }
        )
    }
}

// ============================================================================ portada grande

enum class HeroKind(val badge: String) {
    MOVIE("PELÍCULA"),
    SERIES("SERIE"),
    LIVE("EN VIVO"),
    RESUME("CONTINUAR")
}

/** Lo que muestra la portada grande: lo que está enfocado (o lo destacado al entrar). */
class HeroItem(
    val key: String,
    val kind: HeroKind,
    val title: String,
    val image: String?,
    val rating10: Float = 0f,
    val subtitle: String? = null,
    val progress: Float? = null,
    val movie: Movie? = null,
    val series: SeriesItem? = null,
    val channel: LiveChannel? = null,
    /** ▶ Ver ahora. */
    val onPlay: () -> Unit,
    /** ⓘ Más información (null = no hay ficha). */
    val onInfo: (() -> Unit)? = null
)

/** Lo que se baja aparte para la portada: imagen de fondo, de qué trata y datos (año, género, duración…). */
class HeroDetails(val backdrop: String?, val plot: String?, val meta: String)

/**
 * Datos de la portada: al momento si ya se bajaron antes; si no, se piden cuando el foco se queda un rato
 * en el título (así recorrer una fila rápido no hace una consulta por cada póster).
 */
@Composable
fun rememberHeroDetails(vm: AppViewModel, hero: HeroItem?): HeroDetails? {
    val state = produceState<HeroDetails?>(hero?.key?.let { vm.cachedHeroDetails(it) }, hero?.key) {
        val h = hero ?: return@produceState
        val cached = vm.cachedHeroDetails(h.key)
        if (cached != null) {
            value = cached
            return@produceState
        }
        value = null
        delay(350)
        value = vm.loadHeroDetails(h)
    }
    return state.value
}

/** Imagen de fondo de la portada (a la derecha, de orilla a orilla) con degradados para que se lea el texto. */
@Composable
fun BoxScope.HeroBackground(hero: HeroItem?, details: HeroDetails?, height: Dp = HERO_HEIGHT + 90.dp) {
    val image = details?.backdrop ?: hero?.image
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .fillMaxWidth(0.74f)
            .height(height)
    ) {
        Crossfade(targetState = if (hero?.kind == HeroKind.LIVE) null else image, label = "portada") { url ->
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
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
        }
        if (hero?.kind == HeroKind.LIVE && hero.image != null) {
            AsyncImage(
                model = hero.image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(start = 120.dp)
                    .size(150.dp)
            )
        }
        // Degradados: se funde con el fondo a la izquierda y abajo.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to BackgroundColor,
                        0.30f to BackgroundColor.copy(alpha = 0.80f),
                        0.62f to BackgroundColor.copy(alpha = 0.15f),
                        1f to Color.Transparent
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to BackgroundColor.copy(alpha = 0.25f),
                        1f to BackgroundColor
                    )
                )
        )
    }
}

/**
 * Texto de la portada: tipo, título, datos, de qué trata, avance y botones. Los botones quedan siempre abajo,
 * a la misma altura, cambie lo que cambie el texto (así el control no "brinca").
 */
@Composable
fun HeroText(
    hero: HeroItem?,
    details: HeroDetails?,
    modifier: Modifier = Modifier,
    placeholder: String = "Facst Play TV",
    buttons: (@Composable RowScope.() -> Unit)? = null
) {
    var titleLines by remember { mutableIntStateOf(1) }
    Column(
        modifier = modifier.padding(start = CONTENT_START, end = 40.dp, top = 24.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        if (hero != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = hero.kind.badge,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (hero.kind == HeroKind.LIVE) LiveBadgeColor else AccentColor)
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                )
                if (hero.rating10 >= 0.5f) {
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.Filled.Star, null, tint = StarColor, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f", hero.rating10),
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Text(
            text = hero?.title ?: placeholder,
            color = TextPrimary,
            fontSize = 30.sp,
            lineHeight = 33.sp,
            fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { titleLines = it.lineCount },
            modifier = Modifier.fillMaxWidth(0.58f)
        )
        val meta = listOfNotNull(hero?.subtitle, details?.meta?.takeIf { it.isNotBlank() }).joinToString("   ·   ")
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                color = AccentText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.6f)
            )
        }
        val plot = details?.plot
        if (!plot.isNullOrBlank()) {
            Text(
                text = plot,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                // Con el título en dos renglones, la sinopsis se acorta para que no tape los botones.
                maxLines = if (titleLines > 1) 2 else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.5f)
            )
        }
        Spacer(Modifier.weight(1f))
        val progress = hero?.progress
        if (progress != null && progress > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(0.3f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x33FFFFFF))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                        .fillMaxHeight()
                        .background(LiveColor)
                )
            }
        }
        if (buttons != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), content = buttons)
        }
    }
}

/** Botón de la portada: blanco el principal (▶), translúcidos los demás. */
@Composable
fun HeroButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    compact: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(
                when {
                    focused -> Color.White
                    primary -> AccentColor
                    else -> Color(0x33FFFFFF)
                },
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 13.dp else 18.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (focused) Color.Black else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

// ============================================================================ filas

enum class TileStyle { POSTER, WIDE, CHANNEL }

/** Una tarjeta de una fila (se arma solo cuando se ve en pantalla). */
class RowEntry(
    val title: String,
    val image: String?,
    val hero: HeroItem,
    val onClick: () -> Unit,
    val rating10: Float = 0f,
    val progress: Float? = null,
    val caption: String? = null,
    val favorite: Boolean = false,
    val onLongClick: (() -> Unit)? = null
)

/** Una fila de la pantalla: título y tarjetas. [entryAt] arma cada tarjeta solo cuando hace falta. */
class MediaRow(
    val key: String,
    val title: String,
    val style: TileStyle,
    val size: Int,
    val keyAt: (Int) -> Any,
    val entryAt: (Int) -> RowEntry,
    /** "Ver todo" al final de la fila (null = no hay). */
    val onSeeAll: (() -> Unit)? = null
)

/**
 * Dónde estaba el foco en una pantalla con filas (se guarda en el ViewModel): al volver de una ficha o del
 * reproductor, o al regresar del menú lateral, el foco vuelve a la misma tarjeta, con las filas donde estaban.
 */
class RowsMemory {
    val listState = LazyListState()
    private val rowStates = HashMap<String, LazyListState>()
    var row by mutableStateOf<String?>(null)
        private set
    var column by mutableStateOf(0)
        private set
    var hero: HeroItem? = null
    val requester = FocusRequester()

    fun rowState(key: String): LazyListState = rowStates.getOrPut(key) { LazyListState() }

    fun focused(rowKey: String, col: Int) {
        row = rowKey
        column = col
    }

    fun isTarget(rowKey: String, col: Int): Boolean = row == rowKey && column == col

    /** Empezar desde arriba (al entrar a la sección desde el menú lateral). */
    fun reset() {
        row = null
        column = 0
        hero = null
        rowStates.clear()
        listState.requestScrollToItem(0)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MediaRows(
    rows: List<MediaRow>,
    memory: RowsMemory,
    onFocusEntry: (RowEntry) -> Unit,
    modifier: Modifier = Modifier,
    /** A dónde va ▲ desde la primera fila (el botón principal de la portada). */
    upFromFirstRow: FocusRequester? = null
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    LazyColumn(
        state = memory.listState,
        modifier = modifier.onPreviewKeyEvent { event ->
            // ▲ con la fila de arriba fuera de la pantalla (la fila enfocada siempre sube hasta arriba): primero se
            // muestra esa fila y luego se mueve el foco, así ▲ nunca brinca a los botones de la portada.
            if (event.type != KeyEventType.KeyDown || event.key != Key.DirectionUp) return@onPreviewKeyEvent false
            val current = rows.indexOfFirst { it.key == memory.row }
            if (current <= 0 || memory.listState.firstVisibleItemIndex < current) return@onPreviewKeyEvent false
            scope.launch {
                memory.listState.scrollToItem(current - 1)
                withFrameNanos { }
                focusManager.moveFocus(FocusDirection.Up)
            }
            true
        },
        // Espacio al final para que cualquier fila pueda subir hasta arriba.
        contentPadding = PaddingValues(bottom = 300.dp)
    ) {
        itemsIndexed(rows, key = { _, r -> r.key }) { rowIndex, row ->
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = row.title,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = CONTENT_START, top = 4.dp)
                )
                LazyRow(
                    state = memory.rowState(row.key),
                    contentPadding = PaddingValues(start = CONTENT_START, end = 60.dp, top = 8.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        // Al volver a esta fila (▲ ▼), el foco cae en la tarjeta donde se estaba.
                        .focusRestorer()
                ) {
                    val seeAll = row.onSeeAll
                    val up = if (rowIndex == 0) upFromFirstRow else null
                    items(row.size, key = row.keyAt) { col ->
                        val entry = remember(row, col) { row.entryAt(col) }
                        val target = memory.isTarget(row.key, col)
                        val last = seeAll == null && col == row.size - 1
                        MediaTile(
                            entry = entry,
                            style = row.style,
                            modifier = Modifier
                                // Al final de la fila, ▶ no hace nada (no salta a los botones de arriba).
                                .tileEdges(last = last, up = up)
                                .then(if (target) Modifier.focusRequester(memory.requester) else Modifier)
                                .onFocusChanged { state ->
                                    if (state.isFocused) {
                                        val newRow = memory.row != row.key
                                        memory.focused(row.key, col)
                                        memory.hero = entry.hero
                                        onFocusEntry(entry)
                                        // La fila con el foco sube hasta arriba (como en las apps de streaming). Se espera un
                                        // cuadro: así este movimiento va después del que hace la lista para mostrar la tarjeta
                                        // (si no, ese gana y la fila se queda a medias).
                                        if (newRow) {
                                            scope.launch {
                                                withFrameNanos { }
                                                memory.listState.animateScrollToItem(rowIndex)
                                            }
                                        }
                                    }
                                }
                        )
                    }
                    if (seeAll != null) {
                        item(key = "__ver_todo") {
                            SeeAllTile(row.style, seeAll, Modifier.tileEdges(last = true, up = up))
                        }
                    }
                }
            }
        }
    }
}

/** Bordes de una tarjeta: ▶ en la última no se sale de la fila; ▲ en la primera fila va a [up]. */
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.tileEdges(last: Boolean, up: FocusRequester?): Modifier =
    if (!last && up == null) {
        this
    } else {
        focusProperties {
            if (last) right = FocusRequester.Cancel
            if (up != null) this.up = up
        }
    }

@Composable
private fun MediaTile(entry: RowEntry, style: TileStyle, modifier: Modifier) {
    when (style) {
        TileStyle.POSTER -> PosterCard(
            title = entry.title,
            imageUrl = entry.image,
            rating10 = entry.rating10,
            favorite = entry.favorite,
            onClick = entry.onClick,
            onLongClick = entry.onLongClick,
            modifier = modifier.width(110.dp)
        )
        TileStyle.WIDE -> FocusCard(
            onClick = entry.onClick,
            onLongClick = entry.onLongClick,
            modifier = modifier.width(206.dp),
            shape = RoundedCornerShape(10.dp),
            scaleOnFocus = 1.06f
        ) {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                ) {
                    PosterImage(url = entry.image, title = entry.title, modifier = Modifier.fillMaxSize())
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                    )
                    entry.caption?.let {
                        Text(
                            text = it,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 8.dp, bottom = 9.dp)
                        )
                    }
                    val progress = entry.progress
                    if (progress != null) {
                        Box(
                            Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(Color(0x40FFFFFF))
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                                    .fillMaxHeight()
                                    .background(LiveColor)
                            )
                        }
                    }
                }
                Text(
                    text = entry.title,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                )
            }
        }
        TileStyle.CHANNEL -> FocusCard(
            onClick = entry.onClick,
            onLongClick = entry.onLongClick,
            modifier = modifier.width(176.dp),
            shape = RoundedCornerShape(12.dp),
            scaleOnFocus = 1.06f,
            background = PanelColor,
            focusedBackground = PanelFocusedColor
        ) {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(Brush.linearGradient(listOf(Color(0xFF231D3E), Color(0xFF0E0C1C)))),
                    contentAlignment = Alignment.Center
                ) {
                    if (entry.image != null) {
                        AsyncImage(
                            model = entry.image,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        )
                    } else {
                        Text(entry.title.take(2).uppercase(), color = TextSecondary, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF4D6D))
                        )
                        entry.caption?.let {
                            Spacer(Modifier.width(5.dp))
                            Text(it, color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (entry.favorite) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = "Favorito",
                            tint = StarColor,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(5.dp)
                                .size(14.dp)
                        )
                    }
                }
                Text(
                    text = entry.title,
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/** Última tarjeta de una fila: abre la lista completa. */
@Composable
private fun SeeAllTile(style: TileStyle, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val width = when (style) {
        TileStyle.POSTER -> 110.dp
        TileStyle.WIDE -> 206.dp
        TileStyle.CHANNEL -> 176.dp
    }
    FocusCard(
        onClick = onClick,
        modifier = modifier
            .width(width)
            .then(if (style == TileStyle.POSTER) Modifier.aspectRatio(2f / 3f) else Modifier.height(112.dp)),
        shape = RoundedCornerShape(10.dp),
        scaleOnFocus = 1.06f,
        background = Color(0x1AFFFFFF),
        focusedBackground = AccentColor
    ) { focused ->
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            GlyphIcon(Glyph.GRID, Modifier.size(26.dp), if (focused) Color.White else AccentText)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Ver todo",
                color = if (focused) Color.White else TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}
