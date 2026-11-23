package com.example.mitvplayer.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Un aviso para los clientes (mantenimiento, promoción, renovación…). */
data class AppNotice(
    val id: String,
    val title: String,
    val message: String,
    /** true = se muestra cada vez que se abre la app; false = una sola vez por aparato. */
    val always: Boolean
)

/**
 * Avisos del dueño de la app a sus clientes.
 *
 * Se leen del archivo docs/avisos.json del repositorio de GitHub de Facst Play TV (cambiarlo ahí no publica una versión
 * nueva de la app). Al abrir la app se muestra el primer aviso vigente que el cliente aún no ha visto, y todos los
 * vigentes aparecen en Más → Avisos.
 *
 * Formato:
 * {"avisos": [{"id": "mantenimiento-20-nov", "titulo": "Mantenimiento", "mensaje": "El jueves de 2 a 4 am…",
 *              "desde": "2026-11-19", "hasta": "2026-11-21", "siempre": false}]}
 * "desde", "hasta" (fechas incluidas) y "siempre" son opcionales.
 */
object AppNotices {
    private const val URL = "https://raw.githubusercontent.com/nunezvaleria227-beep/facst-play-tv/main/docs/avisos.json"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** Avisos vigentes hoy (lista vacía si no hay o no hay internet). */
    suspend fun fetch(): List<AppNotice> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(URL).header("Cache-Control", "no-cache").build()
            val text = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                response.body?.string().orEmpty()
            }
            parse(text)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun parse(text: String): List<AppNotice> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val array = JSONObject(text).optJSONArray("avisos") ?: return emptyList()
        val out = ArrayList<AppNotice>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val message = o.optString("mensaje").trim()
            if (message.isEmpty()) continue
            val from = o.optString("desde").trim()
            val until = o.optString("hasta").trim()
            if (from.isNotEmpty() && today < from) continue
            if (until.isNotEmpty() && today > until) continue
            out += AppNotice(
                id = o.optString("id").trim().ifEmpty { message.hashCode().toString() },
                title = o.optString("titulo").trim().ifEmpty { "Aviso" },
                message = message,
                always = o.optBoolean("siempre", false)
            )
        }
        return out
    }

    fun seen(context: Context, id: String): Boolean =
        prefs(context).getStringSet(KEY_SEEN, emptySet()).orEmpty().contains(id)

    fun markSeen(context: Context, id: String) {
        val prefs = prefs(context)
        val seen = prefs.getStringSet(KEY_SEEN, emptySet()).orEmpty().toMutableSet()
        seen += id
        prefs.edit().putStringSet(KEY_SEEN, seen).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences("avisos_app", Context.MODE_PRIVATE)

    private const val KEY_SEEN = "vistos"
}
