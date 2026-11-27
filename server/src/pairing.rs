use std::collections::HashSet;
use std::net::{IpAddr, Ipv4Addr};
use qrcode::{Color, QrCode};
use rand::seq::SliceRandom;
use rand::Rng;

pub const QR_PAIR_PREFIX: &[u8] = b"AS2P_QR_PAIR|";
pub const PAIR_PROBE_PREFIX: &[u8] = b"AS2P_PAIR_PROBE";
pub const PAIR_VERIFY_PREFIX: &[u8] = b"AS2P_PAIR_VERIFY|";

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum PairingOutcome {
    ReplyOnly(Vec<u8>),
    Authorized { reply: Vec<u8> },
    Rejected { reply: Vec<u8> },
}

/// Manages QR-token and Google-style 2-digit number-matching state on the PC.
pub struct PairingSession {
    pub token: String,
    pub match_code: u8,
    pub choices: [u8; 3],
    pub challenge_id: String,
    pub host_name: String,
}

impl PairingSession {
    pub fn new(host_name: String) -> Self {
        let sanitized = sanitize_host_name(&host_name);
        let mut session = Self {
            token: String::new(),
            match_code: 10,
            choices: [10, 20, 30],
            challenge_id: String::new(),
            host_name: sanitized,
        };
        session.rotate();
        session
    }

    /// Generates a fresh session token, secret 2-digit code, and shuffled 3-number challenge.
    pub fn rotate(&mut self) {
        let mut rng = rand::thread_rng();
        let token_hi: u32 = rng.r#gen();
        let token_lo: u16 = rng.r#gen();
        self.token = format!("{:08x}{:04x}", token_hi, token_lo);

        let challenge_num: u32 = rng.r#gen();
        self.challenge_id = format!("{:08x}", challenge_num);

        let secret = rng.gen_range(10u8..=99u8);
        let mut decoys = HashSet::new();
        decoys.insert(secret);
        while decoys.len() < 3 {
            decoys.insert(rng.gen_range(10u8..=99u8));
        }

        let mut list: Vec<u8> = decoys.into_iter().collect();
        list.shuffle(&mut rng);
        self.match_code = secret;
        self.choices = [list[0], list[1], list[2]];
    }

    /// Encodes all candidate IPs, stream port, session token, and match code into a compact QR URI.
    pub fn build_qr_uri(&self, ips: &[Ipv4Addr], port: u16) -> String {
        let ip_list = if ips.is_empty() {
            "127.0.0.1".to_string()
        } else {
            ips.iter()
                .take(8)
                .map(|ip| ip.to_string())
                .collect::<Vec<_>>()
                .join(",")
        };
        format!(
            "as2p://pair?v=1&ips={}&p={}&t={}&c={}&n={}",
            ip_list, port, self.token, self.match_code, self.host_name
        )
    }

    /// Renders `payload` to an RGBA8 pixel buffer with a 4-module white quiet zone.
    pub fn render_qr_rgba(payload: &str, target_px: u32) -> Result<(u32, u32, Vec<u8>), String> {
        let code = QrCode::new(payload.as_bytes()).map_err(|e| e.to_string())?;
        let modules = code.width() as u32;
        let quiet = 4u32;
        let total_modules = modules + quiet * 2;
        let scale = (target_px / total_modules).max(2);
        let size = total_modules * scale;
        let mut rgba = vec![255u8; (size * size * 4) as usize];

        let colors = code.to_colors();
        for my in 0..modules {
            for mx in 0..modules {
                if colors[(my * modules + mx) as usize] == Color::Dark {
                    let px_start = (mx + quiet) * scale;
                    let py_start = (my + quiet) * scale;
                    for dy in 0..scale {
                        for dx in 0..scale {
                            let idx = (((py_start + dy) * size + (px_start + dx)) * 4) as usize;
                            rgba[idx] = 18;
                            rgba[idx + 1] = 18;
                            rgba[idx + 2] = 22;
                            rgba[idx + 3] = 255;
                        }
                    }
                }
            }
        }
        Ok((size, size, rgba))
    }

    /// Handles additive pairing control packets (`AS2P_QR_PAIR`, `AS2P_PAIR_PROBE`, `AS2P_PAIR_VERIFY`).
    pub fn handle_packet(&mut self, buf: &[u8]) -> Option<PairingOutcome> {
        if buf.starts_with(QR_PAIR_PREFIX) {
            let candidate = std::str::from_utf8(&buf[QR_PAIR_PREFIX.len()..])
                .ok()?
                .trim();
            if candidate == self.token {
                let reply = format!("AS2P_PAIR_ACK|{}|{}", self.host_name, self.token).into_bytes();
                return Some(PairingOutcome::Authorized { reply });
            }
            return Some(PairingOutcome::Rejected {
                reply: b"AS2P_PAIR_DENY|INVALID_TOKEN".to_vec(),
            });
        }

        if buf.starts_with(PAIR_PROBE_PREFIX) {
            let reply = format!(
                "AS2P_PAIR_CHALLENGE|{}|{}|{},{},{}",
                self.host_name,
                self.challenge_id,
                self.choices[0],
                self.choices[1],
                self.choices[2]
            )
            .into_bytes();
            return Some(PairingOutcome::ReplyOnly(reply));
        }

        if buf.starts_with(PAIR_VERIFY_PREFIX) {
            let body = std::str::from_utf8(&buf[PAIR_VERIFY_PREFIX.len()..])
                .ok()?
                .trim();
            let mut parts = body.splitn(2, '|');
            let cid = parts.next()?.trim();
            let code_str = parts.next()?.trim();
            let parsed_code: Option<u8> = code_str.parse().ok();

            if cid == self.challenge_id && parsed_code == Some(self.match_code) {
                let reply = format!("AS2P_PAIR_ACK|{}|{}", self.host_name, self.token).into_bytes();
                return Some(PairingOutcome::Authorized { reply });
            }

            // Rotate secret & choices after a failed guess to stop brute-force attempts.
            self.rotate();
            return Some(PairingOutcome::Rejected {
                reply: b"AS2P_PAIR_DENY|WRONG_CODE".to_vec(),
            });
        }

        None
    }
}

pub fn default_host_name() -> String {
    std::env::var("COMPUTERNAME")
        .or_else(|_| std::env::var("HOSTNAME"))
        .unwrap_or_else(|_| "MobileSpeaker-PC".to_string())
}

pub fn sanitize_host_name(raw: &str) -> String {
    let cleaned: String = raw
        .chars()
        .filter(|c| c.is_ascii_alphanumeric() || *c == '-' || *c == '_')
        .take(24)
        .collect();
    if cleaned.is_empty() {
        "MobileSpeaker-PC".to_string()
    } else {
        cleaned
    }
}

/// Enumerates active non-loopback/non-APIPA IPv4 addresses across Wi-Fi, hotspots, and USB adapters.
pub fn list_candidate_ipv4s() -> Vec<Ipv4Addr> {
    let Ok(ifaces) = local_ip_address::list_afinet_netifas() else {
        return Vec::new();
    };
    let ips = ifaces.into_iter().filter_map(|(_, ip)| match ip {
        IpAddr::V4(v4) => Some(v4),
        IpAddr::V6(_) => None,
    });
    prioritize_ipv4s(ips)
}

/// Filters loopback/link-local addresses, deduplicates, and prioritizes private LAN & hotspot subnets.
pub fn prioritize_ipv4s<I>(ips: I) -> Vec<Ipv4Addr>
where
    I: IntoIterator<Item = Ipv4Addr>,
{
    let mut seen = HashSet::new();
    let mut hotspot_or_private = Vec::new();
    let mut other = Vec::new();

    for ip in ips {
        if ip.is_loopback() || ip.is_unspecified() || ip.is_link_local() || ip.is_multicast() {
            continue;
        }
        if !seen.insert(ip) {
            continue;
        }
        let octets = ip.octets();
        if octets[0] == 192 && octets[1] == 168 && octets[2] == 137 {
            // Put Windows Mobile Hotspot gateway IP at the very front.
            hotspot_or_private.insert(0, ip);
        } else if ip.is_private() {
            hotspot_or_private.push(ip);
        } else {
            other.push(ip);
        }
    }

    hotspot_or_private.extend(other);
    hotspot_or_private
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn prioritize_ipv4s_filters_loopback_linklocal_and_dedups() {
        let input = vec![
            Ipv4Addr::new(127, 0, 0, 1),
            Ipv4Addr::new(169, 254, 10, 5),
            Ipv4Addr::new(192, 168, 1, 42),
            Ipv4Addr::new(192, 168, 137, 1),
            Ipv4Addr::new(192, 168, 1, 42),
            Ipv4Addr::new(0, 0, 0, 0),
        ];
        let out = prioritize_ipv4s(input);
        assert_eq!(
            out,
            vec![
                Ipv4Addr::new(192, 168, 137, 1),
                Ipv4Addr::new(192, 168, 1, 42),
            ]
        );
    }

    #[test]
    fn qr_uri_includes_all_ips_and_token() {
        let session = PairingSession::new("My Gaming PC!".to_string());
        let uri = session.build_qr_uri(
            &[
                Ipv4Addr::new(192, 168, 137, 1),
                Ipv4Addr::new(192, 168, 1, 50),
            ],
            12345,
        );
        assert!(uri.starts_with("as2p://pair?v=1&ips=192.168.137.1,192.168.1.50&p=12345&t="));
        assert!(uri.contains(&format!("&c={}&n=MyGamingPC", session.match_code)));
    }

    #[test]
    fn number_match_challenge_contains_secret_and_two_decoys() {
        let mut session = PairingSession::new("TestPC".to_string());
        let reply = match session.handle_packet(b"AS2P_PAIR_PROBE") {
            Some(PairingOutcome::ReplyOnly(bytes)) => String::from_utf8(bytes).unwrap(),
            other => panic!("unexpected outcome: {:?}", other),
        };
        assert!(reply.starts_with("AS2P_PAIR_CHALLENGE|TestPC|"));
        let parts: Vec<&str> = reply.split('|').collect();
        assert_eq!(parts.len(), 4);
        let nums: Vec<u8> = parts[3].split(',').map(|s| s.parse().unwrap()).collect();
        assert_eq!(nums.len(), 3);
        let unique: HashSet<u8> = nums.iter().copied().collect();
        assert_eq!(unique.len(), 3);
        assert!(unique.contains(&session.match_code));
    }

    #[test]
    fn verify_accepts_matching_code_and_rotates_on_wrong_code() {
        let mut session = PairingSession::new("Host".to_string());
        let old_code = session.match_code;
        let old_cid = session.challenge_id.clone();

        let wrong_code = session
            .choices
            .iter()
            .copied()
            .find(|&c| c != old_code)
            .unwrap();
        let wrong_msg = format!("AS2P_PAIR_VERIFY|{}|{}", old_cid, wrong_code);
        match session.handle_packet(wrong_msg.as_bytes()) {
            Some(PairingOutcome::Rejected { .. }) => {}
            other => panic!("expected rejection, got {:?}", other),
        }
        // Old challenge_id must no longer work after rotation.
        let replay_msg = format!("AS2P_PAIR_VERIFY|{}|{}", old_cid, old_code);
        match session.handle_packet(replay_msg.as_bytes()) {
            Some(PairingOutcome::Rejected { .. }) => {}
            other => panic!("expected stale challenge rejection, got {:?}", other),
        }

        // Current challenge & code must authorize cleanly.
        let ok_msg = format!("AS2P_PAIR_VERIFY|{}|{}", session.challenge_id, session.match_code);
        match session.handle_packet(ok_msg.as_bytes()) {
            Some(PairingOutcome::Authorized { reply }) => {
                let s = String::from_utf8(reply).unwrap();
                assert!(s.starts_with(&format!("AS2P_PAIR_ACK|Host|{}", session.token)));
            }
            other => panic!("expected auth, got {:?}", other),
        }
    }

    #[test]
    fn qr_pair_validates_token_and_rejects_bad_token() {
        let mut session = PairingSession::new("Host".to_string());
        let good = format!("AS2P_QR_PAIR|{}", session.token);
        match session.handle_packet(good.as_bytes()) {
            Some(PairingOutcome::Authorized { reply }) => {
                assert!(String::from_utf8(reply).unwrap().starts_with("AS2P_PAIR_ACK|Host|"));
            }
            other => panic!("expected auth, got {:?}", other),
        }
        match session.handle_packet(b"AS2P_QR_PAIR|deadbeef") {
            Some(PairingOutcome::Rejected { .. }) => {}
            other => panic!("expected reject, got {:?}", other),
        }
    }

    #[test]
    fn render_qr_rgba_produces_valid_square_buffer() {
        let (w, h, rgba) = PairingSession::render_qr_rgba("as2p://pair?v=1&ips=192.168.1.10&p=12345&t=abcd1234", 180)
            .expect("qr render");
        assert_eq!(w, h);
        assert!(w >= 60);
        assert_eq!(rgba.len(), (w * h * 4) as usize);
    }
}
