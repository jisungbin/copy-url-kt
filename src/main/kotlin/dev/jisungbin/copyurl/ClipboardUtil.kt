package dev.jisungbin.copyurl

/** pbcopy 로 시스템 클립보드에 텍스트 쓰기 (AWT 비의존 → NSApp 런루프와 충돌 없음). */
object ClipboardUtil {
    fun set(text: String) {
        try {
            val p = ProcessBuilder("pbcopy").start()
            p.outputStream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            p.waitFor()
        } catch (e: Exception) {
            System.err.println("[copy-url] 클립보드 실패: ${e.message}")
        }
    }
}
