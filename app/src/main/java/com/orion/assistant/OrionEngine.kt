package com.orion.assistant

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
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
                val lines = arrayOf(
                    "Kya hua Ankit boss? Gussa ho kya, kuch bol kyun nahi rahe?",
                    "Arey bolo na boss! Main kabse wait kar rahi hoon!",
                    "Sun rahe ho na Ankit? Itna sannata kyun hai?",
                    "Bolo na Ankit boss, naraz ho kya mujhse?"
                )
                replyImmediate(lines[Random.nextInt(lines.size)])
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
            handleCommandOrQuery(text)
        } else if (isContinuousMode) {
            mainHandler.postDelayed({ startListening() }, 400)
        }
    }
    private fun handleCommandOrQuery(prompt: String) {
        val lower = prompt.lowercase(Locale.ROOT)

        // 1. YouTube & Songs
        if (lower.contains("youtube") || prompt.contains("यूट्यूब") || prompt.contains("युटुब") || prompt.contains("गाना") || lower.contains("song")) {
            val q = prompt.replace(Regex("(?i)youtube|यूट्यूब|युटुब|open|kholo|chalao|bajao|laga do|song|gana|play"), "").trim()
            val finalQ = if (q.isEmpty()) "latest hindi songs" else q
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + URLEncoder.encode(finalQ, "UTF-8"))).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                replyImmediate("YouTube par $finalQ chala rahi hoon Ankit boss!")
            } catch (_: Exception) {
                launchApp("com.google.android.youtube", "YouTube open kar diya hai boss!")
            }
            return
        }

        // 2. WhatsApp
        if (lower.contains("whatsapp") || prompt.contains("व्हाट्सएप") || prompt.contains("वाट्सएप")) {
            if (lower.contains("message") || prompt.contains("भेजो") || lower.contains("bhejo") || lower.contains("send")) {
                OrionAutomationService.shouldAutoSendWhatsApp = true
                val sendIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://api.whatsapp.com/send?text=" + URLEncoder.encode(prompt, "UTF-8"))
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(sendIntent)
                replyImmediate("WhatsApp message prepare kar diya hai Ankit!")
            } else {
                launchApp("com.whatsapp", "WhatsApp open kar diya hai boss.")
            }
            return
        }

        // 3. Camera
        if (lower.contains("camera") || prompt.contains("कैमरा") || prompt.contains("फोटो")) {
            try {
                val intent = Intent("android.media.action.IMAGE_CAPTURE").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
                replyImmediate("Camera open kar diya hai Ankit boss, smile karo!")
            } catch (_: Exception) {
                replyImmediate("Camera open nahi ho paya.")
            }
            return
        }

        // 4. Free Fire MAX
        if (lower.contains("free fire") || prompt.contains("फ्री फायर") || lower.contains("ff")) {
            val ff = launchApp("com.dts.freefiremax", "Free Fire MAX shuru! Aaj booyah nikalna hai boss!")
            if (!ff) launchApp("com.dts.freefireth", "Free Fire launch ho raha hai boss!")
            return
        }

        // 5. Chrome / Web Tasks
        if (lower.contains("chrome") || prompt.contains("क्रोम") || lower.contains("website") || lower.contains("download") || prompt.contains("डाउनलोड")) {
            val query = prompt.replace(Regex("(?i)chrome|open|kholo|browser|search|pe jao"), "").trim()
            val target = if (query.startsWith("http")) query else "https://www.google.com/search?q=" + URLEncoder.encode(query, "UTF-8")
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
                replyImmediate("Chrome par search khol diya hai Ankit boss!")
            } catch (_: Exception) {
                launchApp("com.android.chrome", "Chrome open kar diya hai.")
            }
            return
        }

        // 6. Flashlight
        if (lower.contains("torch") || lower.contains("flashlight") || prompt.contains("टॉर्च")) {
            val on = !lower.contains("off") && !lower.contains("band") && !prompt.contains("बंद")
            toggleFlashlight(on)
            return
        }

        // 7. General Gemini AI
        onStatus("THINKING...")
        queryGemini(prompt)
    }

    private fun launchApp(pkg: String, successText: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            if (intent != null) {
                context.startActivity(intent)
                replyImmediate(successText)
                true
            } else {
                val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(playIntent)
                replyImmediate("Ye app phone me nahi hai, Play Store par khol diya hai.")
                true
            }
        } catch (_: Exception) {
            replyImmediate("App open nahi ho pa raha hai.")
            false
        }
    }

    private fun toggleFlashlight(on: Boolean) {
        try {
            val cam = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cam.setTorchMode(cam.cameraIdList[0], on)
            replyImmediate(if (on) "Torch on kar di hai maine!" else "Torch band kar di hai.")
        } catch (_: Exception) {
            replyImmediate("Torch control nahi ho paayi.")
        }
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

            var answer = ""
            if (geminiKey.isNotEmpty()) {
                answer = callGeminiAPI(prompt, geminiKey)
            }

            if (answer.isEmpty()) {
                answer = if (geminiKey.isEmpty()) {
                    "Ankit boss, upar KEYS button par click karke apni Google Gemini API key save kar lijiye na!"
                } else {
                    "Haan Ankit boss, main hamesha aapke sath hoon! Bataiye kya karna hai?"
                }
            }

            mainHandler.post {
                onStatus("REPLYING...")
                onMessage(answer, false)
                speak(answer)
            }
        }.start()
    }

    private fun callGeminiAPI(prompt: String, key: String): String {
        return try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$key")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connectTimeout = 12000
                readTimeout = 15000
                doOutput = true
                doInput = true
            }

            val systemInstruction = "Aapka naam ORION hai. Aap Ankit ki behad pyaari, intelligent, friendly aur chulbuli Hindi female companion ho. Robot ya kitabi bhasha bilkul mat bolo. Natural, filmy, thoda mazaakiya aur caring ladki ki tarah Hindi me baat karo. Agar joke poocha jaye toh asli hansaane wala joke sunao. Har baar alag aur dynamic jawab do."

            val json = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "$systemInstruction\nUser ne kaha: $prompt")
                            })
                        })
                    })
                })
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(json.toString())
                it.flush()
            }

            val code = conn.responseCode
            if (code == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                val res = reader.readText()
                reader.close()
                val cand = JSONObject(res).getJSONArray("candidates").getJSONObject(0)
                cand.getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").trim()
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }
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
            // Google High-Quality Natural Female Voice Auto-Select
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
            tts?.setPitch(1.22f) // Sweet Female Tone
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
