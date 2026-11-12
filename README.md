# LyronixAi (AgonApp)

Aplikasi pemutar musik Android berbasis **Jetpack Compose** dengan fitur:

- 🎵 **Pemutar musik** — streaming dari YouTube Music, Saavn, dan iTunes
- 🔍 **Pencarian & chart** — Discover, charts, genre, dan riwayat pencarian
- 🤖 **AI Chat** — analisis musik, lirik, prompt Suno, kode HTML/CSS/JS, dan generate gambar
- 📝 **Lirik** — lirik sinkron (LRC) dari LrcLib
- ⏰ **Alarm musik** — jadwal alarm dengan playlist/random
- 🎚️ **Equalizer** — profil EQ parametrik
- 📥 **Download** — simpan lagu offline (cache 512 MB)
- 😴 **Sleep timer** — matikan pemutar otomatis / fade-out

## Modul

- `app` — Aplikasi utama (UI Compose, ViewModel, database Room, pemutar ExoPlayer)
- `innertube` — Klien YouTube Music API (Ktor + MetrolistExtractor/NewPipe)

## Build

```bash
# Butuh JDK 17 dan Android SDK (compileSdk 35)
# `local.properties` TIDAK boleh di-commit — buat sendiri di mesin lokal:
#   sdk.dir=/path/ke/android-sdk
./gradlew assembleDebug
```

APK hasil build: `app/build/outputs/apk/debug/app-debug.apk`

## Konfigurasi

- API key AI diatur lewat **Settings** dalam aplikasi (Gemini, Claude, DeepSeek, GLM, Kimi, Mistral, GoAPI, atau OpenAI).
- Firebase diaktifkan via `app/google-services.json` (optional; aplikasi tetap jalan tanpa login).
