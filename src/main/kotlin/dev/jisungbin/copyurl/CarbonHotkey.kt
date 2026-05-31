package dev.jisungbin.copyurl

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.PointerByReference

/**
 * macOS Carbon `RegisterEventHotKey` 로 시스템 전역 단축키를 잡는다.
 *
 * - 접근성 권한 불필요 (입력 모니터링이 아니라 핫키 등록 API)
 * - 등록된 키 조합은 OS가 우리 앱에 독점 전달 → 크롬의 Cmd+Shift+C(요소 검사)가 안 열림
 *
 * 핫키 콜백은 메인 런루프(=AWT 런루프) 스레드에서 호출되므로,
 * 무거운 작업(osascript 등)은 호출 측에서 코루틴/다른 스레드로 넘긴다.
 */
object CarbonHotkey {

    // Carbon 가상 키코드
    const val KEY_C = 8

    // Carbon modifier 비트마스크
    const val CMD = 256
    const val SHIFT = 512
    const val OPTION = 2048
    const val CONTROL = 4096

    private interface Carbon : Library {
        fun GetApplicationEventTarget(): Pointer

        fun InstallEventHandler(
            inTarget: Pointer,
            inHandler: EventHandler,
            inNumTypes: NativeLong,
            inList: EventTypeSpec,
            inUserData: Pointer?,
            outRef: PointerByReference,
        ): Int

        fun RegisterEventHotKey(
            inHotKeyCode: Int,
            inHotKeyModifiers: Int,
            inHotKeyID: EventHotKeyID.ByValue,
            inTarget: Pointer,
            inOptions: Int,
            outRef: PointerByReference,
        ): Int

        companion object {
            val INSTANCE: Carbon by lazy { Native.load("Carbon", Carbon::class.java) }
        }
    }

    @Structure.FieldOrder("eventClass", "eventKind")
    class EventTypeSpec : Structure() {
        @JvmField var eventClass: Int = 0
        @JvmField var eventKind: Int = 0
    }

    @Structure.FieldOrder("signature", "id")
    open class EventHotKeyID : Structure() {
        @JvmField var signature: Int = 0
        @JvmField var id: Int = 0
        class ByValue : EventHotKeyID(), Structure.ByValue
    }

    interface EventHandler : Callback {
        fun callback(inHandlerCallRef: Pointer?, inEvent: Pointer?, inUserData: Pointer?): Int
    }

    // JNA 콜백/포인터는 GC 되면 네이티브 크래시 → 강한 참조로 살려둔다.
    private var handlerRef: EventHandler? = null
    private val hotKeyRef = PointerByReference()
    private val handlerOutRef = PointerByReference()

    /** 4글자 OSType 을 빅엔디안 Int 로 변환 ('keyb', 'htk1' 등). */
    private fun osType(s: String): Int {
        var r = 0
        for (c in s) r = (r shl 8) or (c.code and 0xff)
        return r
    }

    /** @return 등록 성공 여부 (실패 시 호출 측에서 CGEventTap 등으로 폴백). */
    fun register(modifiers: Int, keyCode: Int, onPressed: () -> Unit): Boolean {
        val carbon = try {
            Carbon.INSTANCE
        } catch (t: Throwable) {
            System.err.println("[copy-url] Carbon 로드 실패: ${t.message}")
            return false
        }

        val target = carbon.GetApplicationEventTarget()

        // kEventClassKeyboard('keyb') / kEventHotKeyPressed(5) 핸들러 설치
        val spec = EventTypeSpec().apply {
            eventClass = osType("keyb")
            eventKind = 5
            write()
        }

        val handler = object : EventHandler {
            override fun callback(inHandlerCallRef: Pointer?, inEvent: Pointer?, inUserData: Pointer?): Int {
                try {
                    onPressed()
                } catch (t: Throwable) {
                    System.err.println("[copy-url] 핫키 콜백 오류: ${t.message}")
                }
                return 0 // noErr
            }
        }
        handlerRef = handler

        val installStatus = carbon.InstallEventHandler(
            target, handler, NativeLong(1L), spec, null, handlerOutRef,
        )
        if (installStatus != 0) {
            System.err.println("[copy-url] InstallEventHandler 실패: status=$installStatus")
            return false
        }

        val hotKeyId = EventHotKeyID.ByValue().apply {
            signature = osType("htk1")
            id = 1
            write()
        }

        val regStatus = carbon.RegisterEventHotKey(
            keyCode, modifiers, hotKeyId, target, 0, hotKeyRef,
        )
        if (regStatus != 0) {
            System.err.println("[copy-url] RegisterEventHotKey 실패: status=$regStatus")
            return false
        }

        println("[copy-url] ✅ 전역 핫키 등록 성공 (modifiers=$modifiers, keyCode=$keyCode)")
        return true
    }
}
