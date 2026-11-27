# Project Operating Instructions (`AGENTS.md`)
> **Notice:** This project inherits user profile, confirmation gates, OS-default vs. Shared Libraries/Ecosystem Registry rules, GitHub-first CI/CD defaults, Top-Secret Local-Only rules, closed-loop `AUDITS.md`/`UIUX.md` and `Downloads\` report routing, pre-commit doc sync, and visual/doc standards from master `AGENTS.md`. Do not duplicate those rules here.

## 1. Repository Classification & Tech Stack
* **Project Name:** Mobile Speaker
* **Verification Mode:** `[Top-Secret Local-Only — NO REMOTE, run local-ci.ps1 only]` *(Defaulted to Local-Only per initial local repo request; switch to `ci.yml` only if user explicitly requests GitHub remote)*
* **Runtime / Language:** *(Pending Path B confirmation — Go 1.27.4, Python 3.15.0, Node.js v24.21.0, and Android SDK `adb` 37.0.1 available locally)*
* **Framework / Host:** *(Pending Path B confirmation — Windows PC sender + Android receiver/PWA)*
* **Target OS / Build Output:** Windows x64 PC sender + Android phone speaker receiver
* **Shared Libraries & Local Ports:** `%LOCALAPPDATA%\SharedLibraries\qrcode\qrcode.min.js` (`qrcode-generator` v1.4.4, MIT); avoid ports already listed in `%USERPROFILE%\.local\share\ecosystem-registry.json` (`8000`, `8001`, `8005`, `8317`, `8765`, `8770`, `8780`, `8790`, `8855`, `27125`, `27130`, `53317`, `59432`). Planned signaling port: `8860` (unregistered — add to `ecosystem-registry.json` once runtime is implemented).

## 2. Verification & Build Commands (Mirrored in CI)
*(Top-Secret Local-Only repo wires stages 01–07 into `local-ci.ps1` once stack is chosen. Stage 08 runs immediately.)*
* **Format & Lint:** *(Pending stack selection)*
* **Static Type Check:** *(Pending stack selection)*
* **Unit Tests:** *(Pending stack selection)*
* **Integration Tests:** *(Pending stack selection)*
* **E2E / UI Tests:** *(Pending stack selection)*
* **Security & Secret Scan:** *(Pending stack selection)*
* **Build & Smoke Test:** *(Pending stack selection)*
* **Structure & Doc Parity:** `powershell -NoProfile -ExecutionPolicy Bypass -File .\local-ci.ps1` (`local-ci.ps1 :: Test-RepoStructureAndDocParity` checks UTF-8 no-BOM, doc headers, `[TYPE: Slug]` cross-links, and live `path :: symbol` anchors)

## 3. Repository Directory Structure & Conventions
* `local-ci.ps1` — Local Windows verification runner saving patch-ready failure reports to `%USERPROFILE%\Downloads\`.
* `docs/` — Interconnected Markdown suite (`PRODUCT.md`, `ARCHITECTURE.md`, `DECISIONS_AND_TRAPS.md`). Scan dynamically via `grep -n -A 1 "^### \[" docs/` and read only matched line slices.
* `preview/` — On-demand HTML/CSS/JS visual previews, created beside `docs/` only when requested.

## 4. Critical Trap Pointers (1-Line Warnings Only)
*(Key directly by `[TRAP: <Slug>]`; put full rationale and `Protected Code Anchors` in `docs/DECISIONS_AND_TRAPS.md`.)*
* **`[TRAP: WASAPI-Loopback-Silence-Stall]`:** Never assume WASAPI loopback emits continuous frames when Windows is silent; inject synthetic silence or handle timestamp gaps cleanly so Opus/jitter buffers stay in sync.
* **`[TRAP: Android-USB-Needs-ADB-Or-Tethering]`:** Never assume raw USB audio class works on unrooted Android phones; wired PC-to-phone streaming requires `adb reverse`/`adb forward` or USB RNDIS tethering.
