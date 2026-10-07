package app.mosaic.data.net

/** A user agent the playlist editor offers, named after the app it identifies as. */
data class UserAgentPreset(val name: String, val value: String)

/**
 * Common IPTV and media player user agents. Some providers only serve streams to apps they
 * recognise, so Mosaic can identify as one of these. An empty value means Mosaic's default.
 */
object UserAgents {
    val presets = listOf(
        UserAgentPreset("Default (VLC)", ""),
        UserAgentPreset("TiviMate", "TiviMate/5.1.6 (Linux; Android 14)"),
        UserAgentPreset("IPTV Smarters Pro", "IPTVSmartersPro"),
        UserAgentPreset("OTT Navigator", "OTT Navigator/1.7.1.1 (Linux;Android 14)"),
        UserAgentPreset("Perfect Player", "Perfect Player/1.6.0.1 (Linux;Android 14)"),
        UserAgentPreset("Kodi", "Kodi/21.1 (Linux; Android 14.0; Pixel) Android/14.0.0 Sys_CPU/aarch64 App_Bitness/64 Version/21.1-(21.1.0)-Git:20240804"),
        UserAgentPreset("MX Player", "MXPlayer/1.86.0 (Linux; Android 14)"),
        UserAgentPreset("ExoPlayer", "ExoPlayerLib/2.19.1"),
        UserAgentPreset("FFmpeg (Lavf)", "Lavf/60.16.100"),
        UserAgentPreset("Chrome on Android", "Mozilla/5.0 (Linux; Android 14; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"),
        UserAgentPreset("Smart TV (Samsung)", "Mozilla/5.0 (SMART-TV; LINUX; Tizen 7.0) AppleWebKit/537.36 (KHTML, like Gecko) 76.0.3809.146/7.0 TV Safari/537.36"),
    )

    /** The preset whose value is exactly [userAgent], or null for a custom one. */
    fun presetFor(userAgent: String): UserAgentPreset? = presets.firstOrNull { it.value == userAgent.trim() }
}
