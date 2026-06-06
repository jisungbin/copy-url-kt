package dev.jisungbin.copyurl

import com.sun.jna.Callback
import com.sun.jna.CallbackReference
import com.sun.jna.Pointer

/**
 * 메뉴바: NSStatusItem + SF Symbol("link").
 * Compose/AWT TrayIcon은 LSUIElement 앱에서 숨는 경우가 있어 AppKit status item을 직접 쓴다.
 */
object MacStatusBar {
    private var statusItem: Pointer? = null
    private var target: Pointer? = null
    private var actionCallback: StatusItemAction? = null

    fun setup(onClick: () -> Unit) {
        MainDispatch.async { setupNow(onClick) }
    }

    fun close() {
        MainDispatch.async { closeNow() }
    }

    /** 메뉴바 아이콘 중앙에 정렬했을 때 히스토리 창의 좌상단 좌표(dp). 메인 스레드에서 호출. */
    fun windowOriginFor(windowWidthDp: Double, gapDp: Double): Pair<Double, Double>? {
        val item = statusItem ?: return null
        val button = ObjC.sendP(item, "button") ?: return null
        val win = ObjC.sendP(button, "window") ?: return null
        val frame = ObjC.sendRect(win, "frame")
        val screen = mainScreenFrame() ?: return null
        val centerX = frame.x + frame.width / 2
        val belowMenuBarY = screen.height - frame.y
        val maxX = (screen.width - windowWidthDp - 8.0).coerceAtLeast(8.0)
        val winX = (centerX - windowWidthDp / 2).coerceIn(8.0, maxX)
        return winX to (belowMenuBarY + gapDp)
    }

    private fun mainScreenFrame(): ObjC.NSRect? {
        val screenCls = ObjC.cls("NSScreen") ?: return null
        val main = ObjC.sendP(screenCls, "mainScreen") ?: return null
        return ObjC.sendRect(main, "frame")
    }

    private fun setupNow(onClick: () -> Unit) {
        closeNow()
        val nsAppCls = ObjC.cls("NSApplication") ?: return
        val nsApp = ObjC.sendP(nsAppCls, "sharedApplication") ?: return
        ObjC.sendV(nsApp, "setActivationPolicy:", 1L) // Accessory → Dock 미표시

        val statusBarCls = ObjC.cls("NSStatusBar") ?: return
        val statusBar = ObjC.sendP(statusBarCls, "systemStatusBar") ?: return
        val item = ObjC.msgSend.invokePointer(
            arrayOf<Any>(statusBar, ObjC.sel("statusItemWithLength:"), -1.0),
        ) ?: return
        ObjC.sendP(item, "retain")
        statusItem = item

        val button = ObjC.sendP(item, "button")
        if (button != null) {
            val nsImageCls = ObjC.cls("NSImage")
            val symName = ObjC.nsString("link")
            val desc = ObjC.nsString("URL 복사")
            if (nsImageCls != null && symName != null && desc != null) {
                val image = ObjC.msgSend.invokePointer(
                    arrayOf<Any>(
                        nsImageCls,
                        ObjC.sel("imageWithSystemSymbolName:accessibilityDescription:"),
                        symName, desc,
                    ),
                )
                if (image != null) ObjC.sendV(button, "setImage:", image)
            }

            val actionTarget = createActionTarget(onClick)
            if (actionTarget != null) {
                ObjC.sendV(button, "setTarget:", actionTarget)
                ObjC.sendV(button, "setAction:", ObjC.sel(ActionSelector))
            }
        }

        Dbg.log("네이티브 메뉴바 NSStatusItem 구성 완료")
    }

    private fun closeNow() {
        val item = statusItem ?: return
        val statusBarCls = ObjC.cls("NSStatusBar") ?: return
        val statusBar = ObjC.sendP(statusBarCls, "systemStatusBar") ?: return
        ObjC.sendV(statusBar, "removeStatusItem:", item)
        statusItem = null
        target = null
    }

    private fun createActionTarget(onClick: () -> Unit): Pointer? {
        actionCallback = object : StatusItemAction {
            override fun callback(self: Pointer?, selector: Pointer?, sender: Pointer?) {
                onClick()
            }
        }
        val targetClass = getOrCreateTargetClass() ?: return null
        val targetObject = ObjC.sendP(targetClass, "alloc")?.let { ObjC.sendP(it, "init") } ?: return null
        ObjC.sendP(targetObject, "retain")
        target = targetObject
        return targetObject
    }

    private fun getOrCreateTargetClass(): Pointer? {
        ObjC.cls(TargetClassName)?.let { return it }
        val superclass = ObjC.cls("NSObject") ?: return null
        val targetClass = ObjC.allocateClassPair(superclass, TargetClassName) ?: return null
        val selector = ObjC.sel(ActionSelector)
        val callback = actionCallback ?: return null
        val implementation = CallbackReference.getFunctionPointer(callback)
        ObjC.addMethod(targetClass, selector, implementation, "v@:@")
        ObjC.registerClassPair(targetClass)
        return targetClass
    }

    private interface StatusItemAction : Callback {
        fun callback(self: Pointer?, selector: Pointer?, sender: Pointer?)
    }

    private const val TargetClassName = "CopyUrlStatusItemTarget"
    private const val ActionSelector = "statusItemClicked:"
}
