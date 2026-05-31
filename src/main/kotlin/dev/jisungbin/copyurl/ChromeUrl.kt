package dev.jisungbin.copyurl

import java.util.concurrent.TimeUnit

/** osascript(AppleScript)로 크롬 맨 앞 창의 활성 탭 URL을 읽는다. */
object ChromeUrl {

    fun current(): String? {
        val script =
            """tell application "Google Chrome" to return URL of active tab of front window"""
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
