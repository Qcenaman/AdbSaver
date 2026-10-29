package com.qcenaman.adbsaver.net

/**
 * The ways the phone can reach the PC's `tokensaver serve`, in the order they are tried:
 *  lan       – PC's LAN IP (home Wi-Fi only)
 *  tailscale – PC's Tailscale IP (any network, needs Tailscale on both)
 *  public    – PC's public address: DDNS name or public IP, router forwards the serve port (no Tailscale)
 *  tunnel    – an HTTPS reverse tunnel the PC opens outward (cloudflared / ngrok / any), works behind CGNAT (no Tailscale)
 * Pure Kotlin so it is unit-testable; PcLink feeds it from Prefs.
 */
object Routes {
    data class Route(val via: String, val base: String, val timeoutMs: Int)

    /** "host", "host:port", "ip" or a full "http(s)://…" → base URL (no trailing slash), or null when empty. */
    fun publicBase(host: String, port: Int): String? {
        val h = host.trim().trimEnd('/')
        if (h.isEmpty()) return null
        if (h.startsWith("http://") || h.startsWith("https://")) return h
        val hasPort = h.lastIndexOf(':') > h.lastIndexOf(']') && h.substringAfterLast(':').toIntOrNull() != null
        return if (hasPort) "http://$h" else "http://$h:$port"
    }

    /** Tunnel URLs must be absolute; scheme defaults to https (what cloudflared/ngrok give). */
    fun tunnelBase(url: String): String? {
        val u = url.trim().trimEnd('/')
        if (u.isEmpty()) return null
        return if (u.startsWith("http://") || u.startsWith("https://")) u else "https://$u"
    }

    fun of(lanIp: String, tsIp: String, publicHost: String, tunnelUrl: String, port: Int): List<Route> {
        val out = ArrayList<Route>(4)
        if (lanIp.isNotEmpty()) out += Route("lan", "http://$lanIp:$port", 1500)
        if (tsIp.isNotEmpty()) out += Route("tailscale", "http://$tsIp:$port", 4000)
        publicBase(publicHost, port)?.let { out += Route("public", it, 5000) }
        tunnelBase(tunnelUrl)?.let { out += Route("tunnel", it, 8000) }
        return out
    }

    /** Host part of a public route (for Wake-on-LAN to a router that forwards UDP 9), or null. */
    fun publicHostOnly(host: String): String? {
        var h = host.trim().removePrefix("http://").removePrefix("https://").substringBefore('/')
        if (h.isEmpty()) return null
        if (!h.startsWith("[") && h.count { it == ':' } == 1) h = h.substringBefore(':')
        return h
    }
}
