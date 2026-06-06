package dev.jisungbin.copyurl

import com.sun.jna.Callback
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import java.util.Collections

/**
 * AWT 스레드에서 넘긴 작업을 macOS 메인 스레드(main dispatch queue)에서 실행한다.
 * dispatch_get_main_queue() 는 export 함수가 아니라 _dispatch_main_q 전역을 가리키는
 * 매크로라 dlsym 으로 못 찾는다 → 전역 변수 주소를 직접 얻어 큐로 쓴다.
 */
object MainDispatch {
    private val pending = Collections.synchronizedSet(mutableSetOf<DispatchFunction>())
    private val lib = runCatching { NativeLibrary.getInstance("System") }.getOrNull()
    private val mainQueue = runCatching { lib?.getGlobalVariableAddress("_dispatch_main_q") }.getOrNull()
    private val dispatchAsyncF = runCatching { lib?.getFunction("dispatch_async_f") }.getOrNull()

    fun async(block: () -> Unit) {
        if (mainQueue == null || dispatchAsyncF == null) {
            Dbg.log("libdispatch main queue 확보 실패 → main queue 작업 생략")
            return
        }

        val callback = object : DispatchFunction {
            override fun callback(context: Pointer?) {
                try {
                    block()
                } finally {
                    pending.remove(this)
                }
            }
        }
        pending.add(callback)
        dispatchAsyncF.invoke(Void.TYPE, arrayOf<Any?>(mainQueue, null, callback))
    }

    private interface DispatchFunction : Callback {
        fun callback(context: Pointer?)
    }
}
