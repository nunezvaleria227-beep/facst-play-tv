package com.example.mitvplayer.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.runtime.remember
import com.example.mitvplayer.R

val BackgroundColor = Color(0xFF07060F)
val BackgroundGlow = Color(0xFF13111F)
val SidebarColor = Color(0xFF0D0B18)
val PanelColor = Color(0xFF13111F)
val PanelFocusedColor = Color(0xFF231D3E)
val RowSelectedColor = Color(0xFF1C1930)
val AccentColor = Color(0xFF8B3DF0)
val OnAccentColor = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFFF3F1FA)
val TextSecondary = Color(0xFFB4AECB)
val TextTertiary = Color(0xFF8E88A8)
val AccentText = Color(0xFFC792FF)
val FocusRingColor = Color(0xFFFFFFFF)
val LiveColor = Color(0xFF38D0FF)
val LiveBadgeColor = Color(0xFF0E7FA8)
val LineColor = Color(0xFF2C2846)
val ErrorColor = Color(0xFFFF6B6B)
val StarColor = Color(0xFFFFC93C)

@Composable
fun MiTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AccentColor,
            onPrimary = OnAccentColor,
            background = BackgroundColor,
            onBackground = TextPrimary,
            surface = PanelColor,
            onSurface = TextPrimary,
            surfaceVariant = PanelFocusedColor,
            onSurfaceVariant = TextSecondary,
            error = ErrorColor
        ),
        content = content
    )
}

private val artCache = java.util.concurrent.ConcurrentHashMap<Int, ImageBitmap>()

/**
 * Deja listos los fondos y logos al abrir la app, en segundo plano: así la primera vez que se entra a
 * cada sección no hay que esperar a que se lean las imágenes.
 */
fun preloadArt(resources: android.content.res.Resources, ids: List<Int>) {
    for (id in ids) {
        try {
            if (!artCache.containsKey(id)) artCache.putIfAbsent(id, ImageBitmap.imageResource(resources, id))
        } catch (e: Throwable) {
            // Si una no se puede leer, se intentará cuando se use.
        }
    }
}

/** Imagen de res/drawable decodificada una sola vez (las pantallas cambian seguido y así no se vuelve a leer). */
@Composable
fun rememberArt(@DrawableRes id: Int): ImageBitmap {
    val resources = LocalContext.current.applicationContext.resources
    return remember(id) { artCache.getOrPut(id) { ImageBitmap.imageResource(resources, id) } }
}

/**
 * Fondo común a todas las pantallas: una imagen (por defecto el fondo con las ondas de luz de Facst Play TV)
 * oscurecida un poco, [dim] de 0 a 1, para que el texto se lea bien encima.
 */
@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    @DrawableRes art: Int = R.drawable.bg_home,
    dim: Float = 0.30f,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        Image(
            bitmap = rememberArt(art),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            SidebarColor.copy(alpha = dim * 0.7f),
                            SidebarColor.copy(alpha = dim)
                        )
                    )
                )
        )
        content()
    }
}
