package dev.jisungbin.copyurl

/**
 * 알림 표시. 우선 네이티브 NSUserNotification("CopyUrl" 이름으로) 시도,
 * 실패하면 osascript(발신자 "스크립트 편집기")로 폴백.
 */
object Notifier {
    fun notify(title: String, body: String) {
        if (!deliverNative(title, body)) {
            deliverOsascript(title, body)
        }
    }

    private fun deliverNative(title: String, body: String): Boolean {
        return try {
            val noteCls = ObjC.cls("NSUserNotification") ?: return false
            val centerCls = ObjC.cls("NSUserNotificationCenter") ?: return false
            val center = ObjC.sendP(centerCls, "defaultUserNotificationCenter") ?: return false
            val note = ObjC.sendP(noteCls, "alloc")?.let { ObjC.sendP(it, "init") } ?: return false
            val t = ObjC.nsString(title) ?: return false
            val b = ObjC.nsString(body) ?: return false
            ObjC.sendV(note, "setTitle:", t)
            ObjC.sendV(note, "setInformativeText:", b)
            ObjC.sendV(center, "deliverNotification:", note)
            true
        } catch (e: Throwable) {
            System.err.println("[copy-url] 네이티브 알림 실패 → osascript 폴백: ${e.message}")
            false
        }
    }

    private fun deliverOsascript(title: String, body: String) {
        val script = "display notification \"${esc(body)}\" with title \"${esc(title)}\""
        try {
            ProcessBuilder("osascript", "-e", script)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        } catch (e: Exception) {
            System.err.println("[copy-url] osascript 알림 실패: ${e.message}")
        }
    }

    private fun esc(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")
}
