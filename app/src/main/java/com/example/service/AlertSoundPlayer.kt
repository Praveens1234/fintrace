package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.data.repository.PriceMonitorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

object AlertSoundPlayer {
    private var currentRingtone: Ringtone? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    @Volatile private var ttsLocale: Locale = Locale.US
    private val handler = Handler(Looper.getMainLooper())
    private var stopRunnable: Runnable? = null

    fun initTts(context: Context) {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    applyTtsLocale()
                } else {
                    Log.e("AlertSoundPlayer", "TTS Initialization failed")
                }
            }
        }
    }

    fun updateTtsLocale(languageTag: String, context: Context) {
        ttsLocale = parseLocale(languageTag)
        if (tts != null) {
            applyTtsLocale()
        } else {
            initTts(context)
        }
    }

    private fun parseLocale(languageTag: String): Locale = when (languageTag) {
        "hi-IN" -> Locale("hi", "IN")
        else -> Locale.US
    }

    private fun applyTtsLocale() {
        tts?.let { ttsInstance ->
            val result = ttsInstance.setLanguage(ttsLocale)
            isTtsReady = when (result) {
                TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED -> {
                    Log.w("AlertSoundPlayer", "TTS locale $ttsLocale not available, falling back to English")
                    val fallback = ttsInstance.setLanguage(Locale.US)
                    fallback != TextToSpeech.LANG_MISSING_DATA && fallback != TextToSpeech.LANG_NOT_SUPPORTED
                }
                else -> true
            }
        }
    }

    fun buildAlertText(symbol: String, formattedPrice: String, languageTag: String): String {
        val symSpoken = symbol.replace("/", " ")
        return when (languageTag) {
            "hi-IN" -> "सूचना: $symSpoken का मूल्य $formattedPrice पर पहुंचा"
            else -> "Alert: $symbol crossed target price of $formattedPrice"
        }
    }

    fun playAlertSound(context: Context, textToSpeak: String, priority: String) {
        handler.post {
            try {
                stopPlayback()

                val monitor = PriceMonitorManager.getInstance(context)
                val scope = CoroutineScope(Dispatchers.IO)
                
                scope.launch {
                    val suffix = priority.lowercase(Locale.US)
                    val pSoundUriStr = monitor.getSetting("alert_sound_uri_$suffix")
                    val pDurationSec = monitor.getSetting("alert_ring_duration_sec_$suffix")?.toIntOrNull()
                    val pSoundMode = monitor.getSetting("alert_sound_mode_$suffix")

                    val soundUriStr = if (!pSoundUriStr.isNullOrEmpty()) pSoundUriStr else (monitor.getSetting("alert_sound_uri") ?: "")
                    val durationSec = pDurationSec ?: monitor.getSetting("alert_ring_duration_sec")?.toIntOrNull() ?: 5
                    val soundMode = if (!pSoundMode.isNullOrEmpty()) pSoundMode else (monitor.getSetting("alert_sound_mode") ?: "Both Tone and Voice")

                    withContext(Dispatchers.Main) {
                        // Play TTS voice
                        if (soundMode == "Both Tone and Voice" || soundMode == "TTS voice only") {
                            initTts(context)
                            if (isTtsReady) {
                                tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "ALERT_TTS")
                            } else {
                                tts = TextToSpeech(context.applicationContext) { status ->
                                    if (status == TextToSpeech.SUCCESS) {
                                        applyTtsLocale()
                                        tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "ALERT_TTS")
                                    }
                                }
                            }
                        }

                        // Play selected Tone / Ringtone
                        if (soundMode == "Both Tone and Voice" || soundMode == "Tone alert only") {
                            val uri = if (soundUriStr.isNotEmpty()) {
                                Uri.parse(soundUriStr)
                            } else {
                                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                            }

                            if (uri != null) {
                                val ringtone = RingtoneManager.getRingtone(context.applicationContext, uri)
                                if (ringtone != null) {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                        val aa = AudioAttributes.Builder()
                                            .setUsage(AudioAttributes.USAGE_ALARM)
                                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                            .build()
                                        ringtone.audioAttributes = aa
                                    }
                                    ringtone.play()
                                    currentRingtone = ringtone

                                    // Schedule to stop ringing after the chosen duration
                                    val runnable = Runnable {
                                        stopPlayback()
                                    }
                                    stopRunnable = runnable
                                    handler.postDelayed(runnable, durationSec * 1000L)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AlertSoundPlayer", "Failed to play alert sound: ${e.message}")
            }
        }
    }

    fun stopPlayback() {
        try {
            stopRunnable?.let {
                handler.removeCallbacks(it)
                stopRunnable = null
            }
            currentRingtone?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                currentRingtone = null
            }
            tts?.let {
                if (it.isSpeaking) {
                    it.stop()
                }
            }
        } catch (e: Exception) {
            Log.e("AlertSoundPlayer", "Error stopping playback: ${e.message}")
        }
    }

    fun shutdown() {
        stopPlayback()
        tts?.shutdown()
        tts = null
        isTtsReady = false
    }
}
