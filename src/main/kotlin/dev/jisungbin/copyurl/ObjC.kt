package dev.jisungbin.copyurl

import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer

/** Objective-C 런타임(libobjc) 호출 헬퍼. objc_msgSend 는 nil 을 반환할 수 있어 모두 nullable. */
object ObjC {
    private val lib = NativeLibrary.getInstance("objc")
    val msgSend = lib.getFunction("objc_msgSend")
    private val fGetClass = lib.getFunction("objc_getClass")
    private val fSel = lib.getFunction("sel_registerName")

    fun cls(name: String): Pointer? = fGetClass.invokePointer(arrayOf<Any>(name))
    fun sel(name: String): Pointer = fSel.invokePointer(arrayOf<Any>(name))

    fun sendP(receiver: Pointer, selector: String, vararg args: Any): Pointer? =
        msgSend.invokePointer(arrayOf(receiver, sel(selector), *args))

    fun sendV(receiver: Pointer, selector: String, vararg args: Any) {
        msgSend.invoke(Void.TYPE, arrayOf(receiver, sel(selector), *args))
    }

    /** Kotlin String → NSString. */
    fun nsString(s: String): Pointer? {
        val c = cls("NSString") ?: return null
        return msgSend.invokePointer(arrayOf<Any>(c, sel("stringWithUTF8String:"), s))
    }
}
