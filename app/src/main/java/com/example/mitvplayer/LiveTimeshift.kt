package com.example.mitvplayer

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.UriUtil
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

/**
 * Pausa de la TV en vivo.
 *
 * Al pausar un canal, el reproductor suelta su conexión y esta clase sigue bajando el canal a la memoria del aparato
 * en pedacitos, armando una lista HLS local (index.m3u8 + s0.ts, s1.ts…). Al reanudar, el reproductor lee esa lista:
 * se sigue justo donde se pausó y se puede regresar o adelantar dentro de lo guardado. Mientras tanto se sigue
 * guardando lo que va pasando en vivo. Usa una sola conexión con el servidor, igual que ver el canal.
 *
 * Sirve con los dos formatos de En vivo:
 *  - HLS (.m3u8): se copian los pedacitos que da el servidor.
 *  - MPEG-TS (.ts): la transmisión continua se corta en pedacitos de unos 4 a 10 segundos, cada uno empezando en una
 *    imagen completa y con las tablas del canal (PAT/PMT), para poder saltar a cualquiera.
 *
 * Límite: lo que quepa (máximo 1.5 GB y 3 horas, dejando libre espacio del aparato). Al llenarse, la lista se cierra
 * y al terminar de verla se vuelve al vivo.
 */
@OptIn(UnstableApi::class)
class LiveTimeshift(private val dir: File, private val sourceUrl: String, private val userAgent: String) {

    private class Segment(val name: String, val durationSec: Double, val discontinuity: Boolean)

    private val lock = Any()
    private val segments = ArrayList<Segment>()
    private var targetDurationSec = 10
    private var bytes = 0L
    private var maxBytes = 0L

    @Volatile
    private var stopped = false

    @Volatile
    private var call: Call? = null
    private var thread: Thread? = null

    /** Ya no se va a guardar nada más (se llenó, se cortó la conexión o el canal no se puede guardar). */
    @Volatile
    var finished = false
        private set

    /** El canal usa algo que no se puede guardar (video cifrado o en formato fMP4). */
    @Volatile
    var unsupported = false
        private set

    val playlistFile: File get() = File(dir, "index.m3u8")

    /** Segundos guardados hasta ahora. */
    val savedSeconds: Double get() = synchronized(lock) { segments.sumOf { it.durationSec } }

    val segmentCount: Int get() = synchronized(lock) { segments.size }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun start() {
        dir.deleteRecursively()
        dir.mkdirs()
        maxBytes = minOf(MAX_BYTES, dir.usableSpace - RESERVE_BYTES)
        if (maxBytes < MIN_BYTES) {
            // No hay espacio libre en el aparato para guardar el canal.
            unsupported = true
            finished = true
            return
        }
        val t = Thread({ runRecorder() }, "pausa-en-vivo")
        thread = t
        t.start()
    }

    /** Deja de guardar y borra lo guardado. */
    fun stop() {
        stopped = true
        call?.cancel()
        val t = thread
        t?.interrupt()
        Thread({
            try {
                t?.join(5_000)
            } catch (ignored: InterruptedException) {
            }
            dir.deleteRecursively()
        }, "pausa-borrar").start()
    }

    private fun full(): Boolean = bytes >= maxBytes || savedSeconds >= MAX_SECONDS

    private fun runRecorder() {
        try {
            // El reproductor acaba de soltar su conexión: se le da un momento al servidor para que la cierre.
            Thread.sleep(400)
            if (isHls(sourceUrl)) recordHls() else recordTs()
        } catch (ignored: InterruptedException) {
        } catch (ignored: Throwable) {
            // Cualquier problema: se queda lo que ya se guardó.
        } finally {
            synchronized(lock) {
                finished = true
                if (!stopped) writePlaylist()
            }
        }
    }

    private fun isHls(url: String): Boolean = url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)

    // ------------------------------------------------------------------------ HLS

    private fun recordHls() {
        var playlistUrl = sourceUrl
        var lastSequence = -1L
        var lastDiscontinuity = -1
        var pendingDiscontinuity = false
        var failures = 0
        var next = 0
        while (!stopped && !full()) {
            val parsed = try {
                fetchPlaylist(playlistUrl)
            } catch (e: IOException) {
                if (stopped) return
                if (++failures > MAX_FAILURES) return
                Thread.sleep(2_000)
                continue
            }
            failures = 0
            val playlist = parsed.second
            if (playlist is HlsMultivariantPlaylist) {
                // Lista con varias calidades: se guarda la mejor.
                val variant = playlist.variants.maxByOrNull { it.format.bitrate } ?: return
                playlistUrl = variant.url.toString()
                continue
            }
            if (playlist !is HlsMediaPlaylist) return
            if (playlist.segments.any { it.fullSegmentEncryptionKeyUri != null || it.initializationSegment != null }) {
                unsupported = true
                return
            }
            val baseUrl = parsed.first
            val list = playlist.segments
            // La primera vez se empieza unos pedacitos antes del final, donde iba el reproductor al pausar.
            val firstIndex = if (lastSequence < 0) (list.size - START_SEGMENTS).coerceAtLeast(0) else 0
            synchronized(lock) {
                targetDurationSec = maxOf(targetDurationSec, ceil(playlist.targetDurationUs / 1_000_000.0).toInt())
            }
            for (i in firstIndex until list.size) {
                if (stopped || full()) return
                val segment = list[i]
                val sequence = playlist.mediaSequence + i
                if (sequence <= lastSequence) continue
                val discontinuityNumber = playlist.discontinuitySequence + segment.relativeDiscontinuitySequence
                val jumped = lastSequence >= 0 && sequence > lastSequence + 1
                val discontinuity = pendingDiscontinuity || jumped ||
                    (lastDiscontinuity >= 0 && discontinuityNumber != lastDiscontinuity)
                val name = "s$next.ts"
                val ok = downloadSegment(
                    UriUtil.resolve(baseUrl, segment.url),
                    File(dir, name),
                    segment.byteRangeOffset,
                    segment.byteRangeLength
                )
                lastSequence = sequence
                lastDiscontinuity = discontinuityNumber
                if (!ok) {
                    if (stopped) return
                    // Ese pedacito no se pudo bajar: se salta y el siguiente va marcado como corte.
                    pendingDiscontinuity = true
                    continue
                }
                pendingDiscontinuity = false
                addSegment(Segment(name, segment.durationUs / 1_000_000.0, discontinuity && segmentCount > 0))
                next++
            }
            if (playlist.hasEndTag) return
            val waitMs = (playlist.targetDurationUs / 2_000).coerceIn(1_000, 5_000)
            Thread.sleep(waitMs)
        }
    }

    /** Baja la lista y devuelve (dirección final después de redirecciones, lista). */
    private fun fetchPlaylist(url: String): Pair<String, Any> {
        val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
        val c = client.newCall(request)
        call = c
        c.execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val finalUrl = response.request.url.toString()
            val body = response.body ?: throw IOException("Sin respuesta")
            val playlist = HlsPlaylistParser().parse(Uri.parse(finalUrl), body.byteStream())
            return finalUrl to playlist
        }
    }

    private fun downloadSegment(url: String, file: File, offset: Long, length: Long): Boolean {
        repeat(2) {
            if (stopped) return false
            try {
                val builder = Request.Builder().url(url).header("User-Agent", userAgent)
                if (length != C.LENGTH_UNSET.toLong()) builder.header("Range", "bytes=$offset-${offset + length - 1}")
                val c = client.newCall(builder.build())
                call = c
                c.execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val body = response.body ?: throw IOException("Sin respuesta")
                    var written = 0L
                    FileOutputStream(file).use { out ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                out.write(buffer, 0, n)
                                written += n
                            }
                        }
                    }
                    synchronized(lock) { bytes += written }
                    if (written > 0) return true
                }
            } catch (e: IOException) {
                file.delete()
            }
        }
        return false
    }

    // ------------------------------------------------------------------- MPEG-TS

    private fun recordTs() {
        val cutter = TsCutter(
            dir = dir,
            isStopped = { stopped },
            segmentCount = { segmentCount },
            onSegment = { name, duration, discontinuity, size ->
                synchronized(lock) { bytes += size }
                addSegment(Segment(name, duration, discontinuity))
            }
        )
        var reconnects = 0
        try {
            while (!stopped && !full()) {
                val got = try {
                    streamTs(cutter)
                } catch (e: IOException) {
                    false
                }
                if (stopped || full()) return
                if (got) reconnects = 0
                if (++reconnects > MAX_FAILURES) return
                // Se cortó la conexión: se vuelve a pedir el canal y lo que llegue va marcado como corte.
                cutter.breakStream()
                Thread.sleep(1_500)
            }
        } finally {
            cutter.close()
        }
    }

    /** Lee la transmisión hasta que se corte. true = llegó algo. */
    private fun streamTs(cutter: TsCutter): Boolean {
        val request = Request.Builder().url(sourceUrl).header("User-Agent", userAgent).build()
        val c = client.newCall(request)
        call = c
        var got = false
        c.execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val input = response.body?.byteStream() ?: throw IOException("Sin respuesta")
            val buffer = ByteArray(188 * 348)
            while (!stopped && !full()) {
                val n = input.read(buffer)
                if (n < 0) break
                if (n > 0) got = true
                cutter.feed(buffer, n)
            }
        }
        return got
    }

    // ------------------------------------------------------------------- la lista

    private fun addSegment(segment: Segment) {
        synchronized(lock) {
            segments.add(segment)
            if (segment.durationSec > targetDurationSec) targetDurationSec = ceil(segment.durationSec).toInt()
            writePlaylist()
        }
    }

    /** Escribe index.m3u8 de una vez (archivo temporal y cambio de nombre), para que el reproductor nunca lea media lista. */
    private fun writePlaylist() {
        val text = StringBuilder(64 + segments.size * 40)
        text.append("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-PLAYLIST-TYPE:EVENT\n")
        text.append("#EXT-X-TARGETDURATION:").append(targetDurationSec).append('\n')
        text.append("#EXT-X-MEDIA-SEQUENCE:0\n")
        for (s in segments) {
            if (s.discontinuity) text.append("#EXT-X-DISCONTINUITY\n")
            text.append("#EXTINF:").append(String.format(Locale.US, "%.3f", s.durationSec)).append(",\n")
            text.append(s.name).append('\n')
        }
        if (finished) text.append("#EXT-X-ENDLIST\n")
        try {
            val tmp = File(dir, "index.tmp")
            tmp.writeText(text.toString())
            if (!tmp.renameTo(playlistFile)) {
                playlistFile.writeText(text.toString())
                tmp.delete()
            }
        } catch (ignored: IOException) {
        }
    }

    companion object {
        private const val START_SEGMENTS = 3
        private const val MAX_FAILURES = 6
        private const val MAX_BYTES = 1_500L * 1024 * 1024
        private const val MIN_BYTES = 100L * 1024 * 1024
        private const val RESERVE_BYTES = 400L * 1024 * 1024
        private const val MAX_SECONDS = 3 * 3600.0
    }
}

/**
 * Corta una transmisión MPEG-TS en pedacitos de 4 a 10 segundos que empiezan en una imagen completa, cada uno con las
 * tablas del canal (PAT/PMT) al principio, para poder empezar a ver desde cualquiera (pausa de la TV en vivo).
 */
internal class TsCutter(
private val dir: File,
private val isStopped: () -> Boolean,
private val segmentCount: () -> Int,
/** Pedacito terminado: nombre del archivo, segundos, si viene después de un corte, bytes. */
private val onSegment: (name: String, durationSec: Double, discontinuity: Boolean, bytes: Long) -> Unit
) {
    private val packet = ByteArray(PACKET)
    private var filled = 0

    private var pat: ByteArray? = null
    private var pmt: ByteArray? = null
    private var pmtPid = -1
    private var timingPid = -1
    private var timingIsVideo = false
    private var sawKeyframeFlag = false

    private var out: OutputStream? = null
    private var outName = ""
    private var outBytes = 0L
    private var segmentStartPts = -1L
    private var lastPts = -1L
    private var nextIndex = 0
    private var discontinuityNext = false
    private var waitedPackets = 0

    fun feed(data: ByteArray, length: Int) {
        var i = 0
        while (i < length) {
            if (filled == 0) {
                // Se busca el inicio de un paquete (byte 0x47).
                if (data[i] != SYNC) {
                    i++
                    continue
                }
            }
            val take = minOf(PACKET - filled, length - i)
            System.arraycopy(data, i, packet, filled, take)
            filled += take
            i += take
            if (filled == PACKET) {
                filled = 0
                onPacket(packet)
            }
        }
    }

    /** Se cortó la conexión: lo siguiente empieza un pedacito nuevo marcado como corte. */
    fun breakStream() {
        filled = 0
        closeSegment(estimatedDuration())
        discontinuityNext = segmentCount() > 0
        segmentStartPts = -1
        lastPts = -1
    }

    fun close() {
        closeSegment(estimatedDuration())
    }

    private fun estimatedDuration(): Double =
        if (segmentStartPts >= 0 && lastPts >= 0) ptsSeconds(lastPts - segmentStartPts) else 0.0

    private fun onPacket(p: ByteArray) {
        val pid = ((p[1].toInt() and 0x1F) shl 8) or (p[2].toInt() and 0xFF)
        val unitStart = (p[1].toInt() and 0x40) != 0
        val control = (p[3].toInt() shr 4) and 0x3
        val hasAdaptation = (control and 0x2) != 0
        val hasPayload = (control and 0x1) != 0
        var payload = 4
        var keyframe = false
        if (hasAdaptation) {
            val adaptationLength = p[4].toInt() and 0xFF
            if (adaptationLength > 0) keyframe = (p[5].toInt() and 0x40) != 0
            payload = 5 + adaptationLength
        }

        if (pid == 0 && unitStart && hasPayload && payload < PACKET) {
            parsePat(p, payload)
            pat = p.copyOf()
        } else if (pid == pmtPid && pmtPid >= 0 && unitStart && hasPayload && payload < PACKET) {
            parsePmt(p, payload)
            pmt = p.copyOf()
        }

        if (pid == timingPid && keyframe) sawKeyframeFlag = true
        val pts = if (pid == timingPid && unitStart && hasPayload && payload < PACKET - 14) readPts(p, payload) else -1L
        if (pts >= 0) {
            onTimingUnit(pts, keyframe)
            lastPts = pts
        }

        val o = out ?: return
        o.write(p)
        outBytes += PACKET
    }

    private fun onTimingUnit(pts: Long, keyframe: Boolean) {
        if (pat == null || pmt == null) return
        val current = out
        if (current == null) {
            // El primer pedacito empieza en una imagen completa (o tras unos segundos si el canal no las marca).
            waitedPackets++
            if (!timingIsVideo || keyframe || waitedPackets > 400) openSegment(pts)
            return
        }
        val elapsed = ptsSeconds(pts - segmentStartPts)
        when {
            // Salto raro en el tiempo del canal (se reinició la transmisión): pedacito nuevo marcado como corte.
            elapsed < -1.0 || elapsed > 60.0 -> {
                closeSegment(ptsSeconds(lastPts - segmentStartPts).coerceIn(0.1, 60.0))
                discontinuityNext = true
                openSegment(pts)
            }
            elapsed >= MAX_SEGMENT_SEC ||
                (elapsed >= MIN_SEGMENT_SEC && (!timingIsVideo || keyframe || !sawKeyframeFlag)) -> {
                closeSegment(elapsed)
                openSegment(pts)
            }
        }
    }

    private fun openSegment(pts: Long) {
        if (isStopped()) return
        outName = "s$nextIndex.ts"
        nextIndex++
        val o = FileOutputStream(File(dir, outName)).buffered(256 * 1024)
        // Cada pedacito empieza con las tablas del canal, para poder empezar a ver desde ahí.
        pat?.let { o.write(it) }
        pmt?.let { o.write(it) }
        outBytes = 2L * PACKET
        out = o
        segmentStartPts = pts
    }

    private fun closeSegment(durationSec: Double) {
        val o = out ?: return
        out = null
        try {
            o.close()
        } catch (ignored: IOException) {
        }
        if (durationSec <= 0.05 || isStopped()) {
            File(dir, outName).delete()
            return
        }
        val discontinuity = discontinuityNext && segmentCount() > 0
        discontinuityNext = false
        onSegment(outName, durationSec, discontinuity, outBytes)
    }

    private fun parsePat(p: ByteArray, payloadStart: Int) {
        var i = payloadStart + 1 + (p[payloadStart].toInt() and 0xFF) // pointer_field
        if (i + 8 > PACKET || (p[i].toInt() and 0xFF) != 0x00) return
        val sectionLength = ((p[i + 1].toInt() and 0x0F) shl 8) or (p[i + 2].toInt() and 0xFF)
        val end = minOf(i + 3 + sectionLength - 4, PACKET)
        i += 8
        while (i + 4 <= end) {
            val program = ((p[i].toInt() and 0xFF) shl 8) or (p[i + 1].toInt() and 0xFF)
            val pid = ((p[i + 2].toInt() and 0x1F) shl 8) or (p[i + 3].toInt() and 0xFF)
            if (program != 0) {
                pmtPid = pid
                return
            }
            i += 4
        }
    }

    private fun parsePmt(p: ByteArray, payloadStart: Int) {
        var i = payloadStart + 1 + (p[payloadStart].toInt() and 0xFF)
        if (i + 12 > PACKET || (p[i].toInt() and 0xFF) != 0x02) return
        val sectionLength = ((p[i + 1].toInt() and 0x0F) shl 8) or (p[i + 2].toInt() and 0xFF)
        val end = minOf(i + 3 + sectionLength - 4, PACKET)
        val programInfoLength = ((p[i + 10].toInt() and 0x0F) shl 8) or (p[i + 11].toInt() and 0xFF)
        i += 12 + programInfoLength
        var video = -1
        var audio = -1
        while (i + 5 <= end) {
            val type = p[i].toInt() and 0xFF
            val pid = ((p[i + 1].toInt() and 0x1F) shl 8) or (p[i + 2].toInt() and 0xFF)
            val infoLength = ((p[i + 3].toInt() and 0x0F) shl 8) or (p[i + 4].toInt() and 0xFF)
            if (video < 0 && type in VIDEO_TYPES) video = pid
            if (audio < 0 && type in AUDIO_TYPES) audio = pid
            i += 5 + infoLength
        }
        val chosen = if (video >= 0) video else audio
        if (chosen >= 0 && chosen != timingPid) {
            timingPid = chosen
            timingIsVideo = video >= 0
        }
    }

    /** PTS (en unidades de 90 kHz) del inicio de un paquete PES, o -1 si no trae. */
    private fun readPts(p: ByteArray, payloadStart: Int): Long {
        val i = payloadStart
        if (p[i].toInt() != 0 || p[i + 1].toInt() != 0 || p[i + 2].toInt() != 1) return -1
        val flags = p[i + 7].toInt() and 0xFF
        if ((flags and 0x80) == 0) return -1
        val b = i + 9
        return (((p[b].toLong() and 0x0E) shl 29) or
            ((p[b + 1].toLong() and 0xFF) shl 22) or
            ((p[b + 2].toLong() and 0xFE) shl 14) or
            ((p[b + 3].toLong() and 0xFF) shl 7) or
            ((p[b + 4].toLong() and 0xFE) shr 1))
    }

    /** Diferencia de PTS en segundos, contando la vuelta del contador de 33 bits. */
    private fun ptsSeconds(diff: Long): Double {
        var d = diff
        if (d < -(PTS_WRAP / 2)) d += PTS_WRAP
        if (d > PTS_WRAP / 2) d -= PTS_WRAP
        return d / 90_000.0
    }

    private companion object {
        const val PACKET = 188
        const val SYNC = 0x47.toByte()
        const val PTS_WRAP = 1L shl 33
        const val MIN_SEGMENT_SEC = 4.0
        const val MAX_SEGMENT_SEC = 10.0

        /** Tipos de video en la tabla del canal: MPEG-2, H.264, H.265, MPEG-4 y el MPEG-2 de los canales ATSC. */
        val VIDEO_TYPES = setOf(0x01, 0x02, 0x10, 0x1B, 0x24, 0x80)

        /** Tipos de audio: MPEG, AAC, AAC-LATM, AC3 y E-AC3 (ATSC). */
        val AUDIO_TYPES = setOf(0x03, 0x04, 0x0F, 0x11, 0x81, 0x87)
    }
}
