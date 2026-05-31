package dev.jisungbin.copyurl

import java.io.File

/** 콘솔 + 파일(~/copyurl-debug.log) 동시 로그. .app 은 콘솔 출력이 안 보이므로 파일에도 남긴다. */
object Dbg {
    private val file = File(System.getProperty("user.home"), "copyurl-debug.log")
    fun log(msg: String) {
        val line = "[copy-url] $msg"
        System.err.println(line)
        try {
            file.appendText(line + "\n")
        } catch (e: Exception) {
            // 무시
        }
    }
}
