package dev.jisungbin.copyurl

data class CopiedUrlEntry(
    val url: String,
    val label: String,
    val copiedAt: String,
    val cleaned: Boolean,
)
