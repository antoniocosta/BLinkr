package net.uncorp.blinkr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class SitesTest {
    @Test fun matchesHostsAndSubdomains() {
        assertEquals("tiktok", Sites.match("vm.tiktok.com")?.id)
        assertEquals("tiktok", Sites.match("tiktok.com")?.id)
        assertEquals("x", Sites.match("T.CO")?.id)
        assertEquals("facebook", Sites.match("l.facebook.com.")?.id)
        assertEquals("youtube", Sites.match("youtu.be")?.id)
        assertNull(Sites.match("nytimes.com"))
        assertNull(Sites.match("notfacebook.com"))   // "*.facebook.com" needs the dot
        assertNull(Sites.match(null))
    }

    @Test fun overridesBeatTheDefault() {
        val r = Routes(default = "brave", overrides = mapOf("tiktok" to "focus"))
        assertEquals("focus", r.target(Sites.match("www.tiktok.com")))
        assertEquals("brave", r.target(Sites.match("x.com")))
        assertNull(Routes().target(Sites.match("x.com")))  // nothing picked: ask
    }

    @Test fun sampleUrls() {
        assertEquals("https://www.tiktok.com/", Sites.sampleUrl("*.tiktok.com"))
        assertEquals("https://t.co/", Sites.sampleUrl("t.co"))
    }

    @Test fun idsAndHostsAreUnique() {
        assertEquals(Sites.ALL.size, Sites.ALL.map { it.id }.toSet().size)
        assertEquals(Sites.ALL_HOSTS.size, Sites.ALL_HOSTS.toSet().size)
    }

    @Test fun sitesAreAlphabetical() {
        val names = Sites.ALL.map { it.name.lowercase() }
        assertEquals(names.sorted(), names)
    }

    @Test fun everySiteHasAnIcon() {
        assertEquals(Sites.ALL.map { it.id }.toSet(), SiteIcons.BY_ID.keys)
    }

    /** Android only opens the hosts in the manifest: it must list exactly the ones in Sites.kt. */
    @Test fun manifestHasExactlyTheSiteHosts() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val hosts = Regex("android:host=\"([^\"]+)\"").findAll(manifest).map { it.groupValues[1] }.toList()
        assertEquals("run scripts/gen-manifest.py", Sites.ALL_HOSTS, hosts)
    }
}
