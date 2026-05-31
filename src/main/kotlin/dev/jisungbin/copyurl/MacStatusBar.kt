package dev.jisungbin.copyurl

import com.sun.jna.Pointer

/**
 * 메뉴바: NSStatusItem + SF Symbol("link") + NSMenu(종료) + NSApp 런루프.
 * NSStatusItem(UI 요소)이 있어야 NSApp 이 전역 핫키 이벤트를 펌프한다 — 순수
 * 백그라운드(UI 0)에서는 Carbon 핫키 콜백이 오지 않아 이 방식이 필요하다.
 */
object MacStatusBar {
    private var statusItem: Pointer? = null
    private var menu: Pointer? = null

    fun setup() {
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

        // 버튼 이미지 = SF Symbol "link" (벡터, 자동 다크/라이트)
        val button = ObjC.sendP(item, "button")
        val nsImageCls = ObjC.cls("NSImage")
        val symName = ObjC.nsString("link")
        val desc = ObjC.nsString("URL 복사")
        if (button != null && nsImageCls != null && symName != null && desc != null) {
            val image = ObjC.msgSend.invokePointer(
                arrayOf<Any>(
                    nsImageCls,
                    ObjC.sel("imageWithSystemSymbolName:accessibilityDescription:"),
                    symName, desc,
                ),
            )
            if (image != null) ObjC.sendV(button, "setImage:", image)
        }

        // 메뉴: "종료" (terminate:)
        val nsMenuCls = ObjC.cls("NSMenu") ?: return
        val menuAlloc = ObjC.sendP(nsMenuCls, "alloc") ?: return
        val m = ObjC.sendP(menuAlloc, "init") ?: return
        ObjC.sendP(m, "retain")
        menu = m
        val quitTitle = ObjC.nsString("종료") ?: return
        val empty = ObjC.nsString("") ?: return
        ObjC.msgSend.invokePointer(
            arrayOf<Any>(
                m,
                ObjC.sel("addItemWithTitle:action:keyEquivalent:"),
                quitTitle, ObjC.sel("terminate:"), empty,
            ),
        )
        ObjC.sendV(item, "setMenu:", m)

        println("[copy-url] ✅ 메뉴바(NSStatusItem + SF Symbol) 구성됨")
    }

    /** NSApp 런루프 시작 (블로킹). 메인 스레드(-XstartOnFirstThread)에서 호출. */
    fun run() {
        val nsAppCls = ObjC.cls("NSApplication") ?: return
        val nsApp = ObjC.sendP(nsAppCls, "sharedApplication") ?: return
        ObjC.sendV(nsApp, "run")
    }
}
