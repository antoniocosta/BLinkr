<img src="art/blinkr.svg" width="96" alt="BLinkr icon">

# BLinkr

A tiny Android link router for social media. When you open a link to one of 20 sites (Facebook, Instagram, TikTok, Twitter / X, YouTube, Reddit, Discord, Snapchat, LinkedIn & co.), BLinkr passes it to the app *you* chose, showing nothing on the way. For example: TikTok in Firefox Focus, Facebook in the AdGuard browser, everything else in Brave.

It's a much simpler take on [LinkSheet](https://github.com/LinkSheet/LinkSheet): one fixed list of sites, one app for all of them, and optional per-site overrides. No browser of its own, no network access, no background work.

BLinkr has a sibling app with the same look: [IPeekr](https://github.com/antoniocosta/IPeekr), a home-screen widget that shows your connection.

- **Stack:** Kotlin, Compose, DataStore.
- **SDK levels:** minSdk 26, target 36.
- **Theme:** black, white and blue (#0000EE; #8AB4F8 on #202124 in dark mode). Follows the system's light or dark mode, or pick Light or Dark at the bottom of the main screen.

## How it works

1. **BLinkr declares the sites' domains.** [`Sites.kt`](app/src/main/java/net/uncorp/blinkr/Sites.kt) lists them (`*.instagram.com`, `t.co`, `youtu.be`, …). The same hosts are in the manifest's intent filter, because Android only lets an app open domains it declares at build time. That's why the list is fixed, not editable in the app.
2. **You approve them once.** BLinkr doesn't own these domains, so Android 12+ won't make it the default for them automatically. The app's banner opens Settings → Apps → BLinkr → Open by default → *Add link*; tick them all. On Android 8–11, Android asks the first time you open such a link: pick BLinkr, *Always*.
3. **Links reach [`RouterActivity`](app/src/main/java/net/uncorp/blinkr/RouterActivity.kt)**, which finds the site and opens the link in that site's override, or else the default app. On the way it flashes a small "logo → app" card in the middle of the screen for 0.6 s, so you can tell BLinkr handled it. If no app is set ("Ask every time"), it shows its own list of browsers and the site's apps to pick from. Android's chooser can't be used here: on Android 12+ it only offers the app approved for the domain (BLinkr itself), and it shows at most 2 extra apps. The link is opened with an explicit package, so it never loops back.

The picker lists every installed browser (any app that opens arbitrary websites) and, for each site, apps made for it (e.g. the Reddit app), so a site can also be sent to its own app. Listing apps needs `QUERY_ALL_PACKAGES`. That's fine for sideloading or F-Droid; the Play Store only allows it for some kinds of app.

### The site's own app comes first

Apps like YouTube, Reddit, Instagram or TikTok are *verified* for their domains. While one is installed with *Open supported links* on (the default), Android sends its links straight to it, and BLinkr never sees them. This holds even if BLinkr's link for that domain is approved. Tested on Android 13 with YouTube.

- BLinkr can't override this by itself; only the user can, in that app's settings.
- BLinkr detects it: the site's row says "Opened by the YouTube app, not BLinkr", and its picker has a shortcut to that app's *Open by default* screen to turn its links off.
- Once they're off, BLinkr gets the links and routes them. To still use the app for that site, pick it under "*Site* app" in the picker. To have every link for that site go to its app, leaving the app's links on works just as well.

## Limits

- **In-app browsers aren't affected.** Links tapped inside Instagram, TikTok or Facebook open in their own built-in browser unless you choose "Open in browser".
- **Links that an app sends to a specific browser** (Custom Tabs pinned to Chrome, for example) skip BLinkr.
- **Release builds don't log** (R8 strips `Log.v/d/i/w`).

## Adding a site

Add it to `Sites.ALL` in `Sites.kt`, run `scripts/gen-manifest.py` (it rewrites the manifest's host list), and approve the new links on the phone. `SitesTest` fails if the manifest and `Sites.kt` differ.

## Develop

The scripts use the SDK, JDK 21 and emulator from an `android-dev-toolkit` folder next to this repo (override with `TOOLKIT=`). With your own Android SDK and JDK 21, plain `./gradlew assembleDebug` works too.

```sh
scripts/dev.sh run                    # build, install on emulator-5554, launch
scripts/dev.sh test                   # unit tests
scripts/dev.sh phone                  # release build (R8, ~1 MB) installed on the USB phone, user 0
scripts/dev.sh approve                # approve all links (instead of ticking them in Settings)
scripts/dev.sh open https://t.co/x    # open a link as another app would
```

## Icon

The icon source is `art/blinkr.svg` (512px pixel art). `scripts/gen-icon.py` regenerates the launcher drawables from it. Site logos come from [Simple Icons](https://simpleicons.org) (CC0), except LinkedIn and Truth Social, which are simple hand-drawn marks. Their SVGs are in `art/sites/`, and `scripts/gen-site-icons.py` turns them into drawables in brand colours.
