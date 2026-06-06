package dev.jisungbin.copyurl

import com.sun.jna.NativeLibrary
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.Structure

/** Objective-C 런타임(libobjc) 호출 헬퍼. objc_msgSend 는 nil 을 반환할 수 있어 모두 nullable. */
object ObjC {
    private val lib = NativeLibrary.getInstance("objc")
    val msgSend = lib.getFunction("objc_msgSend")
    private val fGetClass = lib.getFunction("objc_getClass")
    private val fSel = lib.getFunction("sel_registerName")
    private val fAllocateClassPair = lib.getFunction("objc_allocateClassPair")
    private val fRegisterClassPair = lib.getFunction("objc_registerClassPair")
    private val fAddMethod = lib.getFunction("class_addMethod")

    fun cls(name: String): Pointer? = fGetClass.invokePointer(arrayOf<Any>(name))
    fun sel(name: String): Pointer = fSel.invokePointer(arrayOf<Any>(name))
    fun allocateClassPair(superclass: Pointer, name: String): Pointer? =
        fAllocateClassPair.invokePointer(arrayOf<Any>(superclass, name, NativeLong(0L)))

    fun registerClassPair(cls: Pointer) {
        fRegisterClassPair.invoke(Void.TYPE, arrayOf<Any>(cls))
    }

    fun addMethod(cls: Pointer, selector: Pointer, implementation: Pointer, types: String): Boolean =
        fAddMethod.invokeInt(arrayOf<Any>(cls, selector, implementation, types)) != 0

    fun sendP(receiver: Pointer, selector: String, vararg args: Any): Pointer? =
        msgSend.invokePointer(arrayOf(receiver, sel(selector), *args))

    fun sendV(receiver: Pointer, selector: String, vararg args: Any?) {
        msgSend.invoke(Void.TYPE, arrayOf(receiver, sel(selector), *args))
    }

    /** Kotlin String → NSString. */
    fun nsString(s: String): Pointer? {
        val c = cls("NSString") ?: return null
        return msgSend.invokePointer(arrayOf<Any>(c, sel("stringWithUTF8String:"), s))
    }

    /** NSString → Kotlin String. */
    fun stringValue(ns: Pointer?): String? {
        ns ?: return null
        val cstr = msgSend.invokePointer(arrayOf<Any>(ns, sel("UTF8String"))) ?: return null
        return cstr.getString(0, "UTF-8")
    }

    /** NSRect(=CGRect, 4×double) 반환 메시지. ARM64 에선 HFA 라 일반 objc_msgSend 로 반환된다. */
    fun sendRect(receiver: Pointer, selector: String): NSRect =
        msgSend.invoke(NSRect::class.java, arrayOf<Any>(receiver, sel(selector))) as NSRect

    @Structure.FieldOrder("x", "y", "width", "height")
    class NSRect : Structure(), Structure.ByValue {
        @JvmField var x: Double = 0.0
        @JvmField var y: Double = 0.0
        @JvmField var width: Double = 0.0
        @JvmField var height: Double = 0.0
    }
}
