# Product Intent & Feature Map (`PRODUCT.md`)
> **1-Line Summary:** Permanent source of truth for what Mobile Speaker must do and feel like, turning an Android phone into a low-latency, auto-synced PC speaker over Wi-Fi or USB wire with QR pairing.

## 1. Core Vision & User Experience Intent
* **What This App Does (Plain English):** Turns an Android phone into an instant, high-quality external speaker for a Windows PC over Wi-Fi or USB cable, replacing proprietary SoundWire workflows with effortless QR-code pairing and automatic audio sync.
* **How It Must Feel to Use:** Open on PC, see a scannable QR code immediately, scan in the phone app, and hear crisp, lag-free PC audio within seconds. Works reliably across Wi-Fi jitter, wired USB fallback, and background screen-off playback without ads or manual IP typing.

## 2. Active Features (Do Not Break)
*(None implemented yet — awaiting Path B architecture & Build-vs-Fork confirmation before writing code.)*

## 3. Planned Features & App-Merge Wishlist

### [WISHLIST: QR-Instant-Pairing] | Aliases: qr code, scan to connect, zero-config pairing
> **1-Line Summary:** Opening the PC app displays a QR code containing connection endpoints and a short-lived session token so the phone connects on scan without typing IP addresses.
* **Links:** `[SUBSYSTEM: Audio-Transport-And-Sync]`
* **User Intention:** Eliminate manual IP/port entry and reconnect seamlessly when switching networks or cables.

### [WISHLIST: Dual-Transport-WiFi-And-USB] | Aliases: wireless, wired, adb tunnel, usb tethering
> **1-Line Summary:** Supports both wireless LAN (Wi-Fi) and wired USB connections, automatically preferring wired USB when plugged in for minimum latency and zero packet loss.
* **Links:** `[SUBSYSTEM: Audio-Transport-And-Sync]`, `[TRAP: Android-USB-Needs-ADB-Or-Tethering]`
* **User Intention:** Match and exceed SoundWire's Wi-Fi + USB flexibility without fragile manual firewall/ADB commands.

### [WISHLIST: Adaptive-Sync-And-Quality] | Aliases: jitter buffer, clock drift, latency, opus, soundwire replacement
> **1-Line Summary:** Automatically adapts jitter buffering and resamples clock drift so audio stays continuously in sync with video and gaming while preserving transparent Opus audio quality.
* **Links:** `[SUBSYSTEM: Audio-Transport-And-Sync]`, `[TRAP: WASAPI-Loopback-Silence-Stall]`
* **User Intention:** Keep lip-sync tight and prevent buffer bloat, crackles, or progressive drift over long listening sessions.

### [WISHLIST: Lateral-Companion-Controls] | Aliases: remote volume, mute, media keys, auto-reconnect, battery saver
> **1-Line Summary:** Optional companion features such as phone-side PC volume/mute control, per-app Windows audio routing, background foreground-service lock, and one-tap reconnect.
* **Links:** `[SUBSYSTEM: Audio-Transport-And-Sync]`
* **User Intention:** Give practical everyday convenience beyond raw SoundWire audio forwarding, selectable during Path B scoping.
