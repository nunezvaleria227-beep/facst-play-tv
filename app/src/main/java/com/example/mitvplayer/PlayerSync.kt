package com.example.mitvplayer

import android.content.Context
import com.example.mitvplayer.data.LiveChannel
import java.io.File

/** El elemento del reproductor para un canal en vivo (con su número, para cambiar de canal tecleándolo). */
fun liveItemFor(channel: LiveChannel, url: String): PlayItem = PlayItem(
    title = if (channel.number > 0) "${channel.number}  ${channel.name}" else channel.name,
    subtitle = null,
    url = url,
    isLive = true,
    streamId = channel.id,
    number = channel.number
)

/**
 * Sincronizar audio y subtítulos: lo que se ajustó en cada canal, película o episodio se guarda y se usa
 * solo la próxima vez que se abra.
 */
object PlayerSync {
    private fun prefs(context: Context) = context.getSharedPreferences("sincronizar", Context.MODE_PRIVATE)

    /** Clave de lo que se está viendo: el canal, la película / episodio, o el archivo. */
    fun keyOf(item: PlayItem): String =
        item.streamId?.let { "live:$it" }
            ?: item.resume?.let { "${it.type}:${it.id}" }
            ?: item.url.substringBefore('?').substringAfterLast('/')

    /** Milisegundos que se retrasa el audio (negativo = se adelanta). */
    fun audioMs(context: Context, item: PlayItem): Int = prefs(context).getInt("a:" + keyOf(item), 0)

    fun setAudioMs(context: Context, item: PlayItem, ms: Int) {
        val edit = prefs(context).edit()
        if (ms == 0) edit.remove("a:" + keyOf(item)) else edit.putInt("a:" + keyOf(item), ms)
        edit.apply()
    }

    /** Milisegundos que se retrasan los subtítulos de internet (negativo = salen antes). */
    fun subtitleMs(context: Context, cacheKey: String): Int = prefs(context).getInt("s:$cacheKey", 0)

    fun setSubtitleMs(context: Context, cacheKey: String, ms: Int) {
        val edit = prefs(context).edit()
        if (ms == 0) edit.remove("s:$cacheKey") else edit.putInt("s:$cacheKey", ms)
        edit.apply()
    }

    /** Copia del subtítulo (.srt) con todos los tiempos corridos [offsetMs]. */
    fun shiftedSubtitle(context: Context, original: File, offsetMs: Int): File {
        val dir = File(context.cacheDir, "subs-sincronizados").also { it.mkdirs() }
        val out = File(dir, "${original.nameWithoutExtension}.$offsetMs.srt")
        if (out.exists() && out.lastModified() >= original.lastModified()) return out
        return try {
            out.writeText(shiftSrt(original.readText(Charsets.UTF_8), offsetMs), Charsets.UTF_8)
            out
        } catch (e: Exception) {
            original
        }
    }

    private val SRT_TIME = Regex("""(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})""")

    fun shiftSrt(text: String, offsetMs: Int): String =
        text.lineSequence().joinToString("\n") { line ->
            if (!line.contains("-->")) {
                line
            } else {
                SRT_TIME.replace(line) { match ->
                    val (h, m, s, ms) = match.destructured
                    val total = (((h.toLong() * 60 + m.toLong()) * 60 + s.toLong()) * 1000 + ms.padEnd(3, '0').toLong() + offsetMs)
                        .coerceAtLeast(0L)
                    "%02d:%02d:%02d,%03d".format(total / 3_600_000, total / 60_000 % 60, total / 1000 % 60, total % 1000)
                }
            }
        }
}
