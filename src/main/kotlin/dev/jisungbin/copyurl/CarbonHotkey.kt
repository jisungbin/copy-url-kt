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

        fun UnregisterEventHotKey(inHotKey: Pointer): Int

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

    // setEnabled 로 핫키를 동적으로 켜고 끄기 위해 register 시점의 컨텍스트를 보관한다.
    private var carbon: Carbon? = null
    private var eventTarget: Pointer? = null
    private var modifiers = 0
    private var keyCode = 0
    private var enabled = false

    /** 4글자 OSType 을 빅엔디안 Int 로 변환 ('keyb', 'htk1' 등). */
    private fun osType(s: String): Int {
        var r = 0
        for (c in s) r = (r shl 8) or (c.code and 0xff)
        return r
    }

    /**
     * 키보드 이벤트 핸들러를 설치하고 핫키 파라미터를 보관한다. 실제 키 가로채기는
     * [setEnabled] 로 켜야 시작된다 — frontmost 앱에 따라 동적으로 켜고 끄기 위함.
     *
     * @return 핸들러 설치 성공 여부 (실패 시 호출 측에서 CGEventTap 등으로 폴백).
     */
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

        this.carbon = carbon
        eventTarget = target
        this.modifiers = modifiers
        this.keyCode = keyCode
        return true
    }

    /**
     * 핫키 가로채기를 켜거나 끈다. 켜짐 = OS 가 키 조합을 우리 앱에 독점 전달,
     * 꺼짐 = 다른 앱으로 통과. 중복 호출은 무시한다.
     * [register] 성공 후, register 와 같은 스레드(AWT EDT)에서 호출해야 한다.
     */
    fun setEnabled(enabled: Boolean) {
        if (enabled == this.enabled) return
        val carbon = carbon ?: return
        val target = eventTarget ?: return

        if (enabled) {
            val hotKeyId = EventHotKeyID.ByValue().apply {
                signature = osType("htk1")
                id = 1
                write()
            }
            val status = carbon.RegisterEventHotKey(
                keyCode, modifiers, hotKeyId, target, 0, hotKeyRef,
            )
            if (status != 0) {
                System.err.println("[copy-url] RegisterEventHotKey 실패: status=$status")
                return
            }
        } else {
            val ref = hotKeyRef.value ?: return
            val status = carbon.UnregisterEventHotKey(ref)
            if (status != 0) {
                System.err.println("[copy-url] UnregisterEventHotKey 실패: status=$status")
                return
            }
        }
        this.enabled = enabled
    }
}
