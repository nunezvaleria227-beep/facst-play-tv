package com.example.mitvplayer

import android.content.Context
import android.os.Build
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Avisos de fallas al celular del dueño de la app (sin que el cliente tenga que hacer nada).
 *
 * Cuando la app se cierra sola o un canal / película no abre después de reintentar, se manda un aviso a ntfy.sh
 * (servicio gratuito de notificaciones). El dueño instala la app "ntfy" en su celular y se suscribe al tema [TOPIC]:
 * le llega cada falla con la app, la versión, el aparato, el usuario y qué se estaba viendo.
 *
 * Nunca se manda la contraseña ni la dirección completa del video (lleva usuario y contraseña): solo el servidor.
 * Para no llenar el celular de avisos: la misma falla se avisa una vez cada 2 horas por aparato, y como mucho
 * 20 avisos al día por aparato.
 */
object FailureReport {
    /** Tema de ntfy al que se suscribe el dueño. Quien lo sepa puede leer los avisos: no compartirlo. */
    const val TOPIC = "redmaxplus-fallas-5k9g5lmgh3"
    private const val URL = "https://ntfy.sh/"
    private const val SAME_FAILURE_MS = 2 * 60 * 60 * 1000L
    private const val MAX_PER_DAY = 20

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** No se avisa desde las compilaciones de prueba ni de depuración. */
    private fun enabled(context: Context): Boolean =
        !Config.QA_BUILD && (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0

    /**
     * La app se cerró sola la vez anterior ([details] = lo que guardó CrashLog). Bloquea: llamarla fuera de la
     * pantalla. true = el aviso salió (ya se puede borrar el registro).
     */
    fun crashBlocking(context: Context, details: String, user: String?): Boolean {
        if (!enabled(context)) return true
        return sendBlocking(
            context,
            key = "cierre:" + details.lineSequence().drop(1).firstOrNull().orEmpty().take(80),
            title = "Facst Play TV se cerró sola",
            lines = listOf("Usuario: ${user ?: "—"}") + details.lines(),
            priority = 4,
            tags = listOf("rotating_light")
        )
    }

    /** Un canal, película o episodio no abrió (después de los reintentos). Se manda en segundo plano. */
    fun playback(context: Context, what: String, kind: String, detail: String, server: String?, user: String?) {
        if (!enabled(context)) return
        val app = context.applicationContext
        Thread({
            sendBlocking(
                app,
                key = "video:$kind:$what:$detail",
                title = "Facst Play TV · no abrió: $what",
                lines = listOf(
                    "Tipo: $kind",
                    "Error: $detail",
                    "Servidor: ${server ?: "—"}",
                    "Usuario: ${user ?: "—"}"
                ),
                priority = 3,
                tags = listOf("warning")
            )
        }, "aviso-falla").start()
    }

    private fun sendBlocking(
        context: Context,
        key: String,
        title: String,
        lines: List<String>,
        priority: Int,
        tags: List<String>
    ): Boolean {
        val prefs = context.getSharedPreferences("avisos_fallas", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(now))
        val sentToday = if (prefs.getString("dia", null) == today) prefs.getInt("cuantos", 0) else 0
        if (sentToday >= MAX_PER_DAY) return false
        val lastSame = prefs.getLong("k:" + key.hashCode(), 0L)
        if (now - lastSame < SAME_FAILURE_MS) return true

        val message = (lines + device(context)).joinToString("\n")
        val json = JSONObject()
            .put("topic", TOPIC)
            .put("title", title.take(120))
            .put("message", message.take(3_500))
            .put("priority", priority)
            .put("tags", JSONArray(tags))
        return try {
            val request = Request.Builder()
                .url(URL)
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
            }
            prefs.edit()
                .putString("dia", today)
                .putInt("cuantos", sentToday + 1)
                .putLong("k:" + key.hashCode(), now)
                .apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun device(context: Context): List<String> {
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }
        return listOf(
            "App: Facst Play TV $version",
            "Aparato: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
        )
    }
}
