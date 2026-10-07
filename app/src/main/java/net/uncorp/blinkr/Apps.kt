package net.uncorp.blinkr

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi

data class App(val pkg: String, val label: String)

/** Finds the apps that can open links, and BLinkr's own link approval state. */
object Apps {
    /** Every browser: apps that open any website (asked with a made-up domain). */
    fun browsers(ctx: Context): List<App> = resolve(ctx, "https://blinkr.invalid/")

    /** Apps made for this site (e.g. the TikTok app): they open its links but aren't browsers. */
    fun siteApps(ctx: Context, site: Site): List<App> {
        val browsers = browsers(ctx).map { it.pkg }.toSet()
        return site.hosts.map(Sites::sampleUrl).flatMap { resolve(ctx, it) }
            .distinctBy { it.pkg }.filter { it.pkg !in browsers }
    }

    fun label(ctx: Context, pkg: String): String? = runCatching {
        val pm = ctx.packageManager
        pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
    }.getOrNull()

    fun icon(ctx: Context, pkg: String): Drawable? = runCatching { ctx.packageManager.getApplicationIcon(pkg) }.getOrNull()

    fun installed(ctx: Context, pkg: String) = label(ctx, pkg) != null

    /**
     * Another app that gets this site's links before BLinkr does, or null if they reach BLinkr
     * (or nobody has claimed them yet). That happens when the site's own app is installed and
     * verified for the domain with "Open supported links" on: Android sends links straight to it.
     */
    fun takenBy(ctx: Context, site: Site): String? {
        val pm = ctx.packageManager
        return site.hosts.map(Sites::sampleUrl).firstNotNullOfOrNull { url ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
            val pkg = if (Build.VERSION.SDK_INT >= 33) {
                pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
            } else {
                @Suppress("DEPRECATION") pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }?.activityInfo?.packageName
            // "android" = the chooser: nobody owns it yet
            pkg?.takeIf { it != ctx.packageName && it != "android" }
        }
    }

    /** That app's "Open by default" screen, where its "Open supported links" can be turned off. */
    fun openByDefaultSettings(pkg: String): Intent =
        if (Build.VERSION.SDK_INT >= 31) Intent(android.provider.Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Uri.parse("package:$pkg"))
        else Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))

    private fun resolve(ctx: Context, url: String): List<App> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        val found: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
        } else {
            @Suppress("DEPRECATION") pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }
        return found.map { it.activityInfo.packageName }.distinct()
            .filter { it != ctx.packageName }
            .map { App(it, label(ctx, it) ?: it) }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * Per host: has the user approved BLinkr to open it (Settings → Open by default)?
     * Empty before Android 12, where there's nothing to approve (Android asks on first use).
     */
    @RequiresApi(31)
    fun approvedHosts(ctx: Context): Map<String, Boolean> {
        val state = ctx.getSystemService(DomainVerificationManager::class.java)
            .getDomainVerificationUserState(ctx.packageName) ?: return emptyMap()
        return state.hostToStateMap.mapValues { (_, s) ->
            state.isLinkHandlingAllowed && s != DomainVerificationUserState.DOMAIN_STATE_NONE
        }
    }
}
