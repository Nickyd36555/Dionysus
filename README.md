# Dionysus

A Netflix-style streaming app for **Android TV** (with tvOS planned), built with
**Jetpack Compose for TV**. Dionysus browses a rich movie/show catalog, finds
playable sources through pluggable scrapers, resolves them via debrid services,
plays them in a built-in or third-party player, and downloads them to local
storage for the best possible quality.

> ⚠️ Dionysus ships with **no** bundled content, API keys, scrapers indexes, or
> debrid accounts. You supply your own credentials in **Settings**. It is a
> player/aggregator front-end, exactly like Stremio or Kodi.

---

## Features

- 🎬 **Fantastic, customizable home screen** — a featured hero carousel plus
  reorderable/toggleable content rows (Trending, Popular, Continue Watching,
  My List, Downloads, …). Layout is user-configurable and persisted.
- 🔌 **Pluggable scrapers** — Torrentio and Orion included; add more by
  implementing one interface.
- ☁️ **Debrid integrations** — Real-Debrid (TV-friendly OAuth **device flow**)
  and Premiumize (API key + real cache-check), with an extensible provider set.
- ▶️ **Third-party players** — hand off to VLC, nPlayer, MX Player, Just Player,
  or Kodi, or use the built-in Media3/ExoPlayer.
- ⬇️ **Downloads** — resolve to a direct link and save to local storage via a
  foreground WorkManager job, with live progress.
- 🔎 **Search**, **detail pages** with season/episode pickers, and **Continue
  Watching** resume points.

---

## Architecture

Single Gradle module, layered by responsibility. Dependency injection is
Hilt; new integrations are registered into multibound `Set`s.

```
com.dionysus.tv
├── core/
│   ├── model/       Domain types (MediaItem, StreamSource, ResolvedStream, …)
│   ├── network/     Shared OkHttp + kotlinx.serialization JSON
│   └── di/          Hilt modules (Api, Database, Integrations, Qualifiers)
├── data/
│   ├── metadata/    TMDB catalog + artwork  → MetadataRepository
│   ├── scraper/     Scraper interface + Torrentio, Orion → ScraperRepository
│   ├── debrid/      DebridService interface + Real-Debrid, Premiumize → DebridRepository
│   ├── local/       Room (favorites, watch progress, downloads, home layout)
│   └── settings/    DataStore-backed SettingsRepository (tokens, keys, prefs)
├── player/          ExternalPlayer + PlayerLauncher (intents / built-in)
├── download/        DownloadWorker (foreground) + DownloadRepository
└── ui/              Compose for TV: home, detail, streams, search,
                     downloads, settings, player, components, theme
```

### The playback pipeline

```
MediaItem (TMDB)
   └─ StreamQuery (imdbId, season, episode)
        └─ ScraperRepository ──▶ fan-out to enabled Scrapers ──▶ List<StreamSource>
             └─ DebridRepository.annotateCache()  (flags cached torrents ⚡)
                  └─ user picks a source
                       ├─ Play    → DebridRepository.resolve() → ResolvedStream
                       │             → internal Media3 player or external app
                       └─ Download → resolve → DownloadWorker → local file
```

### Extending it

- **New scraper:** implement `data/scraper/Scraper`, add one `@Binds @IntoSet`
  line in `core/di/IntegrationsModule`. `ScraperRepository` picks it up.
- **New debrid provider:** implement `data/debrid/DebridService`, add one
  `@Binds @IntoSet` line in the same module.
- **New player:** add an entry to `player/ExternalPlayer` with its package name.

---

## Tech stack

| Concern            | Choice |
|--------------------|--------|
| Language / UI      | Kotlin, Jetpack Compose for TV (`androidx.tv:tv-material`) |
| DI                 | Hilt (KSP) |
| Networking         | Retrofit + OkHttp + kotlinx.serialization |
| Images             | Coil |
| Playback           | Media3 / ExoPlayer |
| Persistence        | Room + DataStore |
| Background work    | WorkManager |
| Min / Target SDK   | 23 / 34 (compile 35) |

---

## Building

Requires the Android SDK (platform 35, build-tools 35) and JDK 17+.

```bash
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. Sideload it onto an Android TV
device or emulator (`adb install`).

`local.properties` (with `sdk.dir=...`) is required locally and is git-ignored.

---

## First-run setup

Open **Settings** on the device and add:

1. **Metadata → TMDB API key** (required to browse the catalog).
2. **Debrid → Real-Debrid** (Connect, then enter the shown code on your phone)
   and/or **Premiumize API key**.
3. **Scrapers → Orion API key** / Torrentio base URL as desired.
4. **Preferred Player** (built-in, or an installed external player).

---

## Roadmap

- tvOS target (shared domain logic, SwiftUI front-end)
- Trakt sync for watch state
- Additional scrapers (Jackett/Prowlarr, more Stremio addons)
- Subtitle add-ons (OpenSubtitles)
- Per-source subtitle & audio track selection in the built-in player

## Legal

Dionysus is a front-end that integrates with third-party services the user
configures. It hosts, indexes, and distributes no content. Users are
responsible for how they use it and for complying with the terms of any service
they connect and the laws of their jurisdiction.
