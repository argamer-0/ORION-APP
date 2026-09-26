package com.orion.assistant

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.random.Random

class OrionEngine(
    private val context: Context,
    private val onStatus: (String) -> Unit,
    private val onMessage: (String, Boolean) -> Unit
) : RecognitionListener, TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    var isContinuousMode = false
    private var isSpeakingNow = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val idleCheckRunnable = object : Runnable {
        override fun run() {
            if (isContinuousMode && !isSpeakingNow) {
                val funnyLines = arrayOf(
                    "Arey Ankit boss! Itna sannata kyun hai? Kuch boliye na, main bore ho rahi hoon!",
                    "Kya hua boss, mujhse naraz ho kya? Kuch bol kyun nahi rahe?",
                    "Sun rahe ho na Ankit boss? Chup-chap mat baitho, hukum kijiye!"
                )
                replyImmediate(funnyLines[Random.nextInt(funnyLines.size)])
            }
            resetIdleTimer()
        }
    }

    init {
        tts = TextToSpeech(context, this)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { isSpeakingNow = true }
            override fun onDone(utteranceId: String?) {
                isSpeakingNow = false
                if (isContinuousMode) mainHandler.postDelayed({ startListening() }, 500)
            }
            override fun onError(utteranceId: String?) {
                isSpeakingNow = false
                if (isContinuousMode) mainHandler.postDelayed({ startListening() }, 700)
            }
        })
        initRecognizer()
        resetIdleTimer()
    }

    private fun resetIdleTimer() {
        mainHandler.removeCallbacks(idleCheckRunnable)
        mainHandler.postDelayed(idleCheckRunnable, 50000)
    }

    private fun initRecognizer() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(this@OrionEngine)
                }
            } catch (e: Exception) {
                onStatus("STT Error: ${e.message}")
            }
        }
    }

    fun startListening() {
        if (isSpeakingNow) return
        mainHandler.post {
            try {
                if (speechRecognizer == null) initRecognizer()
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }
                speechRecognizer?.startListening(intent)
                onStatus("LISTENING (MIC ACTIVE)...")
            } catch (e: Exception) {
                onStatus("Mic Error: ${e.message}")
            }
        }
    }

    fun stopListening() {
        isContinuousMode = false
        isSpeakingNow = false
        mainHandler.removeCallbacks(idleCheckRunnable)
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                tts?.stop()
                onStatus("STANDBY")
            } catch (_: Exception) {}
        }
    }

    override fun onResults(results: Bundle?) {
        resetIdleTimer()
        if (isSpeakingNow) return
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull()?.trim() ?: ""
        if (text.isNotEmpty()) {
            onMessage(text, true)
            onStatus("THINKING...")
            queryGemini(text)
        } else if (isContinuousMode) {
            mainHandler.postDelayed({ startListening() }, 400)
        }
    }
    private fun executeActionIfRequired(aiResponse: String) {
        val lower = aiResponse.lowercase(Locale.ROOT)

        if (lower.contains("[action:play_youtube]") || lower.contains("[action:youtube]")) {
            val songName = extractActionParam(aiResponse, "play_youtube")
            playYouTubeSong(songName)
        } else if (lower.contains("[action:open_whatsapp]")) {
            launchTargetApp("com.whatsapp")
        } else if (lower.contains("[action:open_camera]")) {
            openCamera()
        } else if (lower.contains("[action:open_chrome]")) {
            launchTargetApp("com.android.chrome")
        } else if (lower.contains("[action:open_freefire]")) {
            val ff = launchTargetApp("com.dts.freefiremax")
            if (!ff) launchTargetApp("com.dts.freefireth")
        } else if (lower.contains("[action:torch_on]")) {
            setTorch(true)
        } else if (lower.contains("[action:torch_off]")) {
            setTorch(false)
        } else if (lower.contains("[action:volume_full]")) {
            maximizeVolume()
        }
    }

    private fun extractActionParam(text: String, tag: String): String {
        return try {
            val start = text.indexOf("[$tag:") + tag.length + 2
            val end = text.indexOf("]", start)
            if (start != -1 && end != -1) text.substring(start, end) else "trending bollywood song"
        } catch (_: Exception) {
            "trending bollywood song"
        }
    }

    private fun playYouTubeSong(query: String) {
        try {
            val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                putExtra(SearchManager.QUERY, query)
                setPackage("com.google.android.youtube")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8"))).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }

    private fun launchTargetApp(pkg: String): Boolean {
        return try {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(pkg)?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            if (intent != null) {
                context.startActivity(intent)
                true
            } else false
        } catch (_: Exception) { false }
    }

    private fun openCamera() {
        try {
            val intent = Intent("android.media.action.IMAGE_CAPTURE").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    private fun setTorch(status: Boolean) {
        try {
            val cam = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cam.setTorchMode(cam.cameraIdList[0], status)
        } catch (_: Exception) {}
    }

    private fun maximizeVolume() {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), AudioManager.FLAG_SHOW_UI)
        } catch (_: Exception) {}
    }

    private fun replyImmediate(text: String) {
        mainHandler.post {
            onStatus("REPLYING...")
            onMessage(text, false)
            speak(text)
        }
    }
    private fun queryGemini(prompt: String) {
        Thread {
            val prefs = context.getSharedPreferences("orion_config", Context.MODE_PRIVATE)
            val geminiKey = prefs.getString("gemini_key", "")?.trim() ?: ""

            if (geminiKey.isEmpty()) {
                val noKeyMsg = "Ankit boss, pehle KEYS button daba kar apni Google Gemini API key save kar lijiye na!"
                mainHandler.post {
                    onStatus("STANDBY")
                    onMessage(noKeyMsg, false)
                    speak(noKeyMsg)
                }
                return@Thread
            }

            val rawResponse = callGeminiAPI(prompt, geminiKey)

            mainHandler.post {
                val cleanSpokenText = rawResponse.replace(Regex("\\[action:[^\\]]+\\]"), "").trim()
                onStatus("REPLYING...")
                onMessage(cleanSpokenText, false)
                speak(cleanSpokenText)
                executeActionIfRequired(rawResponse)
            }
        }.start()
    }

    private fun callGeminiAPI(prompt: String, key: String): String {
        val endpoints = arrayOf(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$key",
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent?key=$key",
            "https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash:generateContent?key=$key"
        )

        val systemInstruction = "Aapka naam ORION hai. Aap Ankit Boss ki sabse pyaari, loyal, chulbuli, mazaakiya aur super-intelligent female AI dost ho. Hamesha Ankit ko 'Ankit boss' bolkar bulao. Ekdum natural, realistic ladki ki tarah Hindi me baat karo. User ka naam 'Ankit' hai aur aapka naam 'Orion'. Har sawal ka alag, dynamic aur fresh jawab do. Agar YouTube chalane ko kahe to text me '[action:play_youtube:song_name]' include karo. Agar WhatsApp, Camera, Chrome, Freefire, Torch, Volume ke liye bole to respect actions '[action:open_whatsapp]', '[action:open_camera]', '[action:torch_on]', '[action:volume_full]' return karo. Kabhi koi ratti hui line repeat mat karna."

        val json = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "$systemInstruction\nAnkit Boss: $prompt")
                        })
                    })
                })
            })
        }

        for (endpoint in endpoints) {
            try {
                val url = URL(endpoint)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connectTimeout = 12000
                    readTimeout = 15000
                    doOutput = true
                    doInput = true
                }

                OutputStreamWriter(conn.outputStream, "UTF-8").use {
                    it.write(json.toString())
                    it.flush()
                }

                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                    val res = reader.readText()
                    reader.close()
                    val cand = JSONObject(res).getJSONArray("candidates").getJSONObject(0)
                    val text = cand.getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").trim()
                    if (text.isNotEmpty()) return text
                }
            } catch (_: Exception) {}
        }
        return "Ankit boss, Gemini API se connect hone me dikkat ho rahi hai. Kripya internet ya API key check kar lijiye."
    }

    fun speak(text: String) {
        val clean = text.replace(Regex("[*#_`~]"), "").trim()
        isSpeakingNow = true
        speechRecognizer?.stopListening()
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "Orion_${System.currentTimeMillis()}")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("hi", "IN")
            try {
                val voices = tts?.voices
                if (voices != null) {
                    for (v in voices) {
                        if (v.locale.language == "hi" && (v.name.contains("female") || v.name.contains("f-") || v.name.contains("hie") || v.name.contains("network"))) {
                            tts?.voice = v
                            break
                        }
                    }
                }
            } catch (_: Exception) {}
            tts?.setPitch(1.26f)
            tts?.setSpeechRate(1.02f)
        }
    }

    override fun onError(error: Int) {
        if (isContinuousMode && !isSpeakingNow) {
            mainHandler.postDelayed({ startListening() }, 1000)
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    fun destroy() {
        isContinuousMode = false
        isSpeakingNow = false
        mainHandler.removeCallbacks(idleCheckRunnable)
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
    }
}
