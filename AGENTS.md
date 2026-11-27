# Project Operating Instructions (`AGENTS.md`)
> **Notice:** This project inherits user profile, confirmation gates, OS-default vs. Shared Libraries/Ecosystem Registry rules, GitHub-first CI/CD defaults, closed-loop `AUDITS.md`/`UIUX.md` and `Downloads\` report routing, pre-commit doc sync, and visual/doc standards from master `AGENTS.md`. Do not duplicate those rules here.

## 1. Repository Classification & Tech Stack
* **Project Name:** Mobile Speaker
* **Verification Mode:** `[GitHub-First (Remote Configured)]` — `origin` at `https://github.com/Mostakim2005/mobile-speaker` (forked from `upstream` at `https://github.com/anton1615/PC-Audio-Stream-to-Phone`)
* **Runtime / Language:** Rust 2024 (`server/`) + Kotlin / Jetpack Compose + C++17 (`android/`)
* **Framework / Host:** Windows WASAPI loopback (`cpal` + `audiopus` + `slint` + `tray-icon`) & Android Oboe + Opus JNI (`AudioService`)
* **Target OS / Build Output:** Windows x64 `server.exe` + Android `app-debug.apk` (CI artifacts) / signed `app-release.apk` (tag-triggered `vX.Y.Z` releases)
* **Shared Libraries & Local Ports:** AS2P stream port `UDP 12345` (plus planned pairing control port `8860`, not conflicting with ports in `%USERPROFILE%\.local\share\ecosystem-registry.json`).

## 2. Verification & Build Commands (Mirrored in CI)
*(GitHub Actions `.github/workflows/ci.yml` is the authoritative builder/runner — do not install heavy Rust/Android NDK toolchains locally.)*
* **Format & Lint:** `cd server && cargo clippy --all-targets` *(optional)*
* **Static Type Check:** `cd server && cargo check`
* **Unit Tests (PC Server):** `cd server && cargo test`
* **Unit Tests (Android & C++):** `cd android && gradle testDebugUnitTest` and `g++ -std=c++17 -I android/app/src/main/cpp android/app/src/test/cpp/adaptive_buffer_test.cpp -o /tmp/ab_test && /tmp/ab_test`
* **Integration Tests:** `python server/tests/protocol_smoke.py 12345` *(against running server)*
* **E2E / UI Tests:** `tools/verify-on-device.sh` *(real Android device via `adb`)*
* **Security & Secret Scan:** Check `keystore.properties` / `*.keystore` never committed
* **Build & Smoke Test:** `cd server && cargo build --release` and `cd android && gradle assembleDebug`
* **Structure & Doc Parity:** Stage 9 in `.github/workflows/ci.yml` (UTF-8 no-BOM, doc headers, `[TYPE: Slug]` cross-links, and live `path :: symbol` anchors)

## 3. Repository Directory Structure & Conventions
* `.github/workflows/ci.yml` — Authoritative CI running Windows Rust build/tests, Android Gradle + C++ tests, artifact uploads, and Stage 9 doc parity.
* `.github/workflows/release.yml` — Tag-triggered (`v*`) release workflow producing signed APK and Windows binary.
* `server/` — Windows Rust audio capture, Opus encoder, UDP v9 streamer, and Slint tray UI.
* `android/` — Kotlin/Compose UI, `AudioService` foreground service, JNI bridge, and C++ `Oboe` + `AdaptiveBuffer` engine.
* `docs/` — Interconnected Markdown suite (`PRODUCT.md`, `ARCHITECTURE.md`, `DECISIONS_AND_TRAPS.md`). Scan dynamically via `grep -n -A 1 "^### \[" docs/` and read only matched line slices.

## 4. Critical Trap Pointers (1-Line Warnings Only)
*(Key directly by `[TRAP: <Slug>]`; put full rationale and `Protected Code Anchors` in `docs/DECISIONS_AND_TRAPS.md`.)*
* **`[TRAP: JNI-OnLoad-Extern-C]`:** Never put `JNI_OnLoad` outside `extern "C"` in `native-lib.cpp`; C++ name-mangling silently leaves `gJvm` null and breaks retransmission callbacks.
* **`[TRAP: WASAPI-Loopback-Silence-Stall]`:** Never assume WASAPI loopback emits continuous frames when Windows is silent; keep warm-up silence and media-tick separation intact.
* **`[TRAP: Android-USB-Needs-ADB-Or-Tethering]`:** Never assume stock unrooted Android acts as a plug-and-play USB Audio Class speaker; wired streaming needs USB RNDIS tethering (UDP) or `adb reverse` TCP tunneling.
