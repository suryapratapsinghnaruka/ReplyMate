package com.replymate.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

/**
 * Foreground service that:
 *  1. Watches ConversationBuffer for new screen text
 *  2. Debounces + calls the local Gemma model for 3 reply suggestions
 *  3. Shows them in a floating bubble AND as a notification
 *  4. On tap, fills the chosen reply into the focused chat text box
 *     (via ChatAccessibilityService) - you still press Send yourself.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private lateinit var llm: LlmHelper

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var debounceJob: Job? = null

    companion object {
        const val CHANNEL_ID = "replymate_suggestions"
        const val NOTIFICATION_ID = 1
        const val FOREGROUND_ID = 2
        private const val DEBOUNCE_MS = 1200L
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        llm = LlmHelper(this)
        createNotificationChannels()
        startForeground(FOREGROUND_ID, buildForegroundNotification())

        ConversationBuffer.onUpdate = { lines -> onScreenTextChanged(lines) }
    }

    private fun onScreenTextChanged(lines: List<String>) {
        // Debounce: wait for the chat to "settle" before spending a
        // generation call, since UI trees fire multiple events per second.
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_MS)
            generateAndShow(lines)
        }
    }

    private suspend fun generateAndShow(lines: List<String>) {
        val suggestions = withContext(Dispatchers.Default) {
            llm.suggestReplies(lines)
        }
        if (suggestions.isEmpty()) return
        showBubble(suggestions)
        showNotification(suggestions)
    }

    // ---- Floating bubble ----

    private fun showBubble(suggestions: List<String>) {
        removeBubble()

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_bubble, null)
        bubbleView = view

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 120
        }

        val buttons = listOf<Button>(
            view.findViewById(R.id.suggestion1),
            view.findViewById(R.id.suggestion2),
            view.findViewById(R.id.suggestion3)
        )
        buttons.forEachIndexed { i, button ->
            val text = suggestions.getOrNull(i)
            if (text == null) {
                button.visibility = View.GONE
            } else {
                button.visibility = View.VISIBLE
                button.text = text
                button.setOnClickListener {
                    val filled = ChatAccessibilityService.instance?.fillReply(text) ?: false
                    if (!filled) {
                        // Fallback: at least copy it so the user can paste manually.
                        copyToClipboard(text)
                    }
                    removeBubble()
                }
            }
        }

        view.findViewById<Button>(R.id.regenerate).setOnClickListener {
            scope.launch { generateAndShow(ConversationBuffer.snapshot()) }
        }
        view.findViewById<Button>(R.id.close).setOnClickListener { removeBubble() }

        windowManager.addView(view, params)
    }

    private fun removeBubble() {
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        bubbleView = null
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("reply", text))
    }

    // ---- Notification ----

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Reply suggestions", NotificationManager.IMPORTANCE_DEFAULT)
        )
        nm.createNotificationChannel(
            NotificationChannel("replymate_fg", "ReplyMate running", NotificationManager.IMPORTANCE_MIN)
        )
    }

    private fun buildForegroundNotification(): Notification =
        NotificationCompat.Builder(this, "replymate_fg")
            .setContentTitle("ReplyMate is watching for chats")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

    private fun showNotification(suggestions: List<String>) {
        val nm = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("Suggested replies")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(suggestions.joinToString("\n\n"))
            )
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        removeBubble()
        llm.close()
        scope.cancel()
        ConversationBuffer.onUpdate = null
    }
}
