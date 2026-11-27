# Product Intent & Feature Map (`PRODUCT.md`)
> **1-Line Summary:** Permanent source of truth for Mobile Speaker (forked from AS2P), linking active audio-sync and QR/number-match pairing features to architecture flows and verification tests.

## 1. Core Vision & User Experience Intent
* **What This App Does (Plain English):** Turns an Android phone into a low-latency, plug-and-play speaker for a Windows PC over Wi-Fi, PC/Phone hotspots, or USB cable — pairing effortlessly via QR code or Google-style number matching and staying automatically synced for YouTube, movies, lectures, and music.
* **How It Must Feel to Use:** Open on PC, scan a QR code or tap the matching number on your phone, and immediately hear crisp, synchronized sound with zero manual IP typing or buffer fiddling.

## 2. Active Features (Do Not Break)

### [FEATURE: QR-And-Number-Match-Pairing] | Aliases: qr code, number matching, google style pairing, hotspot
> **1-Line Summary:** PC displays a live multi-IP QR code and 2-digit match number across Wi-Fi and hotspots so the phone pairs via 1-scan QR or Google-style 3-number tap and starts streaming automatically.
* **Links:** `[FLOW: QR-And-Number-Match-Handshake]`, `[SUBSYSTEM: Audio-Transport-And-Sync]`, `[TRAP: Pairing-Replies-Must-Use-Sender-Port]`
* **User Intention (Non-Negotiable):** Connect reliably across home Wi-Fi, Windows Mobile Hotspot (`192.168.137.1`), or Phone Hotspot without typing IP addresses or relying on UDP broadcast alone.
* **Visual / UI Behavior:** PC shows the crisp QR card, `Pair Code: XX`, active adapter IPs, `New Code / QR` button, and optional `Require QR / Code Pairing` lock; Android shows `SCAN QR` and `NUMBER MATCH` buttons plus a 3-choice confirmation dialog.
* **Verified By:** `server/src/pairing.rs :: qr_uri_includes_all_ips_and_token` and `android/app/src/test/java/com/example/audiobtbridge/PairingProtocolTest.kt :: PairingProtocolTest`

### [FEATURE: Adaptive-Opus-Streaming] | Aliases: jitter buffer, retransmission, v9 header, opus, auto-sync
> **1-Line Summary:** Streams 48 kHz stereo Opus frames over UDP with a 5-state adaptive jitter buffer and on-demand packet retransmission so Wi-Fi playback stays in sync without manual tweaking.
* **Links:** `[SUBSYSTEM: Audio-Transport-And-Sync]`, `[DECISION: Fork-AS2P-Over-AudioShare]`
* **User Intention (Non-Negotiable):** Audio must stay lip-synced during YouTube/video playback and adapt gracefully to messy Wi-Fi without permanently ratcheting up latency.
* **Visual / UI Behavior:** Displays live latency readout/chart and preset selector (`LOW_LATENCY`, `BALANCE`, `HIGH_QUALITY`, `BEST_QUALITY`) in the Android client.
* **Verified By:** `server/src/network.rs :: audio_packet_layout_is_stable` and `android/app/src/test/cpp/adaptive_buffer_test.cpp :: test_single_underrun_is_transient`

### [FEATURE: Low-Latency-Oboe-Playback] | Aliases: oboe, foreground service, wake lock, background audio
> **1-Line Summary:** Plays decoded PCM via C++ Google Oboe on a dedicated high-priority thread inside an Android foreground service with WakeLock and MulticastLock so audio survives screen lock.
* **Links:** `[SUBSYSTEM: Audio-Transport-And-Sync]`, `[TRAP: JNI-OnLoad-Extern-C]`
* **User Intention (Non-Negotiable):** Phone acts like a real speaker — turning the screen off or switching apps must not kill or stutter playback.
* **Visual / UI Behavior:** Persistent MediaStyle notification with live status and Stop button.
* **Verified By:** `android/app/src/test/java/com/example/audiobtbridge/ConnectionManagerTest.kt :: ConnectionManager`

### [FEATURE: MMCSS-WASAPI-Capture] | Aliases: wasapi loopback, mmcss, pro audio, resampling
> **1-Line Summary:** Captures Windows default speaker output via WASAPI loopback on a Pro Audio MMCSS thread with automatic resampling to 48 kHz and anti-clipping fades.
* **Links:** `[SUBSYSTEM: Audio-Transport-And-Sync]`, `[TRAP: WASAPI-Loopback-Silence-Stall]`
* **User Intention (Non-Negotiable):** Captures whatever is playing on PC cleanly without virtual audio cables or clicks on device switch.
* **Visual / UI Behavior:** Tray-first Windows Slint window with Start/Stop toggle and live connection status.
* **Verified By:** `server/src/encoder.rs :: test_validate_config_normal`

## 3. Planned Features & App-Merge Wishlist

### [WISHLIST: Seamless-USB-ADB-And-Tethering] | Aliases: usb wire, adb reverse, usb tethering, auto-switch
> **1-Line Summary:** Supports both USB tethering (native UDP) and automatic `adb reverse` TCP tunneling so plugging in a USB cable pairs and streams cleanly even without turning on tethering.
* **Links:** `[SUBSYSTEM: Audio-Transport-And-Sync]`, `[TRAP: Android-USB-Needs-ADB-Or-Tethering]`
* **User Intention:** Plug-and-play wired fallback when Wi-Fi is unavailable or ultra-low latency is wanted.

### [WISHLIST: Polished-PC-And-Android-UI] | Aliases: slint ui, compose ui, dark light theme, qr window
> **1-Line Summary:** Adds final visual polish, light/dark surface refinements, and one-tap saved-PC profile switching across desktop and mobile.
* **Links:** `[SUBSYSTEM: Audio-Transport-And-Sync]`
* **User Intention:** Make pairing and daily playback effortless and clean on both Windows and Android.
