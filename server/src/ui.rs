use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use tokio::sync::mpsc as tokio_mpsc;
use crossbeam_channel::Receiver as CrossbeamReceiver;
use slint::{ComponentHandle, Image, Rgba8Pixel, SharedPixelBuffer};
use windows::Win32::UI::WindowsAndMessaging::{PostThreadMessageW, WM_NULL, FindWindowW, ShowWindow, SW_HIDE};
use windows::core::w;

slint::slint! {
    import { Button, VerticalBox, HorizontalBox } from "std-widgets.slint";

    export component AppWindow inherits Window {
        in property <string> status_text: "Idle";
        in property <bool> is_streaming: false;
        in property <string> bitrate_text: "128000 bps";
        in property <string> packets_text: "0 packets";
        in property <string> client_text: "None";
        in property <image> qr_image;
        in property <string> pair_code_text: "--";
        in property <string> network_ips_text: "Detecting networks...";
        in property <int> refresh_counter: 0;
        in-out property <bool> redundancy_enabled: false;
        in-out property <bool> startup_enabled: false;
        in-out property <bool> require_pairing_enabled: false;

        callback toggle_server();
        callback toggle_redundancy(bool);
        callback toggle_startup(bool);
        callback toggle_require_pairing(bool);
        callback refresh_pairing();

        title: "AS2P_SERVER_UI";
        icon: @image-url("as2p.png");
        width: 440px;
        height: 680px;
        background: #181a1f;

        Rectangle {
            width: 100%;
            height: 100%;
            background: root.refresh_counter >= 0 ? #181a1f : #181a1f;

            VerticalLayout {
                padding: 22px;
                spacing: 14px;
                alignment: center;

                HorizontalLayout {
                    alignment: center;
                    spacing: 12px;
                    Image {
                        source: @image-url("as2p.png");
                        width: 44px;
                        height: 44px;
                    }
                    VerticalLayout {
                        alignment: center;
                        Text {
                            text: "Mobile Speaker Server";
                            font-size: 20px;
                            font-weight: 800;
                            color: #ffffff;
                        }
                        HorizontalLayout {
                            spacing: 8px;
                            Rectangle {
                                width: 10px;
                                height: 10px;
                                border-radius: 5px;
                                background: root.is_streaming ? #22c55e : #ef4444;
                                y: (parent.height - self.height) / 2;
                            }
                            Text {
                                text: root.status_text;
                                color: root.is_streaming ? #4ade80 : #f87171;
                                font-size: 13px;
                                font-weight: 700;
                            }
                        }
                    }
                }

                // QR + Number-Match Pairing Card
                Rectangle {
                    background: #22262f;
                    border-radius: 14px;
                    border-width: 1px;
                    border-color: #323846;

                    VerticalLayout {
                        padding: 16px;
                        spacing: 10px;
                        alignment: center;

                        Text {
                            text: "Scan QR or Match Number on Phone";
                            color: #cbd5e1;
                            font-size: 13px;
                            font-weight: 600;
                            horizontal-alignment: center;
                        }

                        HorizontalLayout {
                            alignment: center;
                            Rectangle {
                                width: 184px;
                                height: 184px;
                                background: #ffffff;
                                border-radius: 10px;
                                Image {
                                    source: root.qr_image;
                                    width: 172px;
                                    height: 172px;
                                    x: 6px;
                                    y: 6px;
                                }
                            }
                        }

                        HorizontalLayout {
                            alignment: center;
                            spacing: 12px;

                            Rectangle {
                                background: #1d293b;
                                border-radius: 8px;
                                border-width: 1px;
                                border-color: #38bdf8;
                                height: 38px;
                                HorizontalLayout {
                                    padding-left: 14px;
                                    padding-right: 14px;
                                    alignment: center;
                                    Text {
                                        text: "Pair Code: " + root.pair_code_text;
                                        color: #38bdf8;
                                        font-size: 16px;
                                        font-weight: 800;
                                        vertical-alignment: center;
                                    }
                                }
                            }

                            Button {
                                text: "New Code / QR";
                                height: 38px;
                                clicked => { root.refresh_pairing(); }
                            }
                        }

                        Text {
                            text: "IPs: " + root.network_ips_text;
                            color: #94a3b8;
                            font-size: 11px;
                            horizontal-alignment: center;
                            wrap: word-wrap;
                        }
                    }
                }

                Button {
                    text: root.is_streaming ? "STOP LISTENING / STREAM" : "START LISTENING";
                    primary: true;
                    height: 44px;
                    clicked => { root.toggle_server(); }
                }

                // Toggles & Telemetry
                VerticalLayout {
                    spacing: 10px;
                    alignment: center;

                    HorizontalLayout {
                        alignment: center;
                        spacing: 10px;
                        Text { text: "Require QR / Code Pairing:"; color: #cbd5e1; font-size: 13px; vertical-alignment: center; }
                        Rectangle {
                            width: 20px;
                            height: 20px;
                            background: root.require_pairing_enabled ? #38bdf8 : #444444;
                            border-radius: 4px;
                            TouchArea {
                                clicked => {
                                    root.require_pairing_enabled = !root.require_pairing_enabled;
                                    root.toggle_require_pairing(root.require_pairing_enabled);
                                }
                            }
                            Text { text: root.require_pairing_enabled ? "✔" : ""; color: black; font-size: 13px; font-weight: 700; horizontal-alignment: center; vertical-alignment: center; }
                        }
                    }

                    HorizontalLayout {
                        alignment: center;
                        spacing: 10px;
                        Text { text: "Double Send Redundancy:"; color: #cbd5e1; font-size: 13px; vertical-alignment: center; }
                        Rectangle {
                            width: 20px;
                            height: 20px;
                            background: root.redundancy_enabled ? #22c55e : #444444;
                            border-radius: 4px;
                            TouchArea {
                                clicked => {
                                    root.redundancy_enabled = !root.redundancy_enabled;
                                    root.toggle_redundancy(root.redundancy_enabled);
                                }
                            }
                            Text { text: root.redundancy_enabled ? "✔" : ""; color: black; font-size: 13px; font-weight: 700; horizontal-alignment: center; vertical-alignment: center; }
                        }
                    }

                    HorizontalLayout {
                        alignment: center;
                        spacing: 10px;
                        Text { text: "Run at Startup:"; color: #cbd5e1; font-size: 13px; vertical-alignment: center; }
                        Rectangle {
                            width: 20px;
                            height: 20px;
                            background: root.startup_enabled ? #22c55e : #444444;
                            border-radius: 4px;
                            TouchArea {
                                clicked => {
                                    root.startup_enabled = !root.startup_enabled;
                                    root.toggle_startup(root.startup_enabled);
                                }
                            }
                            Text { text: root.startup_enabled ? "✔" : ""; color: black; font-size: 13px; font-weight: 700; horizontal-alignment: center; vertical-alignment: center; }
                        }
                    }

                    HorizontalLayout {
                        alignment: center;
                        spacing: 18px;
                        Text { text: "Bitrate: " + root.bitrate_text; color: #94a3b8; font-size: 12px; }
                        Text { text: "Client: " + root.client_text; color: #94a3b8; font-size: 12px; }
                    }
                }

                Text { text: "Closing minimizes to Windows System Tray"; font-size: 11px; color: #64748b; horizontal-alignment: center; }
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

    window.set_refresh_counter(1);

    timer.start(slint::TimerMode::Repeated, std::time::Duration::from_millis(250), move || {
        if let Some(ui) = window_weak.upgrade() {
            let running = is_running_timer.load(Ordering::SeqCst);
            ui.set_is_streaming(running);
            ui.set_status_text(if running { "Listening / Active".into() } else { "Idle".into() });
            while let Ok(msg) = stats_rx_timer.try_recv() {
                match msg {
                    UiMessage::UpdateStats { packets, bitrate, client_ip } => {
                        ui.set_packets_text(format!("{} packets", packets).into());
                        ui.set_bitrate_text(format!("{} bps", bitrate).into());
                        ui.set_client_text(client_ip.unwrap_or_else(|| "None".to_string()).into());
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
