# Handoff for TokenSaver — phone link, PC side
Written 2026-10-29 22:15 (+08) from the AdbSaver project. Phone: AdbSaver main 0419e8e. PC: TokenSaver 7f02eb6, serve running.

## 1. `tokensaver phone status` shows a stale "phone's last report"
Reports do arrive: `data\phone.json` on disk had `last_report` 21:37:33 while `phone status` printed "8h ago" (the value from before the 14:11 restart). The status command reads a copy loaded when the MCP server started; it must re-read phone.json (or ask serve) each time. Same for `adb_serial` (phone.json still says 192.168.1.134:33469; the port changes whenever Wireless debugging restarts).

## 2. `phone call tool=run` pass-through
The phone's `run` now accepts `timeout_seconds` (kills the command at the deadline) and `wait_seconds` (default 45 s, returns "still running" with partial output). Keep the PC's wait ≥ 50 s, or pass `wait_seconds` ≤ 45 explicitly.

## 3. Finding the phone's adb port
`adb mdns services` lists stale `_adb-tls-connect` entries; the live one is the port advertised after the phone's Wireless debugging was switched on. The phone's report could carry `service.adb.tls.port` so `phone pair` / adb fallback connect directly instead of guessing.

## Verified today (phone on Wi-Fi, and with Wi-Fi off over Tailscale)
- phone → PC: lan, tailscale, tunnel all up (needed `usesCleartextTraffic` on the phone).
- phone reports every 20 min and on network/IP/VPN change, after /link and after pc status.
- phone shell helper runs under a supervisor loop over loopback TCP; shell commands work with Wi-Fi off via Tailscale.
