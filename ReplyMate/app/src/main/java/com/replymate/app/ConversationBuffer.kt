package com.replymate.app

/**
 * Simple in-memory holder for the chat text that the AccessibilityService
 * has scraped off the current screen. Both services run in the same process,
 * so a plain singleton is enough - no need for IPC.
 */
object ConversationBuffer {

    // Last N lines pulled from the screen, oldest first.
    private val lines = ArrayDeque<String>()
    private const val MAX_LINES = 25

    // Package name of the app currently on screen (e.g. "com.whatsapp").
    @Volatile var currentApp: String = ""
        private set

    // Notified whenever new text shows up, so the overlay can decide to
    // (re)generate a suggestion.
    var onUpdate: ((List<String>) -> Unit)? = null

    @Synchronized
    fun updateFromScreen(app: String, screenLines: List<String>) {
        currentApp = app
        val cleaned = screenLines
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.length in 1..300 }
            .distinct()

        if (cleaned.isEmpty()) return

        // Replace the buffer with the freshest scrape rather than trying to
        // diff old vs new UI trees - chat apps re-lay-out text constantly and
        // diffing produces more noise than it removes.
        lines.clear()
        cleaned.takeLast(MAX_LINES).forEach { lines.addLast(it) }

        onUpdate?.invoke(lines.toList())
    }

    fun snapshot(): List<String> = synchronized(this) { lines.toList() }

    fun clear() = synchronized(this) { lines.clear() }
}
