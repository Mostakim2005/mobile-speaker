# System Architecture & Visual Flows (`ARCHITECTURE.md`)
> **1-Line Summary:** Living visual map of Mobile Speaker (forked from AS2P), showing Windows WASAPI/Opus capture, UDP v9 + adaptive jitter buffer playback, and QR/number-match pairing. *(Build and test commands live in root `AGENTS.md`.)*

## 1. High-Level Visual System Map
* **Plain-English Overview:** The Rust Windows server enumerates local Wi-Fi/hotspot/USB IPv4 addresses into a live QR code and 2-digit match number while advertising `_as2p._udp.local.` via mDNS. Once paired via QR scan or number confirmation, Android starts its `AudioService` foreground service, receives Opus UDP v9 frames into a 5-state `AdaptiveBuffer`, requests resends on gaps, and plays audio via C++ Oboe.

```mermaid
flowchart TB
    %% Color Definitions (Obsidian & GitHub compatible)
    classDef startNode fill:#1d4ed8,stroke:#1e3a8a,color:#ffffff
    classDef processNode fill:#0f766e,stroke:#115e59,color:#ffffff
    classDef outputNode fill:#15803d,stroke:#14532d,color:#ffffff
    classDef trapNode fill:#b91c1c,stroke:#7f1d1d,color:#ffffff

    PC([Windows PC: WASAPI Loopback + QR/Number UI]):::startNode -->|20ms Opus v9| Net[UDP 12345 Sender + PairingSession + mDNS]:::processNode
    PhonePair([Phone: QR Scan or 3-Number Tap]):::startNode -->|AS2P_QR_PAIR / AS2P_PAIR_VERIFY| Net
    Net -->|replies to caller SocketAddr| PhonePair
    Net -->|UDP 12345 Wi-Fi / Hotspot / USB| Svc[Android AudioService Foreground]:::processNode
    Svc -->|raw v9 payload| Jitter[C++ AdaptiveBuffer 5-State Controller]:::processNode
    Jitter -.->|tick gap detected| Net
    Jitter -->|decoded PCM| Oboe([Android Oboe Exclusive Output]):::outputNode
    JNI[extern C JNI_OnLoad Guard]:::trapNode -.->|protects resend callback| Jitter

    subgraph Legend["Legend (Color Guide)"]
        L1([Blue = PC Capture & Phone Pairing]):::startNode
        L2[Teal = Pairing, Opus, UDP v9, Adaptive Buffer]:::processNode
        L3([Green = Android Speaker Output]):::outputNode
        L4[Red = JNI / Real-Time Guard]:::trapNode
    end
```

## 2. Tech Stack & Why We Chose It

| Layer | Tool / Library | Plain-English Reason Why |
| :--- | :--- | :--- |
| Windows Capture & Encode | Rust (`cpal` + `rubato` + `audiopus`) | Captures WASAPI loopback on MMCSS Pro Audio threads and encodes 48 kHz Opus with minimal CPU. |
| PC Pairing & QR UI | `qrcode` + `local-ip-address` + `slint` + `mdns-sd` | Enumerates Wi-Fi/hotspot adapters, renders crisp QR + 2-digit match code, and advertises over mDNS. |
| Android QR & Lifecycle | Kotlin + Jetpack Compose + `zxing-android-embedded` + `NsdManager` | Offline-capable camera QR scan, number-match dialog, mDNS/gateway probing, and foreground service locks. |
| Android Audio Engine | C++17 + `Oboe` + `libopus` + `AdaptiveBuffer` | Exclusive-mode low-latency output decoupled from network/decode threads via JNI. |

## 3. Core Subsystems & Visual Feature Flows

### [FLOW: QR-And-Number-Match-Handshake] | Aliases: qr pairing, number match, hotspot discovery, mdns
> **1-Line Summary:** Pairs phone and PC across Wi-Fi, Windows Mobile Hotspot, or Phone Hotspot using either an `as2p://pair` QR scan or a 3-choice 2-digit number match.
* **Links:** `[FEATURE: QR-And-Number-Match-Pairing]`, `[SUBSYSTEM: Audio-Transport-And-Sync]`, `[TRAP: Pairing-Replies-Must-Use-Sender-Port]`
* **Plain-English Walkthrough:**
  1. `PairingSession` on PC gathers active private IPv4s (prioritizing `192.168.137.1` hotspot), builds `as2p://pair?...`, renders an RGBA QR image in Slint, and shows `Pair Code: XX`.
  2. Scanning the QR sends `AS2P_QR_PAIR|<token>` directly to all listed IPs in parallel; the reachable IP replies `AS2P_PAIR_ACK` directly to the scanner's ephemeral socket.
  3. Choosing Number Match probes interface broadcasts, active link gateways, `192.168.137.1`, and `_as2p._udp.` mDNS hosts with `AS2P_PAIR_PROBE`, displays the server's 3 shuffled numbers on Android, and verifies the tapped choice via `AS2P_PAIR_VERIFY`.
* **Code Anchors:**
  * `server/src/pairing.rs :: PairingSession`
  * `server/src/pairing.rs :: list_candidate_ipv4s`
  * `server/src/ui.rs :: run_ui`
  * `android/app/src/main/kotlin/com/example/audiobtbridge/PairingProtocol.kt :: PairingProtocol`
  * `android/app/src/main/kotlin/com/example/audiobtbridge/PairingClient.kt :: PairingClient`
  * `android/app/src/main/kotlin/com/example/audiobtbridge/MainActivity.kt :: MainScreen`

### [SUBSYSTEM: Audio-Transport-And-Sync] | Aliases: wasapi loopback, opus, adaptive jitter buffer, v9 packet, oboe
> **1-Line Summary:** End-to-end Windows WASAPI capture, Opus UDP v9 streaming with on-demand retransmission, and Android 5-state adaptive Oboe playback.
* **Links:** `[FEATURE: Adaptive-Opus-Streaming]`, `[FEATURE: Low-Latency-Oboe-Playback]`, `[FEATURE: MMCSS-WASAPI-Capture]`, `[FLOW: QR-And-Number-Match-Handshake]`, `[WISHLIST: Seamless-USB-ADB-And-Tethering]`, `[WISHLIST: Polished-PC-And-Android-UI]`, `[DECISION: Fork-AS2P-Over-AudioShare]`, `[DECISION: GitHub-First-Public-Repo]`, `[TRAP: JNI-OnLoad-Extern-C]`, `[TRAP: WASAPI-Loopback-Silence-Stall]`, `[TRAP: Android-USB-Needs-ADB-Or-Tethering]`
* **Plain-English Walkthrough:** The Windows server listens on `0.0.0.0:12345`, handles pairing/discovery and `AS2P_CONFIG`, and spawns a decoupled capture/encode worker sending v9 packets (`media_tick`, `send_ts_ms`, `flags`, `seq`). Android feeds packets into `AdaptiveBuffer`, requests resends (`AS2P_RESEND`) on gaps, and plays PCM via Oboe while reporting `AS2P_STATS` every second.
* **Code Anchors:**
  * `server/src/main.rs :: main`
  * `server/src/audio.rs :: MmcssGuard`
  * `server/src/encoder.rs :: encode_frame`
  * `server/src/network.rs :: UdpSender`
  * `server/src/discovery.rs :: DiscoveryServer`
  * `android/app/src/main/kotlin/com/example/audiobtbridge/AudioService.kt :: AudioService`
  * `android/app/src/main/cpp/adaptive_buffer.h :: AdaptiveBuffer`

## 4. Recipes: How to Add New Things Safely
* **To extend pairing payload fields:**
  1. Add optional query parameter in `server/src/pairing.rs :: build_qr_uri`.
  2. Parse safely with fallback default in `android/app/src/main/kotlin/com/example/audiobtbridge/PairingProtocol.kt :: parseQrPayload` and cover in `PairingProtocolTest.kt`.
