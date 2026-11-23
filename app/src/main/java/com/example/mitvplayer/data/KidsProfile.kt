package com.example.mitvplayer.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Perfil Niños.
 *
 * Con el perfil puesto, en En vivo, Películas, Series, el buscador, la guía y el inicio solo se ve lo de las
 * categorías infantiles (caricaturas, Disney, Nick, niños, familia…). Se queda puesto aunque se cierre la app, y para
 * salir hace falta el PIN del control parental (así los niños no pueden salir solos).
 *
 * Las categorías infantiles se reconocen solas por el nombre; desde Ajustes se pueden agregar o quitar a mano.
 */
class KidsProfile(context: Context) {
    private val prefs = context.getSharedPreferences("perfil_ninos", Context.MODE_PRIVATE)

    /** El perfil Niños está puesto. */
    var active by mutableStateOf(prefs.getBoolean(KEY_ACTIVE, false))
        private set

    /** Cambios hechos a mano: "LIVE:12" → true (se ve en el perfil) o false (no se ve). */
    private var overrides by mutableStateOf(load())

    fun turn(on: Boolean) {
        active = on
        prefs.edit().putBoolean(KEY_ACTIVE, on).apply()
    }

    /** ¿Esta categoría se ve en el perfil Niños? (lee estado de Compose: las listas se actualizan solas). */
    fun allows(kind: Kind, category: Category): Boolean {
        if (isAdultCategoryName(category.name)) return false
        overrides["${kind.name}:${category.id}"]?.let { return it }
        return isKidsCategoryName(category.name)
    }

    fun setAllowed(kind: Kind, category: Category, allowed: Boolean) {
        val key = "${kind.name}:${category.id}"
        val next = HashMap(overrides)
        if (allowed == isKidsCategoryName(category.name)) next.remove(key) else next[key] = allowed
        overrides = next
        prefs.edit().putStringSet(KEY_OVERRIDES, next.map { (k, v) -> "$k=${if (v) 1 else 0}" }.toSet()).apply()
    }

    private fun load(): Map<String, Boolean> =
        prefs.getStringSet(KEY_OVERRIDES, emptySet()).orEmpty().mapNotNull { entry ->
            val key = entry.substringBeforeLast('=', "")
            if (key.isEmpty()) null else key to entry.endsWith("=1")
        }.toMap()

    private companion object {
        const val KEY_ACTIVE = "activo"
        const val KEY_OVERRIDES = "cambios"
    }
}

/** Palabras de las categorías infantiles (en minúsculas y sin acentos). */
private val KIDS_WORDS = listOf(
    "infantil", "ninos", "ninas", "nino ", "kids", "kid ", "children", "child ", "cartoon", "caricatura",
    "dibujo", "animad", "animacion", "disney", "nick", "junior", " jr ", "baby", "bebe", "pixar", "dreamworks",
    "boomerang", "toon", "familia", "family", "peque", "chiquit", "discovery k", "pbs k", "jardin", "escolar"
)

/** ¿El nombre parece de una categoría para niños? (nunca las de adultos). */
fun isKidsCategoryName(name: String): Boolean {
    if (isAdultCategoryName(name)) return false
    val key = " " + searchKey(name).replace(Regex("[^a-z0-9]+"), " ").trim() + " "
    return KIDS_WORDS.any { key.contains(it) }
}
