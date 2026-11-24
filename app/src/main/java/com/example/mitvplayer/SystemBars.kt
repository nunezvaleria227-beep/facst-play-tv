package com.example.mitvplayer

import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Pantalla completa de verdad: sin la barra de arriba (hora, íconos) ni los botones de Android de abajo.
 * En el Fire TV no existen, pero algunas cajitas y teles Android sí las muestran encima de la app.
 * Si alguien las saca deslizando el dedo, se vuelven a esconder solas.
 */
fun Activity.hideSystemBars() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowCompat.getInsetsController(window, window.decorView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.systemBars())
    }
}
