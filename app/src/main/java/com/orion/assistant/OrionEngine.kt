package com.orion.assistant

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class OrionEngine(
    private val context: Context,
    private val onStatus: (String) -> Unit,
    private val onMessage: (String, Boolean) -> Unit
) : RecognitionListener, TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    var isContinuousMode = false
    private var isSpeakingNow = false
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        tts = TextToSpeech(context, this)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeakingNow = true
            }
            override fun onDone(utteranceId: String?) {
                isSpeakingNow = false
                if (isContinuousMode) mainHandler.postDelayed({ startListening() }, 600)
            }
            override fun onError(utteranceId: String?) {
                isSpeakingNow = false
                if (isContinuousMode) mainHandler.postDelayed({ startListening() }, 800)
            }
        })
        initRecognizer()
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
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                tts?.stop()
                mediaPlayer?.stop()
                onStatus("STANDBY")
            } catch (_: Exception) {}
        }
    }

    override fun onResults(results: Bundle?) {
        if (isSpeakingNow) return
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull()?.trim() ?: ""
        if (text.isNotEmpty() && text.length > 1) {
            onMessage(text, true)
            handleCommandOrQuery(text)
        } else if (isContinuousMode) {
            mainHandler.postDelayed({ startListening() }, 400)
        }
    }

    private fun handleCommandOrQuery(prompt: String) {
        val lower = prompt.lowercase(Locale.ROOT)

        // --- DIRECT DEVICE CONTROL COMMANDS ---
        if (lower.contains("youtube") || lower.contains("यूट्यूब") || lower.contains("युटुब")) {
            executeAppLaunch("com.google.android.youtube", "YouTube open kar raha hoon Ankit bhai.")
            return
        }

        if (lower.contains("camera") || lower.contains("कैमरा")) {
            try {
                val intent = Intent("android.media.action.IMAGE_CAPTURE").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                replyImmediate("Camera open kar diya hai boss.")
            } catch (_: Exception) {
                replyImmediate("Camera start nahi ho paya boss.")
            }
            return
        }

        if (lower.contains("whatsapp") || lower.contains("व्हाट्सएप")) {
            executeAppLaunch("com.whatsapp", "WhatsApp open kar raha hoon.")
            return
        }

        if (lower.contains("instagram") || lower.contains("इंस्टाग्राम")) {
            executeAppLaunch("com.instagram.android", "Instagram open kar raha hoon.")
            return
        }

        if (lower.contains("free fire") || lower.contains("फ्री फायर")) {
            executeAppLaunch("com.dts.freefiremax", "Free Fire MAX launch kar raha hoon bhai.")
            return
        }

        if (lower.contains("chrome") || lower.contains("क्रोम") || lower.contains("browser")) {
            executeAppLaunch("com.android.chrome", "Chrome browser open kar raha hoon.")
            return
        }

        if (lower.contains("flashlight on") || lower.contains("टॉर्च जलाओ") || lower.contains("torch on")) {
            toggleFlashlight(true)
            return
        }

        if (lower.contains("flashlight off") || lower.contains("टॉर्च बंद") || lower.contains("torch off")) {
            toggleFlashlight(false)
            return
        }

        if (lower.contains("volume full") || lower.contains("आवाज बढ़ाओ") || lower.contains("volume up")) {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), AudioManager.FLAG_SHOW_UI)
            replyImmediate("Media volume full kar diya hai boss.")
            return
        }

        // --- IF GENERAL QUESTION: ROUTE TO DYNAMIC AI ---
        onStatus("THINKING...")
        queryAI(prompt)
    }

    private fun executeAppLaunch(pkg: String, responseText: String) {
        try {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(pkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent != null) {
                context.startActivity(intent)
                replyImmediate(responseText)
            } else {
                replyImmediate("Boss ye app aapke phone mein installed nahi mila.")
            }
        } catch (e: Exception) {
            replyImmediate("App open karne mein error aaya boss.")
        }
    }

    private fun toggleFlashlight(status: Boolean) {
        try {
            val cam = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cam.cameraIdList[0]
            cam.setTorchMode(id, status)
            replyImmediate(if (status) "Torch on kar di hai boss." else "Torch band kar di hai boss.")
        } catch (_: Exception) {
            replyImmediate("Flashlight control nahi ho payi boss.")
        }
    }

    private fun replyImmediate(text: String) {
        mainHandler.post {
            onStatus("REPLYING...")
            onMessage(text, false)
            speak(text)
        }
    }

    private fun queryAI(prompt: String) {
        Thread {
            val prefs = context.getSharedPreferences("orion_config", Context.MODE_PRIVATE)
            val groqKey = prefs.getString("groq_key", "")?.trim() ?: ""
            val geminiKey = prefs.getString("gemini_key", "")?.trim() ?: ""

            var answer = ""

            if (groqKey.isNotEmpty()) {
                answer = callGroq(prompt, groqKey)
            }

            if (answer.isEmpty() && geminiKey.isNotEmpty()) {
                answer = callGemini(prompt, geminiKey)
            }

            if (answer.isEmpty()) {
                answer = when {
                    groqKey.isEmpty() && geminiKey.isEmpty() -> 
                        "Ankit bhai, KEYS button par click karke Groq ya Gemini API key enter kar dijiye."
                    prompt.contains("naam") -> 
                        "Mera naam Orion hai bhai. Main aapka personal sentinel AI assistant hoon."
                    prompt.contains("kaise ho") -> 
                        "Main bilkul badhiya hoon Ankit bhai! Aap bataiye aaj phone par kya task execute karna hai?"
                    else -> 
                        "Bilkul Ankit bhai, main samajh gaya. Bataiye aage kya control execute karna hai?"
                }
            }

            mainHandler.post {
                onStatus("REPLYING...")
                onMessage(answer, false)
                speak(answer)
            }
        }.start()
    }

    private fun callGroq(prompt: String, key: String): String {
        return try {
            val url = URL("https://api.groq.com/openai/v1/chat/completions")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $key")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("User-Agent", "ORION/1.0")
                connectTimeout = 10000
                readTimeout = 12000
                doOutput = true
                doInput = true
            }

            val body = JSONObject().apply {
                put("model", "llama-3.3-70b-versatile")
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", "Tumhara naam ORION hai. Tum Ankit ke loyal aur sharp Android AI assistant ho. Seedha natural Hindi/Hinglish me answer do. Har bar ek hi baat repeat mat karo. Bilkul dynamic aur sensible jawab do.")
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
                put("temperature", 0.7)
                put("max_tokens", 300)
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(body.toString())
                it.flush()
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val res = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(res).getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
            } else ""
        } catch (_: Exception) { "" }
    }

    private fun callGemini(prompt: String, key: String): String {
        return try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$key")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connectTimeout = 10000
                readTimeout = 12000
                doOutput = true
                doInput = true
            }

            val body = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", "You are Orion, assistant to Ankit. Reply naturally in Hindi: $prompt") })
                        })
                    })
                })
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(body.toString())
                it.flush()
            }

            if (conn.responseCode == 200) {
                val res = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(res).getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .trim()
            } else ""
        } catch (_: Exception) { "" }
    }

    fun speak(text: String) {
        val cleanText = text.replace(Regex("[*#_`~]"), "").trim()
        val prefs = context.getSharedPreferences("orion_config", Context.MODE_PRIVATE)
        val elevenKey = prefs.getString("elevenlabs_key", "")?.trim() ?: ""
        val voiceId = prefs.getString("elevenlabs_voice_id", "pNInz6obpgDQGcFmaJgB")?.trim() ?: "pNInz6obpgDQGcFmaJgB"

        isSpeakingNow = true
        speechRecognizer?.stopListening()

        if (elevenKey.isNotEmpty()) {
            Thread {
                val success = streamElevenLabsVoice(cleanText, elevenKey, voiceId)
                if (!success) {
                    mainHandler.post { speakOfflineTts(cleanText) }
                }
            }.start()
        } else {
            speakOfflineTts(cleanText)
        }
    }

    private fun streamElevenLabsVoice(text: String, apiKey: String, voiceId: String): Boolean {
        return try {
            val url = URL("https://api.elevenlabs.io/v1/text-to-speech/$voiceId")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("xi-api-key", apiKey)
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "audio/mpeg")
                connectTimeout = 12000
                readTimeout = 15000
                doOutput = true
            }

            val json = JSONObject().apply {
                put("text", text)
                put("model_id", "eleven_multilingual_v2")
                put("voice_settings", JSONObject().apply {
                    put("stability", 0.40)
                    put("similarity_boost", 0.85)
                    put("style", 0.30)
                    put("use_speaker_boost", true)
                })
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(json.toString())
                it.flush()
            }

            if (conn.responseCode == 200) {
                val tempMp3 = File.createTempFile("orion_voice", ".mp3", context.cacheDir)
                FileOutputStream(tempMp3).use { fos ->
                    conn.inputStream.copyTo(fos)
                }

                mainHandler.post {
                    try {
                        mediaPlayer?.release()
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(tempMp3.absolutePath)
                            prepare()
                            start()
                            setOnCompletionListener {
                                tempMp3.delete()
                                isSpeakingNow = false
                                onStatus("STANDBY")
                                if (isContinuousMode) {
                                    mainHandler.postDelayed({ startListening() }, 500)
                                }
                            }
                        }
                    } catch (_: Exception) {
                        isSpeakingNow = false
                        speakOfflineTts(text)
                    }
                }
                true
            } else {
                isSpeakingNow = false
                false
            }
        } catch (e: Exception) {
            isSpeakingNow = false
            false
        }
    }

    private fun speakOfflineTts(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "Orion_${System.currentTimeMillis()}")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("hi", "IN")
            tts?.setPitch(0.70f)
            tts?.setSpeechRate(0.95f)
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
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        mediaPlayer?.release()
    }
}
