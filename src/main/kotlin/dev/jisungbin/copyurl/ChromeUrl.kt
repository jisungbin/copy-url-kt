package dev.jisungbin.copyurl

import java.util.concurrent.TimeUnit

/** osascript(AppleScript)로 크롬의 실제 브라우저 창 활성 탭 URL을 읽는다. */
object ChromeUrl {

    fun current(): String? {
        val script =
            """
            tell application "System Events"
                if not (exists process "Google Chrome") then return ""
            end tell

            tell application "Google Chrome"
                repeat with chromeWindow in windows
                    try
                        set tabUrl to URL of active tab of chromeWindow
                        if tabUrl is not "" then
                            set index of chromeWindow to 1
                            activate
                            return tabUrl
                        end if
                    end try
                end repeat
            end tell
            return ""
            """.trimIndent()
        return runOsascript(script)
    }

    private fun runOsascript(script: String): String? = try {
        val proc = ProcessBuilder("osascript", "-e", script)
            .redirectErrorStream(false)
            .start()
        val out = proc.inputStream.bufferedReader().use { it.readText() }.trim()
        val err = proc.errorStream.bufferedReader().use { it.readText() }.trim()
        val finished = proc.waitFor(3, TimeUnit.SECONDS)
        when {
            !finished -> {
                proc.destroyForcibly()
                null
            }
            proc.exitValue() != 0 -> {
                Dbg.log("osascript 오류: $err")
                null
            }
            out.isEmpty() -> null
            else -> out
        }
    } catch (e: Exception) {
        Dbg.log("osascript 실행 실패: ${e.message}")
        null
    }
}
