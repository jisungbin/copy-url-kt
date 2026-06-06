package dev.jisungbin.copyurl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

private const val MaxHistorySize = 50

class CopyUrlAppState {
    var isHistoryVisible by mutableStateOf(false)
        private set

    val history = mutableStateListOf<CopiedUrlEntry>()

    fun toggleHistory() {
        isHistoryVisible = !isHistoryVisible
    }

    fun hideHistory() {
        isHistoryVisible = false
    }

    fun record(entry: CopiedUrlEntry) {
        history.removeAll { it.url == entry.url }
        history.add(0, entry)
        if (history.size > MaxHistorySize) {
            history.removeRange(MaxHistorySize, history.size)
        }
    }

    fun remove(entry: CopiedUrlEntry) {
        history.remove(entry)
    }

    fun clear() {
        history.clear()
    }
}
