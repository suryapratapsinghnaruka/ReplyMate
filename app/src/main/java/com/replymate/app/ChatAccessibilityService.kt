package com.replymate.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Walks the on-screen accessibility node tree, pulls out visible text, and
 * drops it into ConversationBuffer. Also exposes fillReply() so the overlay
 * can push a suggested reply into whatever text box currently has focus.
 *
 * Note: this reads text that is ALREADY visible/exposed to accessibility
 * services (the same channel screen readers use). It does not screenshot or
 * OCR anything.
 */
class ChatAccessibilityService : AccessibilityService() {

    companion object {
        // Set by OverlayService so it can call fillReply() on us directly.
        @Volatile var instance: ChatAccessibilityService? = null

        // Launchers to skip - no point scraping your own home screen etc.
        private val IGNORED_PACKAGES = setOf(
            "com.replymate.app",
            "com.android.systemui",
            "com.android.launcher3"
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg in IGNORED_PACKAGES) return

        val root = rootInActiveWindow ?: return
        val textLines = mutableListOf<String>()
        collectText(root, textLines, depth = 0)

        if (textLines.isNotEmpty()) {
            ConversationBuffer.updateFromScreen(pkg, textLines)
        }
    }

    private fun collectText(node: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int) {
        if (node == null || depth > 40 || out.size > 60) return

        node.text?.toString()?.let { if (it.isNotBlank()) out.add(it) }
        node.contentDescription?.toString()?.let { if (it.isNotBlank()) out.add(it) }

        for (i in 0 until node.childCount) {
            collectText(node.getChild(i), out, depth + 1)
        }
    }

    /**
     * Finds the currently focused editable field on screen and sets its text.
     * Returns true if it found one. The user still has to tap Send themselves.
     */
    fun fillReply(text: String): Boolean {
        val focused = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return false
        if (!focused.isEditable) return false

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }
}
