# Architectural Decisions, Waivers & Landmines (`DECISIONS_AND_TRAPS.md`)
> **1-Line Summary:** Records architectural decisions (forking AS2P, switching to GitHub-First CI) and verified Windows/Android/JNI landmines.

## 1. Architectural Decisions, Pivots & Audit Waivers

### [DECISION: Fork-AS2P-Over-AudioShare] | Aliases: as2p, pcm vs opus, sync priority, oss fork
> **1-Line Summary:** Forked `anton1615/PC-Audio-Stream-to-Phone` (MIT) instead of `mkckr0/audio-share` or building from scratch because AS2P already solves the hardest Wi-Fi adaptive jitter-buffer + Oboe playback problem.
* **Links:** `[FEATURE: Adaptive-Opus-Streaming]`, `[SUBSYSTEM: Audio-Transport-And-Sync]`
* **Decision Maker:** `[DECIDED BY: HUMAN]`
* **Options Considered:**
  * **Rejected / Waived:** Custom Go/Python/TS audio engine from scratch (reinventing adaptive jitter buffer + Oboe), or `mkckr0/audio-share` (uncompressed PCM lacks Wi-Fi packet-loss resilience and adaptive retransmission for YouTube/movies).
  * **Chosen:** Fork AS2P (`upstream`), keep its Rust+Oboe+Opus core intact, and build QR pairing, Google-style number matching, hotspot/USB reliability, and polished UI on top.

### [DECISION: GitHub-First-Public-Repo] | Aliases: github actions, ci.yml, public repo, artifacts
> **1-Line Summary:** Upgraded Mobile Speaker from Local-Only to a public GitHub-First repository (`Mostakim2005/mobile-speaker`) so GitHub Actions builds Rust Windows binaries and Android APKs cleanly in the cloud.
* **Links:** `[SUBSYSTEM: Audio-Transport-And-Sync]`
* **Decision Maker:** `[DECIDED BY: HUMAN]`
* **Options Considered:**
  * **Rejected / Waived:** Local-only builds requiring heavy Rust + Android NDK installations locally.
  * **Chosen:** Public GitHub repository with `.github/workflows/ci.yml` uploading CI build artifacts (`server.exe` and `app-debug.apk`) for intermediate testing and saving `vX.Y.Z` tags for milestone releases.

---

## 2. Do-Not-Touch Engineering Landmines (Traps)

### [TRAP: Pairing-Replies-Must-Use-Sender-Port] | Aliases: ephemeral port, qr pair timeout, pairing reply, symmetric port
> **1-Line Summary:** Pairing handshake replies (`AS2P_PAIR_CHALLENGE`, `AS2P_PAIR_ACK`, `AS2P_PAIR_DENY`, `AS2P_PAIR_REQUIRED`) MUST go back to the caller's exact `SocketAddr` (`addr`), never hardcoded `:12345`.
* **Links:** `[FEATURE: QR-And-Number-Match-Pairing]`, `[FLOW: QR-And-Number-Match-Handshake]`
* **Strict Rule:** **NEVER** rewrite `addr` to `SocketAddr::new(addr.ip(), 12345)` when replying to pairing control packets (`AS2P_QR_PAIR`, `AS2P_PAIR_PROBE`, `AS2P_PAIR_VERIFY`).
* **Why This Trap Exists (Plain English):** Audio streaming binds port `12345` symmetrically inside `AudioService`, whereas QR scanning and Number Matching run beforehand from `MainActivity` using ephemeral `DatagramSocket()` ports. Sending replies to `:12345` causes silent pairing timeouts.
* **Protected Code Anchors:**
  * `server/src/main.rs :: main`
  * `server/src/pairing.rs :: handle_packet`

### [TRAP: JNI-OnLoad-Extern-C] | Aliases: gJvm, retransmission callback, name mangling, native-lib
> **1-Line Summary:** `JNI_OnLoad` in `native-lib.cpp` MUST stay inside `extern "C"` or the JVM never invokes it, leaving `gJvm` null and silently disabling all packet retransmission requests.
* **Links:** `[FEATURE: Low-Latency-Oboe-Playback]`, `[SUBSYSTEM: Audio-Transport-And-Sync]`
* **Strict Rule:** **NEVER** move `JNI_OnLoad` out of the `extern "C"` block in `android/app/src/main/cpp/native-lib.cpp`.
* **Why This Trap Exists (Plain English):** C++ name-mangling hides `JNI_OnLoad` from Android's dynamic linker while everything still compiles and passes unit tests; retransmission fails silently at runtime on real phones.
* **Protected Code Anchors:**
  * `android/app/src/main/cpp/native-lib.cpp :: JNI_OnLoad`

### [TRAP: WASAPI-Loopback-Silence-Stall] | Aliases: silence padding, stalled audio stream, wasapi idle
> **1-Line Summary:** Windows WASAPI loopback stops delivering audio buffers when no application is playing sound; keep AS2P's warm-up silence and separate `media_tick` / `send_ts_ms` clocks intact.
* **Links:** `[FEATURE: MMCSS-WASAPI-Capture]`, `[SUBSYSTEM: Audio-Transport-And-Sync]`
* **Strict Rule:** **NEVER** conflate packet arrival/wall-clock timestamps with `media_tick` or remove initial warm-up silence frames.
* **Why This Trap Exists (Plain English):** WASAPI loopback pauses callbacks during system silence, and retransmitted packets look old by wall-clock time even when on time in the 20 ms media timeline.
* **Protected Code Anchors:**
  * `server/src/network.rs :: build_audio_packet`

### [TRAP: Android-USB-Needs-ADB-Or-Tethering] | Aliases: usb wire, adb reverse, usb tethering, private profile
> **1-Line Summary:** Stock unrooted Android phones cannot act as plug-and-play USB Audio Class speakers; wired streaming requires USB RNDIS tethering (with Windows network profile set to Private) or `adb reverse` TCP tunneling.
* **Links:** `[WISHLIST: Seamless-USB-ADB-And-Tethering]`, `[SUBSYSTEM: Audio-Transport-And-Sync]`
* **Strict Rule:** **NEVER** assume raw USB audio class works on stock Android, and note that `adb reverse` forwards TCP only (not UDP), whereas USB tethering supports UDP `12345` natively.
* **Why This Trap Exists (Plain English):** Android exposes MTP/ADB/RNDIS over USB rather than a UAC2 audio sink gadget, and Windows Public firewall profiles silently drop UDP discovery replies on tethered adapters.
* **Protected Code Anchors:**
  * `server/src/main.rs :: bind_socket`
