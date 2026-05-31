package dev.jisungbin.copyurl

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

private const val DOUBLE_TAP_MS = 500L

// 직전 핫키 시각 — 따닥(더블탭) 판정용
private val lastHotkeyTime = AtomicLong(0L)

// 핫키 콜백은 메인 런루프 스레드에서 오므로, 무거운 작업(osascript/pbcopy)은 워커로 위임
private val worker = Executors.newSingleThreadExecutor { r ->
    Thread(r, "copy-url-worker").apply { isDaemon = true }
}

fun main() {
    Dbg.log("CopyUrl 시작")
    System.setProperty("apple.awt.UIElement", "true")

    // 메뉴바(NSStatusItem) 구성 — NSApp.sharedApplication 도 여기서 생성됨
    MacStatusBar.setup()

    val ok = CarbonHotkey.register(
        modifiers = CarbonHotkey.CMD or CarbonHotkey.SHIFT,
        keyCode = CarbonHotkey.KEY_C,
    ) {
        worker.submit { onHotkey() }
    }
    Dbg.log(if (ok) "핫키 등록 완료" else "⚠️ 핫키 등록 실패")

    // NSApp 런루프 시작 (블로킹) — Carbon 핫키 콜백이 이 위에서 동작
    MacStatusBar.run()
}

private fun onHotkey() {
    val now = System.currentTimeMillis()
    val prev = lastHotkeyTime.getAndSet(now)
    copyAndNotify(clean = now - prev < DOUBLE_TAP_MS)
}

private fun copyAndNotify(clean: Boolean) {
    val url = ChromeUrl.current()
    if (url == null) {
        Notifier.notify("크롬 URL을 읽을 수 없음", "맨 앞 창이 크롬인지 확인하세요")
        return
    }

    val text: String
    val label: String
    if (clean) {
        val result = TrackerCleaner.clean(url)
        text = result.url
        label = if (result.removed > 0) {
            "트래커 ${result.removed}개 제거 후 복사됨"
        } else {
            "제거할 트래커 없음 · 복사됨"
        }
    } else {
        text = url
        label = "전체 URL 복사됨"
    }

    ClipboardUtil.set(text)
    Notifier.notify(label, text)
}
