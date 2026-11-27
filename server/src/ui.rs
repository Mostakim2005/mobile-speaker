use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use tokio::sync::mpsc as tokio_mpsc;
use crossbeam_channel::Receiver as CrossbeamReceiver;
use slint::{ComponentHandle, Image, Rgba8Pixel, SharedPixelBuffer};
use windows::Win32::Foundation::ERROR_SUCCESS;
use windows::Win32::System::Registry::{RegGetValueW, HKEY_CURRENT_USER, RRF_RT_REG_DWORD};
use windows::Win32::UI::WindowsAndMessaging::{PostThreadMessageW, WM_NULL, FindWindowW, ShowWindow, SW_HIDE};
use windows::core::w;

slint::slint! {
    component M3PillButton inherits Rectangle {
        in property <string> label;
        in property <color> bg_color;
        in property <color> bg_hover;
        in property <color> fg_color;
        in property <color> stroke_color: transparent;
        in property <length> stroke_width: 0px;
        in property <length> btn_height: 44px;
        callback clicked();

        height: btn_height;
        border-radius: btn_height / 2;
        background: area.has-hover ? bg_hover : bg_color;
        border-color: stroke_color;
        border-width: stroke_width;

        area := TouchArea {
            mouse-cursor: pointer;
            clicked => { root.clicked(); }
        }

        Text {
            text: root.label;
            color: root.fg_color;
            font-size: 13px;
            font-weight: 700;
            horizontal-alignment: center;
            vertical-alignment: center;
        }
    }

    component M3ToggleRow inherits Rectangle {
        in property <string> title;
        in property <string> subtitle;
        in-out property <bool> checked: false;
        in property <color> card_bg;
        in property <color> title_color;
        in property <color> sub_color;
        in property <color> accent_bg;
        in property <color> accent_fg;
        in property <color> track_off_bg;
        in property <color> border_col;
        callback toggled(bool);

        height: 48px;
        border-radius: 14px;
        background: card_bg;
        border-width: 1px;
        border-color: border_col;

        TouchArea {
            mouse-cursor: pointer;
            clicked => {
                root.checked = !root.checked;
                root.toggled(root.checked);
            }
        }

        HorizontalLayout {
            padding-left: 14px;
            padding-right: 14px;
            spacing: 10px;
            alignment: space-between;

            VerticalLayout {
                alignment: center;
                spacing: 1px;
                Text {
                    text: root.title;
                    color: root.title_color;
                    font-size: 13px;
                    font-weight: 600;
                }
                if root.subtitle != "" : Text {
                    text: root.subtitle;
                    color: root.sub_color;
                    font-size: 10px;
                }
            }

            Rectangle {
                width: 44px;
                height: 24px;
                y: (parent.height - self.height) / 2;
                border-radius: 12px;
                background: root.checked ? root.accent_bg : root.track_off_bg;

                Rectangle {
                    width: 18px;
                    height: 18px;
                    border-radius: 9px;
                    y: 3px;
                    x: root.checked ? 23px : 3px;
                    background: root.checked ? root.accent_fg : rgb(255, 255, 255);
                }
            }
        }
    }

    export component AppWindow inherits Window {
        in property <bool> is_dark: true;
        in property <string> status_text: "Ready";
        in property <bool> is_streaming: false;
        in property <bool> has_client: false;
        in property <string> bitrate_text: "160000 bps";
        in property <string> packets_text: "0 packets";
        in property <string> client_text: "Waiting for phone";
        in property <image> qr_image;
        in property <string> pair_code_text: "--";
        in property <string> network_ips_text: "Detecting networks...";
        in property <string> copy_feedback_text: "";
        in property <int> refresh_counter: 0;
        in-out property <bool> redundancy_enabled: false;
        in-out property <bool> startup_enabled: false;
        in-out property <bool> require_pairing_enabled: false;

        callback toggle_server();
        callback toggle_redundancy(bool);
        callback toggle_startup(bool);
        callback toggle_require_pairing(bool);
        callback refresh_pairing();
        callback copy_pair_code();

        // Google Cobalt Material 3 Tonal Tokens (Light & Dark parity via rgb() to avoid Rust literal collisions)
        property <color> bg_surface: root.is_dark ? rgb(11, 15, 23) : rgb(246, 248, 252);
        property <color> card_surface: root.is_dark ? rgb(18, 24, 38) : rgb(255, 255, 255);
        property <color> tonal_surface: root.is_dark ? rgb(25, 34, 52) : rgb(237, 242, 250);
        property <color> border_subtle: root.is_dark ? rgb(51, 65, 85) : rgb(203, 213, 225);
        property <color> text_primary: root.is_dark ? rgb(241, 245, 249) : rgb(17, 24, 39);
        property <color> text_secondary: root.is_dark ? rgb(203, 213, 225) : rgb(71, 85, 105);
        property <color> text_muted: root.is_dark ? rgb(148, 163, 184) : rgb(82, 96, 117);
        property <color> accent: root.is_dark ? rgb(147, 197, 253) : rgb(29, 78, 216);
        property <color> accent_hover: root.is_dark ? rgb(191, 219, 254) : rgb(30, 64, 175);
        property <color> accent_text: root.is_dark ? rgb(11, 18, 32) : rgb(255, 255, 255);
        property <color> accent_soft: root.is_dark ? rgb(30, 58, 138) : rgb(219, 234, 254);
        property <color> accent_soft_text: root.is_dark ? rgb(219, 234, 254) : rgb(30, 58, 138);
        property <color> ok_bg: root.is_dark ? rgb(6, 78, 59) : rgb(209, 250, 229);
        property <color> ok_fg: root.is_dark ? rgb(52, 211, 153) : rgb(4, 120, 87);
        property <color> ok_text: root.is_dark ? rgb(209, 250, 229) : rgb(6, 95, 70);
        property <color> danger_bg: root.is_dark ? rgb(127, 29, 29) : rgb(254, 226, 226);
        property <color> danger_hover: root.is_dark ? rgb(153, 27, 27) : rgb(254, 202, 202);
        property <color> danger_fg: root.is_dark ? rgb(254, 226, 226) : rgb(185, 28, 28);

        title: "AS2P_SERVER_UI";
        icon: @image-url("as2p.png");
        width: 440px;
        height: 700px;
        background: root.bg_surface;

        Rectangle {
            width: 100%;
            height: 100%;
            background: root.refresh_counter >= 0 ? root.bg_surface : root.bg_surface;

            VerticalLayout {
                padding: 20px;
                spacing: 14px;

                // Header Row
                HorizontalLayout {
                    alignment: space-between;
                    spacing: 12px;

                    HorizontalLayout {
                        spacing: 10px;
                        Image {
                            source: @image-url("as2p.png");
                            width: 40px;
                            height: 40px;
                            y: (parent.height - self.height) / 2;
                        }
                        VerticalLayout {
                            alignment: center;
                            Text {
                                text: "Mobile Speaker";
                                font-size: 19px;
                                font-weight: 800;
                                color: root.text_primary;
                            }
                            Text {
                                text: root.has_client ? ("Streaming · " + root.client_text) : "PC Audio Bridge";
                                font-size: 11px;
                                color: root.text_muted;
                            }
                        }
                    }

                    // Status Pill
                    Rectangle {
                        height: 30px;
                        border-radius: 15px;
                        y: (parent.height - self.height) / 2;
                        background: root.has_client ? root.ok_bg : (root.is_streaming ? root.accent_soft : root.tonal_surface);
                        HorizontalLayout {
                            padding-left: 12px;
                            padding-right: 12px;
                            spacing: 6px;
                            Rectangle {
                                width: 8px;
                                height: 8px;
                                border-radius: 4px;
                                y: (parent.height - self.height) / 2;
                                background: root.has_client ? root.ok_fg : (root.is_streaming ? root.accent : root.text_muted);
                            }
                            Text {
                                text: root.has_client ? "Connected" : root.status_text;
                                color: root.has_client ? root.ok_text : (root.is_streaming ? root.accent_soft_text : root.text_secondary);
                                font-size: 12px;
                                font-weight: 700;
                                vertical-alignment: center;
                            }
                        }
                    }
                }

                // QR + Number-Match Tonal Card
                Rectangle {
                    background: root.card_surface;
                    border-radius: 24px;
                    border-width: 1px;
                    border-color: root.border_subtle;

                    VerticalLayout {
                        padding: 18px;
                        spacing: 12px;
                        alignment: center;

                        Text {
                            text: "Scan QR or Match Number in Mobile Speaker";
                            color: root.text_secondary;
                            font-size: 12px;
                            font-weight: 600;
                            horizontal-alignment: center;
                        }

                        HorizontalLayout {
                            alignment: center;
                            Rectangle {
                                width: 184px;
                                height: 184px;
                                background: rgb(255, 255, 255);
                                border-radius: 16px;
                                border-width: 1px;
                                border-color: root.border_subtle;
                                Image {
                                    source: root.qr_image;
                                    width: 170px;
                                    height: 170px;
                                    x: 7px;
                                    y: 7px;
                                }
                            }
                        }

                        HorizontalLayout {
                            alignment: center;
                            spacing: 10px;

                            Rectangle {
                                background: root.accent_soft;
                                border-radius: 18px;
                                height: 36px;
                                HorizontalLayout {
                                    padding-left: 16px;
                                    padding-right: 16px;
                                    Text {
                                        text: "Match Code: " + root.pair_code_text;
                                        color: root.accent_soft_text;
                                        font-size: 15px;
                                        font-weight: 800;
                                        vertical-alignment: center;
                                    }
                                }
                            }

                            M3PillButton {
                                width: 92px;
                                btn_height: 36px;
                                label: root.copy_feedback_text != "" ? root.copy_feedback_text : "Copy Code";
                                bg_color: root.tonal_surface;
                                bg_hover: root.border_subtle;
                                fg_color: root.text_primary;
                                stroke_color: root.border_subtle;
                                stroke_width: 1px;
                                clicked => { root.copy_pair_code(); }
                            }

                            M3PillButton {
                                width: 92px;
                                btn_height: 36px;
                                label: "New QR";
                                bg_color: root.tonal_surface;
                                bg_hover: root.border_subtle;
                                fg_color: root.text_primary;
                                stroke_color: root.border_subtle;
                                stroke_width: 1px;
                                clicked => { root.refresh_pairing(); }
                            }
                        }

                        Text {
                            text: "Reachable IPs: " + root.network_ips_text;
                            color: root.text_muted;
                            font-size: 11px;
                            horizontal-alignment: center;
                            wrap: word-wrap;
                        }
                    }
                }

                // Primary Hero Pill Action
                M3PillButton {
                    btn_height: 48px;
                    label: root.is_streaming ? "Stop Listening" : "Start Listening";
                    bg_color: root.is_streaming ? root.danger_bg : root.accent;
                    bg_hover: root.is_streaming ? root.danger_hover : root.accent_hover;
                    fg_color: root.is_streaming ? root.danger_fg : root.accent_text;
                    clicked => { root.toggle_server(); }
                }

                // Settings Section
                VerticalLayout {
                    spacing: 8px;

                    M3ToggleRow {
                        title: "Require QR / Code Pairing";
                        subtitle: "Blocks unpaired devices on shared Wi-Fi";
                        checked <=> root.require_pairing_enabled;
                        card_bg: root.card_surface;
                        title_color: root.text_primary;
                        sub_color: root.text_muted;
                        accent_bg: root.accent;
                        accent_fg: root.accent_text;
                        track_off_bg: root.border_subtle;
                        border_col: root.border_subtle;
                        toggled(val) => { root.toggle_require_pairing(val); }
                    }

                    M3ToggleRow {
                        title: "Double Send Redundancy";
                        subtitle: "Extra packet backup on noisy Wi-Fi";
                        checked <=> root.redundancy_enabled;
                        card_bg: root.card_surface;
                        title_color: root.text_primary;
                        sub_color: root.text_muted;
                        accent_bg: root.accent;
                        accent_fg: root.accent_text;
                        track_off_bg: root.border_subtle;
                        border_col: root.border_subtle;
                        toggled(val) => { root.toggle_redundancy(val); }
                    }

                    M3ToggleRow {
                        title: "Run at Windows Startup";
                        subtitle: "Start silently in system tray";
                        checked <=> root.startup_enabled;
                        card_bg: root.card_surface;
                        title_color: root.text_primary;
                        sub_color: root.text_muted;
                        accent_bg: root.accent;
                        accent_fg: root.accent_text;
                        track_off_bg: root.border_subtle;
                        border_col: root.border_subtle;
                        toggled(val) => { root.toggle_startup(val); }
                    }
                }

                // Footer Telemetry
                HorizontalLayout {
                    alignment: space-between;
                    Text { text: "Bitrate: " + root.bitrate_text; color: root.text_muted; font-size: 11px; }
                    Text { text: "Minimized to Tray on Close"; color: root.text_muted; font-size: 11px; }
                }
            }
        }
    }
}

pub enum UiMessage {
    UpdateStats { packets: u64, bitrate: i32, client_ip: Option<String> },
    SyncRedundancy(bool),
    UpdatePairing {
        qr_rgba: Vec<u8>,
        qr_size: u32,
        match_code: u8,
        ips_summary: String,
    },
}

#[derive(Clone, Copy, Debug)]
pub enum UiCommand {
    ToggleServer,
    SetRedundancy(bool),
    SetRequirePairing(bool),
    RefreshPairing,
}

pub struct UiState {
    pub is_running: Arc<AtomicBool>,
    pub stats_rx: CrossbeamReceiver<UiMessage>,
    pub cmd_tx: tokio_mpsc::Sender<UiCommand>,
    pub is_ui_visible: Arc<AtomicBool>,
    pub main_thread_id: u32,
}

fn detect_windows_dark_mode() -> bool {
    let mut buffer: u32 = 0;
    let mut cb_data: u32 = std::mem::size_of::<u32>() as u32;
    let status = unsafe {
        RegGetValueW(
            HKEY_CURRENT_USER,
            w!(r"Software\Microsoft\Windows\CurrentVersion\Themes\Personalize"),
            w!("AppsUseLightTheme"),
            RRF_RT_REG_DWORD,
            None,
            Some(&mut buffer as *mut u32 as *mut std::ffi::c_void),
            Some(&mut cb_data),
        )
    };
    if status == ERROR_SUCCESS {
        buffer == 0
    } else {
        true
    }
}

fn check_startup_status() -> bool {
    if let Some(mut path) = dirs::config_dir() {
        path.push("Microsoft");
        path.push("Windows");
        path.push("Start Menu");
        path.push("Programs");
        path.push("Startup");
        path.push("AS2P.lnk");
        return path.exists();
    }
    false
}

fn set_startup(enabled: bool) {
    if let Some(mut path) = dirs::config_dir() {
        path.push("Microsoft");
        path.push("Windows");
        path.push("Start Menu");
        path.push("Programs");
        path.push("Startup");
        path.push("AS2P.lnk");

        if enabled {
            if let Ok(exe_path) = std::env::current_exe() {
                let lnk = mslnk::ShellLink::new(&exe_path).unwrap();
                let _ = lnk.create_lnk(&path);
            }
        } else {
            let _ = std::fs::remove_file(path);
        }
    }
}

pub fn run_ui(state: UiState, handle_tx: crossbeam_channel::Sender<slint::Weak<AppWindow>>) {
    unsafe { std::env::set_var("SLINT_BACKEND", "software"); }
    let window = AppWindow::new().expect("Failed to create Slint window");

    let _ = handle_tx.send(window.as_weak());

    window.set_is_dark(detect_windows_dark_mode());
    window.set_startup_enabled(check_startup_status());

    let is_visible_on_close = state.is_ui_visible.clone();
    let main_tid = state.main_thread_id;
    window.window().on_close_requested(move || {
        if let Ok(hwnd) = unsafe { FindWindowW(None, w!("AS2P_SERVER_UI")) } {
            if !hwnd.0.is_null() {
                unsafe { let _ = ShowWindow(hwnd, SW_HIDE); }
            }
        }
        is_visible_on_close.store(false, Ordering::SeqCst);
        unsafe { let _ = PostThreadMessageW(main_tid, WM_NULL, None, None); }
        slint::CloseRequestResponse::KeepWindowShown
    });

    let cmd_tx_ui = state.cmd_tx.clone();
    window.on_toggle_server(move || { let _ = cmd_tx_ui.try_send(UiCommand::ToggleServer); });

    let cmd_tx_redundancy = state.cmd_tx.clone();
    window.on_toggle_redundancy(move |enabled| { let _ = cmd_tx_redundancy.try_send(UiCommand::SetRedundancy(enabled)); });

    let cmd_tx_pair_req = state.cmd_tx.clone();
    window.on_toggle_require_pairing(move |enabled| { let _ = cmd_tx_pair_req.try_send(UiCommand::SetRequirePairing(enabled)); });

    let cmd_tx_refresh = state.cmd_tx.clone();
    window.on_refresh_pairing(move || { let _ = cmd_tx_refresh.try_send(UiCommand::RefreshPairing); });

    let window_weak_copy = window.as_weak();
    let copy_clear_ticks = Arc::new(std::sync::atomic::AtomicU8::new(0));
    let copy_ticks_clone = copy_clear_ticks.clone();
    window.on_copy_pair_code(move || {
        if let Some(ui) = window_weak_copy.upgrade() {
            let code = ui.get_pair_code_text().to_string();
            if let Ok(mut cb) = arboard::Clipboard::new() {
                if cb.set_text(code).is_ok() {
                    ui.set_copy_feedback_text("Copied!".into());
                    copy_ticks_clone.store(8, Ordering::SeqCst);
                }
            }
        }
    });

    let window_weak_startup = window.as_weak();
    window.on_toggle_startup(move |enabled| {
        set_startup(enabled);
        let ui_handle = window_weak_startup.clone();
        let _ = slint::invoke_from_event_loop(move || {
            if let Some(ui) = ui_handle.upgrade() {
                let old = ui.get_refresh_counter();
                ui.set_refresh_counter(old + 1);
                ui.window().request_redraw();
            }
        });
    });

    let window_weak = window.as_weak();
    let is_running_timer = state.is_running.clone();
    let stats_rx_timer = state.stats_rx.clone();
    let timer = slint::Timer::default();
    let mut theme_poll_counter: u8 = 0;

    window.set_refresh_counter(1);

    timer.start(slint::TimerMode::Repeated, std::time::Duration::from_millis(250), move || {
        if let Some(ui) = window_weak.upgrade() {
            theme_poll_counter = theme_poll_counter.wrapping_add(1);
            if theme_poll_counter % 8 == 0 {
                ui.set_is_dark(detect_windows_dark_mode());
            }

            let left = copy_clear_ticks.load(Ordering::SeqCst);
            if left > 0 {
                let next = left - 1;
                copy_clear_ticks.store(next, Ordering::SeqCst);
                if next == 0 {
                    ui.set_copy_feedback_text("".into());
                }
            }

            let running = is_running_timer.load(Ordering::SeqCst);
            ui.set_is_streaming(running);
            ui.set_status_text(if running { "Listening".into() } else { "Paused".into() });
            while let Ok(msg) = stats_rx_timer.try_recv() {
                match msg {
                    UiMessage::UpdateStats { packets, bitrate, client_ip } => {
                        ui.set_packets_text(format!("{} packets", packets).into());
                        if bitrate > 0 {
                            ui.set_bitrate_text(format!("{} kbps", bitrate / 1000).into());
                        } else {
                            ui.set_bitrate_text("160 kbps".into());
                        }
                        let has = client_ip.is_some();
                        ui.set_has_client(has);
                        ui.set_client_text(client_ip.unwrap_or_else(|| "Waiting for phone".to_string()).into());
                    }
                    UiMessage::SyncRedundancy(enabled) => {
                        ui.set_redundancy_enabled(enabled);
                    }
                    UiMessage::UpdatePairing { qr_rgba, qr_size, match_code, ips_summary } => {
                        if qr_size > 0 && qr_rgba.len() == (qr_size * qr_size * 4) as usize {
                            let buf = SharedPixelBuffer::<Rgba8Pixel>::clone_from_slice(
                                &qr_rgba,
                                qr_size,
                                qr_size,
                            );
                            ui.set_qr_image(Image::from_rgba8(buf));
                        }
                        ui.set_pair_code_text(format!("{:02}", match_code).into());
                        ui.set_network_ips_text(ips_summary.into());
                    }
                }
            }
        }
    });

    window.run().expect("Slint event loop error");
}
