package dev.jisungbin.copyurl

import com.sun.jna.Callback
import com.sun.jna.CallbackReference
import com.sun.jna.Pointer

/**
 * frontmost 앱이 Chrome 인지 NSWorkspace 활성화 알림으로 추적해, 바뀔 때만 [onChange] 로 알린다.
 * 핫키를 Chrome 포커스 중에만 켜기 위한 게이트 — 그 외 앱에선 Cmd+Shift+C 가 그 앱으로 통과한다.
 */
object ChromeFocusGate {
    private var observerCallback: ActivationCallback? = null
    private var observer: Pointer? = null
    private var onChange: ((Boolean) -> Unit)? = null
    private var wasChrome: Boolean? = null

    fun start(onChange: (isChrome: Boolean) -> Unit) {
        this.onChange = onChange
        MainDispatch.async { startNow() }
    }

    private fun startNow() {
        val center = workspace()?.let { ObjC.sendP(it, "notificationCenter") } ?: return
        val observer = createObserver() ?: return
        // 알림 이름 상수 심볼을 dlsym 으로 못 찾으니, 동일 문자열을 NSString 으로 넘겨 매칭한다.
        val name = ObjC.nsString(ActivateNotification) ?: return
        ObjC.sendV(
            center, "addObserver:selector:name:object:",
            observer, ObjC.sel(ActivatedSelector), name, Pointer.NULL,
        )
        emitIfChanged() // 시작 시 현재 frontmost 를 즉시 반영
    }

    private fun createObserver(): Pointer? {
        observerCallback = object : ActivationCallback {
            override fun callback(self: Pointer?, selector: Pointer?, notification: Pointer?) {
                emitIfChanged()
            }
        }
        val cls = getOrCreateObserverClass() ?: return null
        val instance = ObjC.sendP(cls, "alloc")?.let { ObjC.sendP(it, "init") } ?: return null
        ObjC.sendP(instance, "retain")
        observer = instance
        return instance
    }

    private fun getOrCreateObserverClass(): Pointer? {
        ObjC.cls(ObserverClassName)?.let { return it }
        val superclass = ObjC.cls("NSObject") ?: return null
        val cls = ObjC.allocateClassPair(superclass, ObserverClassName) ?: return null
        val callback = observerCallback ?: return null
        ObjC.addMethod(cls, ObjC.sel(ActivatedSelector), CallbackReference.getFunctionPointer(callback), "v@:@")
        ObjC.registerClassPair(cls)
        return cls
    }

    private fun emitIfChanged() {
        val isChrome = isChromeFrontmost()
        if (isChrome == wasChrome) return
        wasChrome = isChrome
        onChange?.invoke(isChrome)
    }

    private fun isChromeFrontmost(): Boolean {
        val front = workspace()?.let { ObjC.sendP(it, "frontmostApplication") } ?: return false
        return ObjC.stringValue(ObjC.sendP(front, "bundleIdentifier")) == ChromeBundleId
    }

    private fun workspace(): Pointer? =
        ObjC.cls("NSWorkspace")?.let { ObjC.sendP(it, "sharedWorkspace") }

    private interface ActivationCallback : Callback {
        fun callback(self: Pointer?, selector: Pointer?, notification: Pointer?)
    }

    private const val ChromeBundleId = "com.google.Chrome"
    private const val ActivateNotification = "NSWorkspaceDidActivateApplicationNotification"
    private const val ObserverClassName = "CopyUrlChromeFocusObserver"
    private const val ActivatedSelector = "appActivated:"
}
