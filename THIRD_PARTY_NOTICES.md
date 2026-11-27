# Third-Party Notices

This repository is forked from [`anton1615/PC-Audio-Stream-to-Phone`](https://github.com/anton1615/PC-Audio-Stream-to-Phone) (AS2P) under the **MIT License**.

## Ships inside the app

| Component | License | Version | Source / Upstream | Notes |
| :--- | :--- | :--- | :--- | :--- |
| AS2P Base (`server/` & `android/`) | MIT | v1.3.4 | https://github.com/anton1615/PC-Audio-Stream-to-Phone | Upstream capture, Opus pipeline, Oboe output, adaptive jitter buffer. |
| `cpal` | Apache-2.0 | 0.17.1 | https://github.com/RustAudio/cpal | Windows WASAPI loopback audio capture. |
| `audiopus` / `libopus` | MIT / BSD-3-Clause | 0.2.0 | https://opus-codec.org/ | Opus 48 kHz stereo audio encoding and decoding with PLC. |
| `rubato` | MIT | 0.16.1 | https://github.com/HEnquist/rubato | Sample-rate resampling to 48 kHz. |
| `slint` | GPL-3.0 / Royalty-free | 1.9.1 | https://slint.dev/ | Desktop GUI toolkit (`renderer-software`). |
| `oboe` | Apache-2.0 | FetchContent | https://github.com/google/oboe | Low-latency Android C++ audio output (AAudio / OpenSL ES). |
| `tokio` / `tray-icon` / `mdns-sd` | MIT / Apache-2.0 | Cargo crates | https://crates.io/ | Async networking, Windows tray integration, mDNS discovery. |
| `qrcode` / `local-ip-address` | MIT / Apache-2.0 | 0.14 / 0.6 | https://crates.io/ | Local IPv4 interface enumeration and pure-Rust QR generation. |
| `zxing-android-embedded` | Apache-2.0 | 4.3.0 | https://github.com/journeyapps/zxing-android-embedded | Offline-capable Android QR code camera scanner (`ScanContract`). |
| AndroidX / Jetpack Compose / Material 3 | Apache-2.0 | BOM 2023.03.00 | https://developer.android.com/jetpack | Android UI framework and MediaStyle foreground service. |

## Build / test only — not shipped

| Component | License | Version | Source / Upstream | Notes |
| :--- | :--- | :--- | :--- | :--- |
| GitHub Actions CI (`ci.yml`) | MIT / Template | — | `%USERPROFILE%\.agent-templates\templates\ci.yml` | Automated build, test, and doc-parity verification. |
