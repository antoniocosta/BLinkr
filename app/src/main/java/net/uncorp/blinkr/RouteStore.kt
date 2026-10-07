package net.uncorp.blinkr

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.routes by preferencesDataStore("routes")
private val DEFAULT = stringPreferencesKey("default")
private fun siteKey(id: String) = stringPreferencesKey("site_$id")

/**
 * Which app opens what: [default] for every site, unless the site has an override.
 * null = ask every time (the system chooser).
 */
data class Routes(val default: String? = null, val overrides: Map<String, String> = emptyMap()) {
    fun target(site: Site?): String? = site?.let { overrides[it.id] } ?: default
}

object RouteStore {
    fun flow(ctx: Context): Flow<Routes> = ctx.applicationContext.routes.data.map { p ->
        Routes(
            default = p[DEFAULT],
            overrides = Sites.ALL.mapNotNull { s -> p[siteKey(s.id)]?.let { s.id to it } }.toMap(),
        )
    }

    suspend fun setDefault(ctx: Context, pkg: String?) {
        ctx.applicationContext.routes.edit { if (pkg == null) it.remove(DEFAULT) else it[DEFAULT] = pkg }
    }

    /** null = back to the default. */
    suspend fun setOverride(ctx: Context, site: Site, pkg: String?) {
        ctx.applicationContext.routes.edit { if (pkg == null) it.remove(siteKey(site.id)) else it[siteKey(site.id)] = pkg }
    }
}
