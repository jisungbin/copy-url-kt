package dev.jisungbin.copyurl

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import javax.swing.SwingUtilities

private const val DOUBLE_TAP_MS = 500L
private const val WindowWidthDp = 480.0
private const val WindowHeightDp = 460.0
private const val HistoryWindowTitle = "CopyUrl 기록"
private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.KOREA)

// 직전 핫키 시각 — 따닥(더블탭) 판정용
private val lastHotkeyTime = AtomicLong(0L)

// 핫키 콜백은 메인 런루프 스레드에서 오므로, 무거운 작업(osascript/pbcopy)은 워커로 위임
private val worker = Executors.newSingleThreadExecutor { r ->
    Thread(r, "copy-url-worker").apply { isDaemon = true }
}

fun main() {
    Dbg.log("CopyUrl 시작")
    System.setProperty("apple.awt.UIElement", "true")

    application {
        val appState = remember { CopyUrlAppState() }
        val listState = rememberLazyListState()
        val windowState = remember {
            WindowState(
                position = WindowPosition(alignment = Alignment.TopEnd),
                size = DpSize(width = WindowWidthDp.dp, height = WindowHeightDp.dp),
            )
        }

        DisposableEffect(Unit) {
            MacStatusBar.setup(onClick = {
                // 좌표 조회(네이티브)가 실패해도 창 토글은 보장한다
                val origin = runCatching { MacStatusBar.windowOriginFor(windowWidthDp = WindowWidthDp, gapDp = 6.0) }
                    .onFailure { Dbg.log("windowOriginFor 실패: ${it.message}") }
                    .getOrNull()
                SwingUtilities.invokeLater {
                    if (origin != null) {
                        windowState.position = WindowPosition.Absolute(origin.first.dp, origin.second.dp)
                    }
                    appState.toggleHistory()
                }
            })
            val ok = CarbonHotkey.register(
                modifiers = CarbonHotkey.CMD or CarbonHotkey.SHIFT,
                keyCode = CarbonHotkey.KEY_C,
            ) {
                worker.submit { onHotkey(appState) }
            }
            Dbg.log(if (ok) "핫키 핸들러 설치 완료" else "⚠️ 핫키 핸들러 설치 실패")
            if (ok) {
                // Chrome 이 맨 앞일 때만 핫키 활성화 → 다른 앱에선 Cmd+Shift+C 가 그 앱으로 통과
                ChromeFocusGate.start { isChrome ->
                    SwingUtilities.invokeLater { CarbonHotkey.setEnabled(isChrome) }
                }
            }

            onDispose { MacStatusBar.close() }
        }

        Window(
            onCloseRequest = appState::hideHistory,
            visible = appState.isHistoryVisible,
            title = HistoryWindowTitle,
            state = windowState,
            alwaysOnTop = true,
            undecorated = true,
            transparent = true,
            resizable = false,
        ) {
            DisposableEffect(Unit) {
                val focusListener = object : WindowFocusListener {
                    override fun windowGainedFocus(event: WindowEvent?) = Unit
                    override fun windowLostFocus(event: WindowEvent?) {
                        appState.hideHistory()
                    }
                }
                window.addWindowFocusListener(focusListener)
                onDispose { window.removeWindowFocusListener(focusListener) }
            }

            // 표시될 때 NSWindow 그림자를 켜고(transparent 라 꺼져 있음), 창을 활성화해 key/포커스를 준다.
            LaunchedEffect(appState.isHistoryVisible) {
                if (appState.isHistoryVisible) {
                    MainDispatch.async { prepareHistoryWindow(HistoryWindowTitle) }
                    if (appState.history.isNotEmpty()) {
                        listState.scrollToItem(0)
                    }
                }
            }

            HistoryWindow(
                entries = appState.history,
                onCopy = { url ->
                    ClipboardUtil.set(url)
                    Notifier.notify("URL 다시 복사됨", url)
                },
                onDelete = appState::remove,
                onClear = appState::clear,
                listState = listState,
            )
        }
    }
}

private fun prepareHistoryWindow(title: String) {
    val nsAppCls = ObjC.cls("NSApplication") ?: return
    val nsApp = ObjC.sendP(nsAppCls, "sharedApplication") ?: return
    // accessory 앱이라 status item 클릭만으로는 창이 key 가 안 돼 포커스를 못 받는다 → 직접 활성화.
    ObjC.sendV(nsApp, "activateIgnoringOtherApps:", 1L)
    val windows = ObjC.sendP(nsApp, "windows") ?: return
    val count = ObjC.msgSend.invokeLong(arrayOf<Any>(windows, ObjC.sel("count")))
    for (i in 0L until count) {
        val win = ObjC.msgSend.invokePointer(arrayOf<Any>(windows, ObjC.sel("objectAtIndex:"), NativeLong(i))) ?: continue
        if (ObjC.stringValue(ObjC.sendP(win, "title")) == title) {
            ObjC.sendV(win, "setHasShadow:", 1L)
            ObjC.sendV(win, "invalidateShadow")
            ObjC.sendV(win, "makeKeyAndOrderFront:", Pointer.NULL)
            return
        }
    }
}

private fun onHotkey(appState: CopyUrlAppState) {
    val now = System.currentTimeMillis()
    val prev = lastHotkeyTime.getAndSet(now)
    copyAndNotify(clean = now - prev < DOUBLE_TAP_MS, appState = appState)
}

private fun copyAndNotify(clean: Boolean, appState: CopyUrlAppState) {
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
    SwingUtilities.invokeLater {
        appState.record(
            CopiedUrlEntry(
                url = text,
                label = label,
                copiedAt = timeFormat.format(Date()),
                cleaned = clean,
            ),
        )
    }
    Notifier.notify(label, text)
}
