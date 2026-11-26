package com.example.mitvplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.mitvplayer.ui.AccountScreen
import com.example.mitvplayer.ui.AppViewModel
import com.example.mitvplayer.ui.ContinueScreen
import com.example.mitvplayer.ui.CatchUpChannelsScreen
import com.example.mitvplayer.ui.CatchUpProgramsScreen
import com.example.mitvplayer.ui.CatchUpScreen
import com.example.mitvplayer.ui.EpgCategoriesScreen
import com.example.mitvplayer.ui.EpgChannelsScreen
import com.example.mitvplayer.ui.EpgGridScreen
import com.example.mitvplayer.ui.HomeScreen
import com.example.mitvplayer.ui.KidsCategoriesScreen
import com.example.mitvplayer.ui.LiveScreen
import com.example.mitvplayer.ui.LoginScreen
import com.example.mitvplayer.ui.MiTvTheme
import com.example.mitvplayer.ui.MovieDetailScreen
import com.example.mitvplayer.ui.MoviesScreen
import com.example.mitvplayer.ui.SeriesScreen
import com.example.mitvplayer.ui.MultiScreen
import com.example.mitvplayer.ui.NotificationsScreen
import com.example.mitvplayer.ui.SeeAllScreen
import com.example.mitvplayer.ui.RecordingsScreen
import com.example.mitvplayer.ui.Screen
import com.example.mitvplayer.ui.qaName
import com.example.mitvplayer.ui.SearchScope
import com.example.mitvplayer.ui.SearchScreen
import com.example.mitvplayer.ui.SeriesDetailScreen
import com.example.mitvplayer.ui.SettingsScreen
import com.example.mitvplayer.data.Kind

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    // Controles universales, de la tele, teclados y mandos: sus teclas se cambian por las del control del Fire TV.
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean =
        super.dispatchKeyEvent(RemoteKeys.translate(event))

    // Mandos que mandan la cruceta como palanca: se convierte en flechas.
    override fun dispatchGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        if (RemoteKeys.hatToKeys(event) { dispatchKeyEvent(it) }) return true
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onResume() {
        super.onResume()
        // El reproductor detectó que la cuenta se acabó (por ejemplo, la demostración): a escribir el usuario nuevo.
        com.example.mitvplayer.data.AccountWatch.endedMessage?.let { message ->
            com.example.mitvplayer.data.AccountWatch.endedMessage = null
            viewModel.endSession(message)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Al volver de otra app o de una ventana del sistema, las barras se vuelven a esconder.
        if (hasFocus) hideSystemBars()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (Config.QA_BUILD) qaOpen(intent)
    }

    private fun qaOpen(intent: android.content.Intent?) {
        MainActivityQa(viewModel, intent?.getStringExtra("qa_open"))
    }

    /**
     * Si la app se cerró sola la vez anterior, se le avisa al dueño al celular en segundo plano (FailureReport).
     * El cliente no ve nada ni tiene que mandar fotos. Si no hay internet, se intenta la próxima vez que se abra.
     */
    private fun reportLastCrash() {
        val app = applicationContext
        val details = CrashLog.read(app) ?: return
        Thread({
            val user = try {
                (app as MiTvApp).repository.savedCredentials()?.username
            } catch (e: Exception) {
                null
            }
            if (FailureReport.crashBlocking(app, details, user)) CrashLog.clear(app)
        }, "aviso-cierre").start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        if (Config.QA_BUILD) qaOpen(intent)
        reportLastCrash()
        setContent {
            MiTvTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot(viewModel)
                    com.example.mitvplayer.ui.TvKeyboardHost()
                }
            }
        }
    }
}

/** Solo en la compilación de prueba: abre una pantalla pedida con "am start --es qa_open Movies". */
private fun MainActivityQa(vm: AppViewModel, name: String?) {
    val screen = when (name) {
        "Live" -> Screen.Live
        "Movies" -> Screen.Movies
        "SeriesList" -> Screen.SeriesList
        "Settings" -> Screen.Settings
        "Search" -> Screen.Search(SearchScope.ALL)
        else -> null
    } ?: return
    vm.qaOpen(screen)
}

@Composable
private fun AppRoot(vm: AppViewModel) {
    // Atrás en cualquier pantalla abierta regresa a la anterior; en el inicio o el login cierra la app.
    BackHandler(enabled = vm.stack.size > 1) { vm.back() }

    val current = vm.stack.last()
    if (Config.QA_BUILD) {
        androidx.compose.runtime.LaunchedEffect(current) {
            androidx.compose.runtime.withFrameNanos { }
            QaProbe.shown(current.qaName())
        }
    }
    when (val top = current) {
        Screen.Login -> LoginScreen(vm)
        Screen.Home -> HomeScreen(vm)
        Screen.Live -> LiveScreen(vm, vm.live, title = "TV en vivo")
        Screen.Radio -> LiveScreen(vm, vm.radio, title = "Radio", radio = true)
        Screen.Movies -> MoviesScreen(vm)
        Screen.SeriesList -> SeriesScreen(vm)
        is Screen.Grid -> if (top.kind == Kind.SERIES) {
            SeeAllScreen(
                vm = vm,
                state = vm.series,
                kind = Kind.SERIES,
                noun = "series",
                posterOf = { it.cover },
                onOpen = { vm.openSeries(it) }
            )
        } else {
            SeeAllScreen(
                vm = vm,
                state = vm.movies,
                kind = Kind.MOVIES,
                noun = "películas",
                posterOf = { it.poster },
                onOpen = { vm.openMovie(it) }
            )
        }
        is Screen.MovieDetails -> MovieDetailScreen(vm, top.movie)
        is Screen.SeriesDetails -> SeriesDetailScreen(vm, top.series)
        Screen.Epg -> EpgCategoriesScreen(vm)
        is Screen.EpgChannels -> EpgChannelsScreen(vm, top.categoryId, top.title)
        is Screen.EpgGrid -> EpgGridScreen(vm, top.categoryId, top.title)
        Screen.CatchUp -> CatchUpScreen(vm)
        is Screen.CatchUpChannels -> CatchUpChannelsScreen(vm, top.categoryId, top.title)
        is Screen.CatchUpPrograms -> CatchUpProgramsScreen(vm, top.channel)
        Screen.Multi -> MultiScreen(vm)
        is Screen.Search -> SearchScreen(vm, top.scope)
        Screen.Account -> AccountScreen(vm)
        Screen.Notifications -> NotificationsScreen(vm)
        Screen.Settings -> SettingsScreen(vm)
        Screen.Recordings -> RecordingsScreen(vm)
        Screen.Continue -> ContinueScreen(vm)
        Screen.KidsCategories -> KidsCategoriesScreen(vm)
    }
}
