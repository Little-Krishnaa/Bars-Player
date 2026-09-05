# AGENTS.md — Bars Player (Music Player Hi-Res Lokal, Android)

**Repo:** https://github.com/Little-Krishnaa/Bars-Player.git

File context buat AI coding agent (Claude Code, dll) yang kerja di project ini.
Baca ini lengkap sebelum generate atau ubah kode apapun.

## Ringkasan Project

Music player Android native buat **koleksi FLAC/hi-res pribadi**. Nggak ada
integrasi streaming service. Diferensiator utama dibanding player biasa:

1. **Pipeline terjemahan lirik pakai LLM** — ini fitur utama, bukan
   pelengkap. Berbasis makna & konteks, bukan terjemahan literal
   kata-per-kata. Persona/gaya terjemahannya **genre-agnostic**: hip-hop
   itu use-case utama developer, tapi bukan sesuatu yang di-hardcode ke
   app — disediakan sebagai salah satu preset system prompt, di samping
   preset genre lain dan opsi full custom prompt (detail di Spesifikasi
   Fitur §3). Kalau ada trade-off waktu/effort antara fitur ini vs fitur
   lain, prioritasin ini duluan.
2. Output bit-perfect ke USB DAC (nggak ada resampling ke 48kHz)
3. UX yang bersih & cepat (motivasinya: frustrasi sama UX HiBy Music
   padahal audio engine-nya bagus)

## Batasan Tegas — JANGAN DILAKUKAN

Ini hard constraint, bukan sekadar preferensi. Jangan implementasi, saranin,
atau scaffold hal-hal berikut meskipun kelihatan seperti ekstensi wajar:

- **Jangan ada integrasi API Deezer, Tidal, Spotify, Apple Music, atau
  streaming service apapun.** App ini cuma muter file lokal.
- **Jangan ada DRM circumvention, stream resolver, atau tools
  reverse-engineered** dalam bentuk apapun (misal apapun yang mirip
  resolver ala Deezload/Metrofuse).
- **Jangan pakai Navidrome / server streaming self-hosted.** Udah diputuskan
  eksplisit — arsitektur yang dipilih itu local-only (satu device, nggak ada
  requirement sinkron multi-device).
- Kalau ada task yang kelihatannya butuh salah satu dari hal di atas, stop
  dan tanya dulu, jangan cari jalan muter buat tetep ngerjain.

## Tech Stack (udah diputuskan, jangan diperdebatkan lagi)

| Layer | Pilihan | Alasan |
|---|---|---|
| Bahasa | Kotlin | Fitur inti butuh API native-Android-only |
| UI | Jetpack Compose | Modern, declarative, standar buat app Android baru |
| Playback engine | Media3 ExoPlayer + `media3-decoder-ffmpeg` extension | Native support FLAC & decoding lossless tingkat lanjut (APE/ALAC/DSD/hi-res PCM), gapless playback, queue/playlist management yang matang |
| Output bit-perfect | Custom `AudioSink` / `AudioTrack` + `AudioMixerAttributes` (`MIXER_BEHAVIOR_BIT_PERFECT`) (Android 14+, API 34) | API resmi Google buat bypass resampling AudioFlinger ke USB DAC via konfigurasi custom AudioTrack/AudioSink — gantiin kebutuhan custom UAC driver |
| Fallback (pre-Android 14 / device nggak support) | AAudio exclusive/MMAP mode | Paling mendekati bit-perfect di device lama; harus degrade dengan graceful, bukan crash |
| Local DB | Room (SQLite) | Index metadata library + cache terjemahan lirik |
| Nggak dipakai | Flutter | Ditolak — API audio inti itu native-only; bridge Dart↔Kotlin nambah friction tanpa benefit kalau pemilihan tools emang agent-driven, bukan soal kenyamanan developer |

## Spesifikasi Fitur

### 1. Library Lokal
- Scan storage device buat file audio lossless (target utama FLAC; support
  juga WAV/APE/ALAC/DSD via ExoPlayer + FFmpeg extension)
- Baca metadata embedded (artist, album, title, cover art)
- Index ke Room DB lokal buat search/browse cepat — jangan scan ulang
  filesystem tiap kali app dibuka

### 2. Playback Engine
- Berbasis ExoPlayer dengan ekstensi `media3-decoder-ffmpeg` untuk menangani
  decoding audio lossless secara luas & presisi tinggi
- Implementasi custom `AudioSink` (berbasis `DefaultAudioSink`) untuk
  mengatur instance `AudioTrack` secara kustom
- Detect koneksi USB DAC; kalau available dan device support (Android 14+, API 34),
  injeksikan `AudioMixerAttributes` dengan `MIXER_BEHAVIOR_BIT_PERFECT` pada
  `AudioTrack` terkait; tampilkan indikator UI sample rate/bit depth yang
  lagi diputar dan status bit-perfect aktif (support vendor buat
  `AUDIO_OUTPUT_FLAG_BIT_PERFECT` itu optional per-device — jangan pernah
  asumsi berhasil diam-diam)
- Fallback path harus silent/graceful di device/versi OS yang nggak support
  bit-perfect mode — jangan sampai crash atau UI nyangkut

### 3. Pipeline Lirik + Terjemahan LLM (FITUR UTAMA)

- **Prioritas sumber lirik:** file `.lrc` lokal di sebelah file audio dulu,
  baru fallback ke API LRCLIB (lrclib.net) buat synced lyrics

- **Konfigurasi AI provider** (di menu Settings, user-configurable):
  - Tiga mode yang harus disupport:
    1. Custom HTTP endpoint (URL configurable, user kasih endpoint sendiri)
    2. Anthropic SDK native
    3. OpenAI SDK native
  - Per mode, user input: API key dan nama model (boleh pilih dari
    beberapa opsi rekomendasi, tapi juga bisa isi manual/free text)
  - API key **wajib** disimpen encrypted (EncryptedSharedPreferences atau
    Android Keystore) — jangan pernah plaintext di SharedPreferences biasa
    atau ke-log
  - Implementasi: bikin interface abstraksi (misal `TranslationProvider`)
    dengan 3 implementasi konkret sesuai mode di atas, dipilih runtime
    berdasarkan setting yang lagi aktif — jangan hardcode ke satu provider

- **System prompt — 3 lapis, beda sifat & hak akses:**
  1. **Hardcoded, nggak bisa diubah user (constraint fungsional):**
     - Instruksi format output JSON terstruktur (schema di bawah)
     - Instruksi jumlah baris `translated` harus 1:1 sama baris sumber —
       ini bukan soal gaya, tapi constraint teknis biar timestamp sync
       nggak berantakan; dikunci berapapun preset/genre yang lagi aktif
  2. **Context otomatis, bukan bagian persona yang diedit:** metadata
     yang ada (artist, judul, tahun, region kalau tau) disisipin ke
     request tiap kali translate. Ini data yang di-passed, bukan teks
     yang user tulis/edit.
  3. **Persona/gaya terjemahan — sepenuhnya user-configurable, TIDAK
     boleh di-hardcode ke satu genre (misal hip-hop):**
     - Diimplementasi sebagai daftar **preset per-genre** yang bisa
       dipilih di Settings, plus opsi **Custom** (field kosong, user
       nulis dari nol)
     - Preset cuma nge-prefill text field yang tetap 100% editable —
       pilih preset lalu ubah dikit itu valid, sama kayak nulis full
       custom
     - Simpan preset sebagai data (list `{nama, prompt_text}`), bukan
       logic if/else di kode — nambah genre baru harus tinggal nambah
       entry, nggak sentuh kode translation pipeline
     - Isi preset **Hip-Hop** (referensi awal, tetap bisa diedit user):
       terjemahin makna/konteks bukan kata-per-kata (idiom, double
       entendre, wordplay), tone santai bukan formal, field `note`
       opsional per baris — isi cuma kalau slang/referensi beneran
       butuh penjelasan
     - Preset genre lain (Pop, R&B, Rock, dll) & preset mana yang aktif
       default pas first-install — belum ditentuin, liat Keputusan
       Terbuka
  - Prompt final tiap request = (hardcoded #1) + (context #2) + (persona
    #3, preset aktif atau custom) — di-construct tiap request, bukan
    disimpen gabungan permanen, biar kalau bagian hardcoded diupdate di
    versi app baru, otomatis kepake tanpa user perlu reset settings.

- **Format output:** JSON terstruktur (pakai structured-output/JSON mode
  dari provider kalau ada, jangan andalkan format JSON dari prompt doang):
  ```json
  {
    "lines": [
      { "time_ms": 12500, "original": "...", "translated": "...", "note": null }
    ]
  }
  ```

- **Caching:** cache hasil terjemahan lokal di Room (table terpisah dari
  index library), key-nya `hash(artist + title + lirik_mentah)` — jangan
  pernah panggil ulang AI provider buat track yang udah pernah diterjemahin.
  Kalau user ubah persona/system prompt di Settings, cache lama tetep valid
  (translation lama nggak otomatis re-generate) kecuali user eksplisit minta
  re-translate.

- **Resilience — wajib graceful, konsisten sama filosofi fallback di
  playback engine:**
  - Gagal manggil AI provider (nggak ada koneksi, timeout, API error, atau
    response JSON invalid/gagal di-parse) → fallback tampilin lirik asli
    aja tanpa translation, jangan block playback, jangan crash
  - Angka pasti retry count & timeout duration — liat Keputusan Terbuka

## Build Environment (udah disetup — buat referensi doang)

- **Host:** Linux **aarch64** (ARM64) — bukan x86_64. Ini penting karena
  binary resmi Android SDK build-tools dari Google (`aapt2`, `aidl`,
  `zipalign`, `split-select`) cuma dirilis buat `linux-x86_64`.
- JDK 17 (Eclipse Temurin, native ARM64) di `/opt/jdk17`, `$JAVA_HOME` udah
  di-set
- Android SDK di `~/android-sdk` (`$ANDROID_HOME`), diinstall lewat
  `sdkmanager` klasik (BUKAN tool baru "Android CLI" dari Google — tool itu
  belum ada build buat Linux ARM64, jangan saranin install itu)
- Komponen SDK yang terinstall: `platform-tools`, `platforms;android-34`,
  `build-tools;37.0.0` (juga ada 33.0.2/35.0.0/36.0.0)
- Binary native ARM64 pengganti `aapt2`/`aidl`/`zipalign`/`split-select`
  diinstall lewat drop-in script `Commit451/android-arm-build-tools` ke
  `build-tools/37.0.0/`
- **Kalau project nanti pakai AGP 9.x:** dia narik `aapt2` sendiri dari
  Maven (x86_64-only) terlepas dari binary di SDK. Perlu ditambahin ke
  `gradle.properties`:
  ```
  android.aapt2FromMavenOverride=/home/tiny/android-sdk/build-tools/37.0.0/aapt2
  ```
  Nggak perlu kalau masih AGP 8.x ke bawah.
- `adb`/`fastboot` **nggak diinstall lokal** — install/testing di device
  lewat artifact GitHub Actions + install manual, bukan `adb` lokal. Jangan
  generate workflow yang asumsi ada koneksi device lokal.
- Nggak ada emulator lokal — jangan andalkan testing yang cuma bisa lewat
  emulator.

## Autentikasi Git (Push)

- Push ke repo pakai **fine-grained PAT**, scope: repo `Bars-Player` doang
  (bukan classic PAT scope `repo` yang akses semua repo)
- Permission token: minimal `Contents: Read and write` (wajib buat push).
  `Metadata: Read-only` otomatis included. `Actions`/`Pull requests` opsional,
  tergantung apa agent perlu cek status run/PR lewat API atau nggak.
- Token disimpen di **`.env`** di root repo (env var `GITHUB_PAT`),
  **bukan** di `AGENTS.md` atau file yang ke-track git manapun — `.env`
  wajib ada di `.gitignore`
- Dipakai buat autentikasi `git push` dari sandbox/agent doang. **Ini
  beda dari `GITHUB_TOKEN`** yang otomatis di-inject GitHub Actions tiap
  workflow run — CI nggak butuh `GITHUB_PAT` ini sama sekali.
- Token yang lagi aktif sekarang di-generate tanpa expiration date —
  pas nanti sempet, rotate ke token baru dengan expiration date di-set,
  dan revoke yang lama.



- GitHub Actions, runner standar `ubuntu-latest` (x86_64) udah cukup. Host
  architecture nggak perlu match sama target architecture buat Android
  (beda sama iOS) — runner ini bisa build APK target `arm64-v8a` tanpa
  masalah.
- Command verifikasi umum, urutan dari cepat ke lengkap:
  ```bash
  ./gradlew compileDebugKotlin   # cek syntax/type cepat
  ./gradlew lint                 # static analysis
  ./gradlew assembleDebug        # full APK
  ```
- **Gradle cache wajib diaktifin** biar build ke-2 dst nggak download
  ulang dependencies dari nol. Pakai opsi bawaan `actions/setup-java`,
  jangan setup `actions/cache` manual kalau nggak perlu:
  ```yaml
  - uses: actions/setup-java@v4
    with:
      distribution: 'temurin'
      java-version: '17'
      cache: 'gradle'
  ```
  Ini otomatis cache `~/.gradle/caches` dan `~/.gradle/wrapper`, key-nya
  dari hash file `**/*.gradle*` dan `**/gradle-wrapper.properties` — cache
  otomatis invalidate sendiri kalau dependency berubah, nggak perlu
  di-manage manual.

## Keputusan Terbuka / Tanya Dulu Sebelum Asumsi

- minSdk / versi Android minimum yang didukung (fitur bit-perfect itu API
  34+, tapi app tetep harus bisa install & muter musik di device lebih
  lama tanpa fitur itu)
- Apakah format lossless tambahan selain FLAC (APE, DSD) masuk scope v1
  atau iterasi berikutnya
- Package name / `applicationId` — belum fix (contoh sementara:
  `com.littlekrishnaa.barsplayer`, sesuaikan kalau mau beda)
- Architecture pattern app (MVVM/MVI) dan DI framework (Hilt/Koin/manual
  tanpa DI) — belum ditentuin
- Retry policy exact buat call AI provider: berapa kali retry, timeout
  berapa detik sebelum dianggap gagal dan fallback ke lirik asli
- Daftar model default per provider yang muncul sebagai opsi di Settings
  (atau full free-text semua, nggak ada preset)
- Preset genre selain Hip-Hop yang mau disediain di v1 (Pop, R&B, Rock,
  dll) beserta isi prompt masing-masing
- Preset mana yang aktif secara default pas app pertama kali diinstall
  (Hip-Hop, atau kosong/general sampai user pilih)
