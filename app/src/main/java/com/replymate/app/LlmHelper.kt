package com.replymate.app

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Thin wrapper around MediaPipe's LLM Inference API running Gemma fully
 * on-device. Nothing here touches the network.
 *
 * Setup (one-time, per phone):
 *   1. Download a .task model file, e.g. "gemma-2b-it-cpu-int4.task"
 *      from https://huggingface.co/litert-community  (search "Gemma" + "task")
 *   2. adb push gemma-2b-it-cpu-int4.task /data/local/tmp/llm/model.task
 *      (or use MainActivity's file picker to copy it into app-private storage)
 */
class LlmHelper(private val context: Context) {

    private var engine: LlmInference? = null

    val modelFile: File
        get() = File(context.filesDir, "model.task")

    val isModelReady: Boolean
        get() = modelFile.exists()

    suspend fun ensureLoaded(): Boolean = withContext(Dispatchers.IO) {
        if (engine != null) return@withContext true
        if (!isModelReady) return@withContext false

        val options = LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(256)
            .setTopK(40)
            .setTemperature(0.9f)
            .setRandomSeed(0)
            .build()

        engine = LlmInference.createFromOptions(context, options)
        true
    }

    /**
     * Given the last few lines of a chat, ask the model for 3 short reply
     * options in the requested tone.
     */
    suspend fun suggestReplies(
        recentLines: List<String>,
        tone: String = "casual, warm, a little playful"
    ): List<String> = withContext(Dispatchers.IO) {
        val loaded = ensureLoaded()
        if (!loaded) return@withContext listOf("(Model not loaded - copy a .task model file into app storage first)")

        val transcript = recentLines.takeLast(12).joinToString("\n")
        val prompt = buildString {
            appendLine("You are helping me write my next reply in an ongoing chat.")
            appendLine("Tone: $tone. Keep replies short, natural, and in the same language as the conversation.")
            appendLine("Do not add explanations, only the replies.")
            appendLine("Conversation so far:")
            appendLine(transcript)
            appendLine()
            appendLine("Give exactly 3 reply options, one per line, no numbering.")
        }

        val raw = engine?.generateResponse(prompt).orEmpty()
        raw.lines()
            .map { it.trim().trimStart('-', '*', '1', '2', '3', '.', ')').trim() }
            .filter { it.isNotEmpty() }
            .take(3)
    }

    fun close() {
        engine?.close()
        engine = null
    }
}
