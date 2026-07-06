package com.replymate.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.replymate.app.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var llm: LlmHelper

    private val pickModel = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { copyModelToAppStorage(it) }
    }

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        llm = LlmHelper(this)

        binding.btnPickModel.setOnClickListener {
            pickModel.launch(arrayOf("*/*"))
        }

        binding.btnEnableAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.btnEnableOverlay.setOnClickListener {
            val uri = Uri.parse("package:$packageName")
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri))
        }

        binding.btnStart.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            startForegroundService(Intent(this, OverlayService::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun copyModelToAppStorage(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            contentResolver.openInputStream(uri)?.use { input ->
                File(filesDir, "model.task").outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            runOnUiThread { refreshStatus() }
        }
    }

    private fun refreshStatus() {
        val modelReady = llm.isModelReady
        binding.statusModel.text = if (modelReady)
            "1. Model: loaded (${llm.modelFile.length() / 1_000_000} MB)"
        else
            "1. Model: not loaded"

        val accessibilityOn = isAccessibilityServiceEnabled()
        binding.statusAccessibility.text = "2. Accessibility service: ${if (accessibilityOn) "on" else "off"}"

        val overlayOn = Settings.canDrawOverlays(this)
        binding.statusOverlay.text = "3. Overlay permission: ${if (overlayOn) "on" else "off"}"

        binding.btnStart.isEnabled = modelReady && accessibilityOn && overlayOn
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = "$packageName/${ChatAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) return true
        }
        return false
    }
}
