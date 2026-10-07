package net.uncorp.blinkr

/**
 * The sites BLinkr opens. Each host here is also in AndroidManifest.xml (Android only lets an app
 * open the domains it declares at build time): after editing, run scripts/gen-manifest.py.
 * SitesTest fails if the two differ.
 *
 * "*.example.com" covers every subdomain (www., m., l., vm. …) but not example.com itself.
 */
data class Site(val id: String, val name: String, val hosts: List<String>)

object Sites {
    /**
     * Alphabetical by name (SitesTest checks). Only the domains links actually use: each one is a box to
     * tick in Android's "Add link" list. "*.x.com" doesn't cover "x.com" itself, so main domains need both.
     */
    val ALL: List<Site> = listOf(
        Site("bereal", "BeReal", listOf("bereal.com", "bere.al")),
        Site("bluesky", "Bluesky", listOf("bsky.app", "*.bsky.app")),
        Site("discord", "Discord", listOf("discord.com", "*.discord.com", "discord.gg")),
        Site("facebook", "Facebook", listOf("facebook.com", "*.facebook.com", "fb.com", "fb.me", "fb.watch")),
        Site("instagram", "Instagram", listOf("instagram.com", "*.instagram.com", "instagr.am", "ig.me")),
        Site("linkedin", "LinkedIn", listOf("linkedin.com", "*.linkedin.com", "lnkd.in")),
        Site("messenger", "Messenger", listOf("messenger.com", "*.messenger.com", "m.me")),
        Site("pinterest", "Pinterest", listOf("pinterest.com", "*.pinterest.com", "pin.it")),
        Site("quora", "Quora", listOf("quora.com", "*.quora.com")),
        Site("reddit", "Reddit", listOf("reddit.com", "*.reddit.com", "redd.it")),
        Site("snapchat", "Snapchat", listOf("snapchat.com", "*.snapchat.com")),
        Site("telegram", "Telegram", listOf("t.me", "telegram.me")),
        Site("threads", "Threads", listOf("threads.net", "*.threads.net", "threads.com", "*.threads.com")),
        Site("tiktok", "TikTok", listOf("tiktok.com", "*.tiktok.com")),
        Site("truthsocial", "Truth Social", listOf("truthsocial.com", "*.truthsocial.com")),
        Site("tumblr", "Tumblr", listOf("tumblr.com", "*.tumblr.com", "tmblr.co")),
        Site("twitch", "Twitch", listOf("twitch.tv", "*.twitch.tv")),
        Site("x", "Twitter / X", listOf("x.com", "*.x.com", "twitter.com", "*.twitter.com", "t.co")),
        Site("vk", "VK", listOf("vk.com", "*.vk.com", "vk.ru")),
        Site("youtube", "YouTube", listOf("youtube.com", "*.youtube.com", "youtu.be")),
    )

    val ALL_HOSTS: List<String> = ALL.flatMap { it.hosts }

    /** The site a link's host belongs to, or null. */
    fun match(host: String?): Site? {
        val h = host?.lowercase()?.trimEnd('.') ?: return null
        return ALL.firstOrNull { site -> site.hosts.any { matches(it, h) } }
    }

    fun matches(pattern: String, host: String): Boolean =
        if (pattern.startsWith("*.")) host.endsWith(pattern.substring(1)) else host == pattern

    /** A concrete URL for a host pattern ("*.tiktok.com" -> "https://www.tiktok.com/"), to ask Android who opens it. */
    fun sampleUrl(pattern: String): String = "https://" + (if (pattern.startsWith("*.")) "www" + pattern.substring(1) else pattern) + "/"
}
