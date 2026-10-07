package net.uncorp.blinkr

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch


/**
 * The whole app: one app for all sites, optional per-site overrides, and a banner
 * while some of BLinkr's links still need approving in the system settings.
 */
/** Widths of the arrow and app columns in the site list (fixed, so they line up with the headers). */
private val ARROW = 20.dp
private val APP_COLUMN = 128.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
                setContent {
            AppTheme(this) {
                Scaffold(containerColor = Paper) { padding -> Screen(Modifier.padding(padding)) }
            }
        }
    }

    /** What the picker dialog is choosing for: the default (site = null) or one site. */
    private data class Picking(val site: Site?)

    @Composable
    private fun Screen(modifier: Modifier) {
        val scope = rememberCoroutineScope()
        val routes by remember { RouteStore.flow(this) }.collectAsState(initial = Routes())
        var picking by remember { mutableStateOf<Picking?>(null) }
        // Re-read installed apps and link approvals whenever we come back (e.g. from Settings)
        var tick by remember { mutableIntStateOf(0) }
        LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
        val browsers = remember(tick) { Apps.browsers(this) }
        val approved = remember(tick) { if (Build.VERSION.SDK_INT >= 31) Apps.approvedHosts(this) else null }
        // Sites whose own app (not a browser: that's just "not approved yet") gets the links first
        val taken = remember(tick, browsers) {
            val browserPkgs = browsers.map { it.pkg }.toSet()
            Sites.ALL.mapNotNull { s -> Apps.takenBy(this, s)?.takeIf { it !in browserPkgs }?.let { s.id to it } }.toMap()
        }

        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            approved?.let { ApprovalBanner(it) }

            HowItWorks()
            HorizontalDivider(color = Line)

            Text("Open all sites with", color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 12.dp))
            Row(
                Modifier.fillMaxWidth().clickable { picking = Picking(null) }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppIcon(routes.default)
                Column(Modifier.weight(1f)) {
                    Text(routes.default?.let { Apps.label(this@MainActivity, it) } ?: "Ask every time",
                        color = Blue, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(if (routes.default != null) "Default" else "Default: Android asks which app to use", color = Muted, fontSize = 12.sp)
                }
            }
            HorizontalDivider(color = Line)

            Text("Per site", color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 12.dp))
            Text("Tap a site to open it with another app.", color = Muted, fontSize = 13.sp)
            // Column headers, lined up with the rows below
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Sites", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(ARROW))
                Text("Apps", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(APP_COLUMN))
            }
            HorizontalDivider(color = Line)
            Sites.ALL.forEach { site ->
                SiteRow(site, routes.overrides[site.id], approved, taken[site.id]) { picking = Picking(site) }
                HorizontalDivider(color = Line)
            }
            ThemePicker()
            Footer()
        }

        picking?.let { p ->
            val site = p.site
            val current = if (site == null) routes.default else routes.overrides[site.id]
            val siteApps = remember(site, tick) { site?.let { Apps.siteApps(this, it) }.orEmpty() }
            Picker(
                title = site?.name ?: "Open all sites with",
                site = site,
                takenBy = site?.let { taken[it.id] },
                none = if (site == null) "Ask every time"
                    else "Default (" + (routes.default?.let { Apps.label(this, it) } ?: "ask every time") + ")",
                groups = listOf("Browsers" to browsers) + if (siteApps.isEmpty()) emptyList() else listOf("${site?.name} app" to siteApps),
                selected = current,
                onDismiss = { picking = null },
                onPick = { pkg ->
                    picking = null
                    scope.launch {
                        if (site == null) RouteStore.setDefault(this@MainActivity, pkg)
                        else RouteStore.setOverride(this@MainActivity, site, pkg)
                    }
                },
            )
        }
    }

    /** "Facebook / facebook.com · fb.me …  →  its override (blue, bold), or "Default". */
    @Composable
    private fun SiteRow(site: Site, override: String?, approved: Map<String, Boolean>?, takenBy: String?, onClick: () -> Unit) {
        // Only once some links are approved: before that the banner says it for all of them
        val notApproved = approved != null && approved.values.any { it } && site.hosts.any { approved[it] != true }
        Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SiteIcon(site)
                Column(Modifier.weight(1f)) {
                    Text(site.name, color = Ink, fontSize = 15.sp)
                    Text(
                        site.hosts.map { it.removePrefix("*.") }.distinct().joinToString(" · ") +
                            if (notApproved) " · not approved" else "",
                        color = Muted, fontSize = 12.sp,
                    )
                }
                Text("→", color = Ink, fontSize = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.width(ARROW))
                // The site's own app if it takes the links, else the override, else just "Default" (so overrides
                // stand out). Fixed width, so the arrows line up.
                val pkg = takenBy ?: override
                Row(
                    Modifier.width(APP_COLUMN),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (pkg == null) {
                        Text("Default", color = Muted, fontSize = 13.sp)
                    } else {
                        AppIcon(pkg)
                        Text(
                            Apps.label(this@MainActivity, pkg) ?: pkg,
                            color = if (takenBy == null) Blue else Muted,
                            fontWeight = if (takenBy == null) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            // Under the whole row (from the name on), not squeezed into the name column
            takenBy?.let {
                Text(
                    "Opened by the ${Apps.label(this@MainActivity, it) ?: it} app, not BLinkr",
                    color = Blue, fontSize = 12.sp, modifier = Modifier.padding(start = ICON + 12.dp, top = 6.dp),
                )
            }
        }
    }

    /** "uncorp.net · v0.1.0" — same footer in every app of the collection; tap opens the site. */
    @Composable
    private fun Footer() {
        val version = remember { packageManager.getPackageInfo(packageName, 0).versionName }
        Text(
            "uncorp.net · v$version",
            modifier = Modifier.fillMaxWidth()
                .clickable { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://uncorp.net"))) }
                .padding(vertical = 20.dp),
            color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center,
        )
    }

    /** Collapsible instructions: approving links, and what to do about sites with their own app. */
    @Composable
    private fun HowItWorks() {
        var open by remember { mutableStateOf(false) }
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("How it works", color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Chevron(open)
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Step("1", "Tap the blue banner and approve all links.")
                Step("2", "Pick one app for all sites. Tap a site to pick another for it.")
                Step("3", "A site says “Opened by the … app”? That app keeps its links. To let BLinkr choose, tap the site and turn them off.")
            }
        }
    }

    /** A numbered one-line step in "How it works". */
    @Composable
    private fun Step(number: String, text: String) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(number, color = Blue, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(14.dp))
            Text(text, color = Ink, fontSize = 14.sp)
        }
    }

    /** A drawn chevron (18dp, as in IPeekr) pointing down; rotates to point up while open. */
    @Composable
    private fun Chevron(expanded: Boolean) {
        val angle by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
        val color = Blue
        Canvas(Modifier.size(18.dp).rotate(angle)) {
            val w = size.width
            val path = Path().apply {
                moveTo(w * 0.22f, w * 0.38f); lineTo(w * 0.5f, w * 0.66f); lineTo(w * 0.78f, w * 0.38f)
            }
            drawPath(path, color, style = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    /**
     * Shown until BLinkr's links are approved (Android 12+ sends it nothing before that); tap opens its
     * "Open by default" settings. A site left out later just says "not approved" in its row.
     */
    @Composable
    private fun ApprovalBanner(approved: Map<String, Boolean>) {
        if (Sites.ALL_HOSTS.any { approved[it] == true }) return
        Column(
            Modifier.fillMaxWidth()
                .background(Blue, RoundedCornerShape(12.dp))
                .clickable {
                    startActivity(Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Uri.parse("package:$packageName")))
                }
                .padding(12.dp),
        ) {
            Text("Allow BLinkr to open links", color = OnBlue, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("Tap, then “Add link” and select all.", color = OnBlue, fontSize = 13.sp)
        }
    }

    /** Pick an app: a "none" choice (ask / default) on top, then the apps in groups. */
    @Composable
    private fun Picker(
        title: String,
        site: Site?,
        takenBy: String?,
        none: String,
        groups: List<Pair<String, List<App>>>,
        selected: String?,
        onDismiss: () -> Unit,
        onPick: (String?) -> Unit,
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = Paper,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    site?.let { SiteIcon(it) }
                    Text(title, color = Ink)
                }
            },
            text = {
                LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    // Taken by the site's own app: nothing picked here would apply, so only explain how to change that
                    if (takenBy != null) {
                        item {
                            val name = Apps.label(this@MainActivity, takenBy) ?: takenBy
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    "The $name app is set to open these links itself, so Android never sends them to BLinkr " +
                                        "and nothing picked here would apply.",
                                    color = Ink, fontSize = 14.sp,
                                )
                                Text(
                                    "To let BLinkr choose, turn off “Open supported links” in the $name app's settings. " +
                                        "Then come back here to pick a browser (or the $name app).",
                                    color = Ink, fontSize = 14.sp,
                                )
                                Text(
                                    "Open $name app settings",
                                    modifier = Modifier.fillMaxWidth()
                                        .background(Blue, RoundedCornerShape(12.dp))
                                        .clickable { startActivity(Apps.openByDefaultSettings(takenBy)) }
                                        .padding(12.dp),
                                    color = OnBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        return@LazyColumn
                    }
                    item { PickerRow(null, none, selected == null) { onPick(null) } }
                    groups.forEach { (header, apps) ->
                        item { Text(header, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) }
                        items(apps, key = { header + it.pkg }) { app -> PickerRow(app.pkg, app.label, selected == app.pkg) { onPick(app.pkg) } }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(if (takenBy != null) "Close" else "Cancel", color = Blue) } },
        )
    }

    @Composable
    private fun PickerRow(pkg: String?, label: String, selected: Boolean, onClick: () -> Unit) {
        Row(
            Modifier.fillMaxWidth()
                .background(if (selected) Blue else Color.Transparent, RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIcon(pkg, keepSpace = true)
            Text(label, color = if (selected) OnBlue else Ink, fontSize = 15.sp)
        }
    }
}
