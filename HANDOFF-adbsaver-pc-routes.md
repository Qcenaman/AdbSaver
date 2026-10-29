# HANDOFF: AdbSaver — PC route probes and reporting (phone side)

Written 2026-10-29 14:20 (+08) from the TokenSaver project after verifying the PC side of the no-Tailscale link. Phone: AdbSaver commit 2ac3b1b on main. PC: TokenSaver main 7f02eb6 (1.18.1 + push_routes fix), `tokensaver serve` running as the "TokenSaver Link" task, cloudflared quick tunnel up.

## What is confirmed working (PC side — do not change there)
- serve listens on 0.0.0.0:8766 and [::]:8766; from the PC itself `/health` answers 200 on 127.0.0.1, the LAN IP 192.168.1.113 and the Tailscale IP 100.109.168.97.
- Firewall rule "TokenSaver Link" allows TCP 8766 on Domain, Private and Public profiles.
- Tunnel https://<random>.trycloudflare.com → serve: `/health` 200, unauthenticated `/mcp` and `/phone/report` 401.
- `tokensaver phone push-routes` reached the phone (over Tailscale, 100.84.75.31:8765) and the phone then showed the tunnel route up.
- The PC reaches the phone's engine over Tailscale every time (`phone status`: "engine: reachable via tailscale").

## Problems seen on the phone side (fix these in AdbSaver)

### 1. `pc status` reports the Tailscale route down although it is up
Phone on mobile data, Tailscale running on the phone (user restarted it; the PC reaches the phone over it the whole time):

    lan       http://192.168.1.113:8766   down
    tailscale http://100.109.168.97:8766  down
    tunnel    https://scotland-coins-apparatus-silk.trycloudflare.com  up

Only the tunnel is "up". Tailscale phone→PC should work (PC→phone does, same tailnet, PC answers on that IP, firewall open). Likely causes to check, in order:
- the probe's timeout is too short for the first Tailscale packet (DERP relay / NAT traversal on mobile data can take 2–5 s on the first request) — use ≥6 s for the tailscale route and retry once;
- the probe binds to or is restricted to the current network (mobile data) so traffic never enters the Tailscale VPN interface — must not call `Network.bindSocket`/`bindProcessToNetwork` with the cellular network, and must not use an OkHttp/`HttpURLConnection` set up with a cellular-only `Network`;
- Android's "always-on VPN / block connections without VPN" or Tailscale not yet connected at probe time — detect and probe again after a short delay;
- the probe target or scheme is wrong for that route (e.g. a stale address, https instead of http, wrong port).
Add a log line per route with the URL, the elapsed time and the exact error (timeout / ECONNREFUSED / EHOSTUNREACH / DNS), and surface it in `pc status` as e.g. `down (timeout 3s)` so this is diagnosable next time. The LAN route being down is expected while on mobile data; still show the error.

### 2. The phone has not sent `/phone/report` for over an hour
PC `phone status` → "phone's last report: 1h2m ago" (13:18 +08), even though: serve was restarted at 14:11, the tunnel URL changed, the phone received the new routes via `POST /link`, the phone ran `pc status` several times, and Tailscale was restarted on the phone. If AdbSaver is meant to report on route changes or periodically, that path is dead; make it report (a) on a timer (every 15–30 min is fine), (b) right after receiving `/link`, (c) when its own network/IPs change, (d) after a `pc status`. The PC answers every report with the current routes, so reporting is also how the phone keeps `tunnel_url` fresh.

### 3. Pass-through `run` on the phone hangs
From the PC: `phone call tool=run args={"command":"curl -s -m 5 -w ' [%{http_code}]' http://100.109.168.97:8766/health"}` (also with `timeout_seconds: 15`) never returned within the PC's 60 s wait, twice, while `pc status` calls through the same engine returned instantly. Either the engine's `run` blocks on stdin/stdout, doesn't honour a timeout, or the curl itself hangs on the Tailscale interface (which would also explain #1). Make `run` always return within its timeout with partial output, and test exactly this command from the phone — it is the direct check for #1. (`adb: none` on the PC right now, so the basic adb fallback isn't available for this.)

## Acceptance
1. Phone on mobile data, Tailscale on: `pc status` shows tailscale up and tunnel up (lan down, with the error shown).
2. Phone on mobile data, Tailscale off: `pc status` shows tunnel up, tailscale down with a clear error.
3. PC `tokensaver phone status` shows "phone's last report" under 30 min old without anyone touching the phone.
4. `phone call tool=run` with a 5 s curl returns in under 10 s.
