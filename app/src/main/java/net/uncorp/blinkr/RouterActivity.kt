package net.uncorp.blinkr

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Gets every link Android sends BLinkr and passes it on: the site's override if it has one,
 * else the default app, else a list to pick from. On the way it flashes a small "logo → app" card
 * in the middle of the screen, so you can see BLinkr did it.
 */
class RouterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (uri == null) {
            finish()
            return
        }
        lifecycleScope.launch {
            val site = Sites.match(uri.host)
            val target = RouteStore.flow(this@RouterActivity).first().target(site)
                ?.takeIf { Apps.installed(this@RouterActivity, it) } // uninstalled since it was picked: ask
            Log.d(TAG, "${uri.host} (${site?.id}) -> ${target ?: "ask"}")
            if (target == null) {
                // Ask. Not the system chooser: on Android 12+ it only offers the app approved for the domain
                // (BLinkr itself), and it shows at most 2 extra apps, so BLinkr lists them itself.
                val apps = (Apps.browsers(this@RouterActivity) + (site?.let { Apps.siteApps(this@RouterActivity, it) } ?: emptyList()))
                    .distinctBy { it.pkg }.filter { it.pkg != packageName }
                setContent { AppTheme(this@RouterActivity) { Ask(uri, site, apps) } }
                return@launch
            }
            if (site != null) {
                setContent { AppTheme(this@RouterActivity) { Flash(site, target) } }
                delay(FLASH_MS)
            }
            open(uri, target)
        }
    }

    private fun open(uri: Uri, pkg: String) {
        val view = Intent(Intent.ACTION_VIEW, uri)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .setPackage(pkg)
        try {
            startActivity(view)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "$pkg can't open $uri", e)
        }
        finish()
    }

    /** "Open instagram.com with" and the apps that can; tap outside (or back) to cancel. */
    @Composable
    private fun Ask(uri: Uri, site: Site?, apps: List<App>) {
        Box(
            Modifier.fillMaxSize().background(Color(0x66000000)).clickable(interactionSource = null, indication = null) { finish() },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .padding(32.dp)
                    .widthIn(max = 360.dp)
                    .background(Paper, RoundedCornerShape(20.dp))
                    .clickable(interactionSource = null, indication = null) {} // taps on the card don't cancel
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    Modifier.padding(start = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    site?.let { SiteIcon(it) }
                    Text("Open ${uri.host?.removePrefix("www.")} with", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    apps.forEach { app ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { open(uri, app.pkg) }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AppIcon(app.pkg)
                            Text(app.label, color = Ink, fontSize = 15.sp)
                        }
                    }
                    if (apps.isEmpty()) Text("No browser installed.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(8.dp))
                }
            }
        }
    }

    /** "[Instagram logo] → [Brave icon]" on a small card, popping in at the centre. */
    @Composable
    private fun Flash(site: Site, pkg: String) {
        val shown = remember { MutableTransitionState(false).apply { targetState = true } }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AnimatedVisibility(shown, enter = fadeIn() + scaleIn(initialScale = 0.85f)) {
                Row(
                    Modifier
                        .shadow(12.dp, RoundedCornerShape(20.dp))
                        .background(Paper, RoundedCornerShape(20.dp))
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SiteIcon(site, 44.dp)
                    Text("→", color = Muted, fontSize = 22.sp)
                    AppIcon(pkg, 44.dp)
                }
            }
        }
    }

    private companion object {
        const val TAG = "BLinkr"
        /** How long the card shows before the link opens: long enough to see, short enough not to wait for. */
        const val FLASH_MS = 600L
    }
}
