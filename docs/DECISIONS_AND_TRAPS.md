# Architectural Decisions, Waivers & Landmines (`DECISIONS_AND_TRAPS.md`)
> **1-Line Summary:** Records architectural decisions, OSS Build-vs-Fork evaluations, and non-obvious Windows/Android audio streaming landmines.

## 1. Architectural Decisions, Pivots & Audit Waivers

### [DECISION: Local-Only-Repo-Bootstrap] | Aliases: local-ci, no remote, repo initialization, polyform
> **1-Line Summary:** Initialized Mobile Speaker as a Local-Only repository with `local-ci.ps1` and PolyForm Noncommercial 1.0.0 licensing pending Path B stack confirmation.
* **Links:** `[SUBSYSTEM: Audio-Transport-And-Sync]`
* **Decision Maker:** `[DECIDED BY: HUMAN]` *(Explicit request to set up a local repo via `.agent-templates`)*
* **Options Considered:**
  * **Rejected / Waived:** Creating a GitHub remote or `.github/workflows/ci.yml` unprompted — violates Local-Only safety rules when no remote is configured.
  * **Chosen:** Local git repository with `local-ci.ps1` verification, interconnected `docs/`, `THIRD_PARTY_NOTICES.md`, and `LICENSE`.

---

## 2. Do-Not-Touch Engineering Landmines (Traps)

### [TRAP: WASAPI-Loopback-Silence-Stall] | Aliases: silence padding, stalled audio stream, wasapi idle
> **1-Line Summary:** Windows WASAPI loopback stops delivering audio buffers when no application is playing sound, which can stall receivers or corrupt jitter/drift estimators if treated as packet loss.
* **Links:** `[SUBSYSTEM: Audio-Transport-And-Sync]`, `[WISHLIST: Adaptive-Sync-And-Quality]`
* **Strict Rule:** **NEVER** rely solely on raw WASAPI loopback callbacks without a silence-keepalive or discontinuous-timestamp (DTX) marker.
* **Why This Trap Exists (Plain English):** By design, Windows WASAPI loopback only fires capture events while an audio endpoint is actively rendering non-idle buffers. Without explicit silence pacing or DTX flags, the phone's jitter buffer either underruns repeatedly or miscalculates clock drift when playback resumes.
* **Protected Code Anchors:**
  * *(Pending implementation after Path B confirmation)*

### [TRAP: Android-USB-Needs-ADB-Or-Tethering] | Aliases: usb wire, aoa, adb reverse, localhost
> **1-Line Summary:** Unrooted Android phones cannot act as plug-and-play USB Audio Class speakers over a raw USB cable without ADB port forwarding or USB RNDIS tethering.
* **Links:** `[SUBSYSTEM: Audio-Transport-And-Sync]`, `[WISHLIST: Dual-Transport-WiFi-And-USB]`
* **Strict Rule:** **NEVER** promise driverless "plug USB cable and Android appears as a Windows sound card" on stock non-rooted Android; implement wired mode via `adb reverse`/`adb forward` (with USB debugging) or USB tethering subnet routing.
* **Why This Trap Exists (Plain English):** Android devices act as USB peripherals in MTP/ADB/RNDIS modes and do not expose a standard USB Audio Device Class (UAC2) sink gadget on stock unrooted kernels. Using `adb reverse` tunnels local TCP/WebSocket traffic cleanly over USB, while USB RNDIS tethering supports UDP directly over the tethered interface.
* **Protected Code Anchors:**
  * *(Pending implementation after Path B confirmation)*
