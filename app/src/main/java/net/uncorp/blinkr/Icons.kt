package net.uncorp.blinkr

import android.graphics.Bitmap
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

/** One size for every icon in a list, so logos and app icons line up. */
val ICON = 26.dp

/** The site's logo, in its brand colour (black logos in the text colour, for dark mode). */
@Composable
fun SiteIcon(site: Site, size: Dp = ICON) {
    val tint = if (site.id in SiteIcons.INK) ColorFilter.tint(Ink) else null
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        SiteIcons.BY_ID[site.id]?.let {
            Image(painterResource(it), contentDescription = null, modifier = Modifier.size(size), colorFilter = tint)
        }
    }
}

/**
 * The app's launcher icon; nothing for "none" unless [keepSpace] (to line up with icons below).
 * Launcher icons carry empty margins of their own (adaptive-icon safe zone, extra shrinking for legacy
 * icons), different for every app, so they're cropped to what's visible to match the logos' size.
 */
@Composable
fun AppIcon(pkg: String?, size: Dp = ICON, keepSpace: Boolean = false) {
    val ctx = LocalContext.current
    val bitmap = remember(pkg) { pkg?.let { Apps.icon(ctx, it) }?.let(::visible)?.asImageBitmap() }
    if (bitmap != null) Image(bitmap, contentDescription = null, modifier = Modifier.size(size), contentScale = ContentScale.Fit)
    else if (keepSpace) Box(Modifier.size(size))
}

/**
 * The icon as you see it, without margins. An adaptive icon on a white (or near-white) background,
 * like YouTube's or AdGuard's, is mostly invisible background on our white page; only its foreground
 * is kept then, so its logo is as big as everyone else's.
 */
private fun visible(d: Drawable): Bitmap {
    if (Build.VERSION.SDK_INT >= 26 && d is AdaptiveIconDrawable) {
        val bg = d.background?.toBitmap(24, 24)
        val fg = d.foreground
        if (fg != null && (bg == null || isLight(bg))) return trim(fg.toBitmap(192, 192))
    }
    return trim(d.toBitmap(192, 192))
}

private fun isLight(b: Bitmap): Boolean {
    val px = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
    // Transparent counts as light: nothing to see there either
    return px.count { p -> (p ushr 24) < 32 || ((p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)) > 3 * 225 } > px.size * 0.9
}

/** [b] cropped to its visible pixels (faint shadows and glows don't count). */
private fun trim(b: Bitmap): Bitmap {
    val w = b.width
    val h = b.height
    val px = IntArray(w * h).also { b.getPixels(it, 0, w, 0, 0, w, h) }
    var left = w
    var top = h
    var right = -1
    var bottom = -1
    for (y in 0 until h) for (x in 0 until w) {
        if ((px[y * w + x] ushr 24) > 96) {
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            if (y > bottom) bottom = y
        }
    }
    return if (right < left) b else Bitmap.createBitmap(b, left, top, right - left + 1, bottom - top + 1)
}
