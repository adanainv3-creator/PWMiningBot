# Lumo 🎵

**Premium local music player for Android.**

Lumo plays music stored on your device — no accounts, no streaming, no internet required. Built with Jetpack Compose, Material 3, and Media3 ExoPlayer.

---

## Features

- **Local playback only** — plays music from device storage
- **Now Playing** — rotating album art disc, live waveform visualizer, full controls
- **Equalizer** — 5-band EQ with presets
- **Sleep Timer** — auto-stop after a set duration
- **Playlists** — create and manage playlists
- **Favorites** — heart any song, access them instantly
- **Recently Played** — automatic listening history
- **Search** — find songs, artists, albums instantly
- **Albums & Artists** — full library browser
- **Queue** — view and manage the current play queue
- **Mini Player** — persistent bottom bar with progress and quick controls
- **Dark Theme** — mat black / neon green / crimson red palette
- **Glassmorphism UI** — glass cards, soft shadows, fluid animations

---

## Tech Stack

| Layer | Tech |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Playback | Media3 ExoPlayer + MediaSession |
| DI | Hilt |
| DB | Room |
| Async | Coroutines + Flow |
| Image | Coil |
| Nav | Navigation Compose |

---

## Project Structure

```
app/src/main/java/com/lumo/app/
├── data/
│   ├── local/          # Room database, DAOs
│   ├── model/          # Data classes (Song, Album, Artist, Playlist)
│   └── repository/     # MediaRepository (MediaStore + Room)
├── service/            # LumoPlaybackService (Media3 MediaSessionService)
├── ui/
│   ├── components/     # Shared composables (GlassCard, MiniPlayer, WaveformVisualizer…)
│   ├── navigation/     # NavGraph, Screen sealed class
│   ├── screens/        # All screens
│   └── theme/          # Color, Typography, Shapes, Theme
├── utils/              # PlayerController, AppModule (Hilt), formatters
├── viewmodel/          # MainViewModel
├── LumoApplication.kt
└── MainActivity.kt
```

---

## CI/CD

GitHub Actions runs on every push and PR:

| Job | Trigger |
|---|---|
| Lint | push / PR |
| Unit tests | push / PR |
| Debug APK build | push / PR (after lint + test) |
| Release APK + AAB | tag `v*` only |
| GitHub Release | tag `v*` |

### Release Secrets

Add these to your repository secrets for signed release builds:

| Secret | Description |
|---|---|
| `KEYSTORE_BASE64` | Base64-encoded `.keystore` file |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

Encode your keystore: `base64 -w 0 lumo.keystore`

---

## Permissions

| Permission | Purpose |
|---|---|
| `READ_MEDIA_AUDIO` (API 33+) | Read audio files |
| `READ_EXTERNAL_STORAGE` (API ≤ 32) | Read audio files |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Background playback |
| `POST_NOTIFICATIONS` (API 33+) | Media notification |
| `WAKE_LOCK` | Keep CPU alive during playback |

---

## Fonts

This project expects the following font files in `app/src/main/res/font/`:

- `inter_regular.ttf`
- `inter_medium.ttf`
- `inter_semibold.ttf`
- `inter_bold.ttf`
- `inter_extrabold.ttf`
- `space_grotesk_regular.ttf`
- `space_grotesk_medium.ttf`
- `space_grotesk_semibold.ttf`
- `space_grotesk_bold.ttf`

Download from [fonts.google.com](https://fonts.google.com).

---

## License

MIT License. See [LICENSE](LICENSE).
