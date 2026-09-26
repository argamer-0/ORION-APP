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
import android.provider.ContactsContract
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
import java.net.URLEncoder
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
            override fun onStart(utteranceId: String?) { isSpeakingNow = true }
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

        // 1. YouTube Song & Video Search / Play
        if (lower.contains("youtube") || lower.contains("यूट्यूब") || lower.contains("गाना") || lower.contains("song")) {
            val query = prompt.replace(Regex("(?i)youtube|यूट्यूब|open|chalao|bajao|laga do|song|gana|kholo|play"), "").trim()
            val searchQuery = if (query.isEmpty()) "latest trending songs" else query
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + URLEncoder.encode(searchQuery, "UTF-8"))).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                replyImmediate("YouTube par $searchQuery chala rahi hoon Ankit.")
            } catch (_: Exception) {
                launchAppByIntent("com.google.android.youtube", "YouTube open kar diya hai Ankit.")
            }
            return
        }

        // 2. WhatsApp Auto Search & Auto Send
        if (lower.contains("whatsapp") || lower.contains("व्हाट्सएप")) {
            if (lower.contains("message") || lower.contains("भेजो") || lower.contains("bhejo") || lower.contains("send")) {
                handleWhatsAppAutoSend(prompt)
            } else {
                launchAppByIntent("com.whatsapp", "WhatsApp open kar diya hai Ankit.")
            }
            return
        }

        // 3. Camera
        if (lower.contains("camera") || lower.contains("कैमरा") || lower.contains("photo khincho")) {
            try {
                val intent = Intent("android.media.action.IMAGE_CAPTURE").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
                replyImmediate("Camera open kar diya hai Ankit, smile karo!")
            } catch (_: Exception) {
                replyImmediate("Camera access nahi ho paya.")
            }
            return
        }

        // 4. Free Fire & Games
        if (lower.contains("free fire") || lower.contains("फ्री फायर")) {
            val ffLaunched = launchAppByIntent("com.dts.freefiremax", "Free Fire MAX start ho raha hai, aaj booyah karna hai!")
            if (!ffLaunched) launchAppByIntent("com.dts.freefireth", "Free Fire start ho raha hai!")
            return
        }

        // 5. Chrome & Web Search / Downloader
        if (lower.contains("chrome") || lower.contains("website") || lower.contains("rotate") || lower.contains("download") || lower.contains("डाउनलोड")) {
            val webQuery = prompt.replace(Regex("(?i)chrome|open|kholo|browser|pe jao"), "").trim()
            val targetUrl = if (webQuery.startsWith("http")) webQuery else "https://www.google.com/search?q=" + URLEncoder.encode(webQuery, "UTF-8")
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
                replyImmediate("Google Chrome par search open kar diya hai.")
            } catch (_: Exception) {
                launchAppByIntent("com.android.chrome", "Chrome open kar diya hai.")
            }
            return
        }

        // 6. Flashlight
        if (lower.contains("torch") || lower.contains("flashlight") || lower.contains("टॉर्च")) {
            val turnOn = !lower.contains("off") && !lower.contains("band")
            toggleFlashlight(turnOn)
            return
        }

        // 7. Dynamic App Launcher for any other app mentioned
        if (lower.contains("open") || lower.contains("kholo") || lower.contains("chalao")) {
            val appWord = prompt.replace(Regex("(?i)open|kholo|chalao|khol|app|karo"), "").trim()
            if (appWord.isNotEmpty() && launchAnyAppByName(appWord)) {
                return
            }
        }

        // 8. General AI Conversation with sweet emotional intelligence
        onStatus("THINKING...")
        queryAI(prompt)
    }

    private fun handleWhatsAppAutoSend(fullPrompt: String) {
        try {
            OrionAutomationService.shouldAutoSendWhatsApp = true
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://api.whatsapp.com/send?text=" + URLEncoder.encode(fullPrompt, "UTF-8"))
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            replyImmediate("WhatsApp message prepare kar diya hai, send ho raha hai.")
        } catch (_: Exception) {
            launchAppByIntent("com.whatsapp", "WhatsApp open kar diya hai.")
        }
    }

    private fun launchAnyAppByName(appName: String): Boolean {
        try {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(0)
            for (app in packages) {
                val label = pm.getApplicationLabel(app).toString().lowercase(Locale.ROOT)
                if (label.contains(appName.lowercase(Locale.ROOT))) {
                    val intent = pm.getLaunchIntentForPackage(app.packageName)?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (intent != null) {
                        context.startActivity(intent)
                        replyImmediate("$label open kar diya hai Ankit!")
                        return true
                    }
                }
            }
        } catch (_: Exception) {}
        return false
    }

    private fun launchAppByIntent(pkg: String, successMsg: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent != null) {
                context.startActivity(intent)
                replyImmediate(successMsg)
                true
            } else {
                // If not found locally, open in Google Play Store
                val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(playIntent)
                replyImmediate("Ye app phone me nahi mila, Play Store par download ke liye khol diya hai.")
                true
            }
        } catch (_: Exception) {
            replyImmediate("App open nahi ho paya Ankit.")
            false
        }
    }

    private fun toggleFlashlight(on: Boolean) {
        try {
            val cam = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cam.setTorchMode(cam.cameraIdList[0], on)
            replyImmediate(if (on) "Torch on kar di hai maine!" else "Torch band kar di hai.")
        } catch (_: Exception) {
            replyImmediate("Torch control nahi ho payi.")
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
            if (groqKey.isNotEmpty()) answer = callGroq(prompt, groqKey)
            if (answer.isEmpty() && geminiKey.isNotEmpty()) answer = callGemini(prompt, geminiKey)

            if (answer.isEmpty()) {
                answer = "Haan Ankit! Main tumhari har baat sun rahi hoon, batao kya mast plan hai aaj ka?"
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
                setRequestProperty("User-Agent", "Mozilla/5.0")
                connectTimeout = 12000
                readTimeout = 12000
                doOutput = true
            }

            val systemInstruction = "Aapka naam ORION hai. Aap Ankit ki sabse pyaari, intelligent aur loyal female AI companion ho. Bilkul natural, sweet, thodi mazaakiya aur caring Hindi me baat karo jaise ek best friend ya close partner baat karti hai. Faltu kitabi ya robot jaisi batein mat karo. Ankit ko naam se bulao aur har sawaal ka crisp, friendly aur smart jawab do."

            val body = JSONObject().apply {
                put("model", "llama-3.3-70b-versatile")
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemInstruction)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
                put("temperature", 0.75)
                put("max_tokens", 300)
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(body.toString()); it.flush() }

            if (conn.responseCode == 200) {
                val res = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(res).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
            } else ""
        } catch (_: Exception) { "" }
    }

    private fun callGemini(prompt: String, key: String): String {
        return try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$key")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connectTimeout = 12000
                readTimeout = 12000
                doOutput = true
            }

            val body = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", "You are Orion, an affectionate smart Hindi female AI friend for Ankit. Reply naturally: $prompt") })
                        })
                    })
                })
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(body.toString()); it.flush() }

            if (conn.responseCode == 200) {
                val res = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(res).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").trim()
            } else ""
        } catch (_: Exception) { "" }
    }

    fun speak(text: String) {
        val cleanText = text.replace(Regex("[*#_`~]"), "").trim()
        val prefs = context.getSharedPreferences("orion_config", Context.MODE_PRIVATE)
        val elevenKey = prefs.getString("elevenlabs_key", "")?.trim() ?: ""
        // Default: EXAVITQu4vr4xnSDxMaL (Bella - Sweet Hindi/Multilingual Natural Female)
        val voiceId = prefs.getString("elevenlabs_voice_id", "EXAVITQu4vr4xnSDxMaL")?.trim() ?: "EXAVITQu4vr4xnSDxMaL"

        isSpeakingNow = true
        speechRecognizer?.stopListening()

        if (elevenKey.isNotEmpty()) {
            Thread {
                val success = streamElevenLabsVoice(cleanText, elevenKey, voiceId)
                if (!success) mainHandler.post { speakOfflineTts(cleanText) }
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
                    put("style", 0.35)
                    put("use_speaker_boost", true)
                })
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(json.toString()); it.flush() }

            if (conn.responseCode == 200) {
                val tempMp3 = File.createTempFile("orion_f_voice", ".mp3", context.cacheDir)
                FileOutputStream(tempMp3).use { fos -> conn.inputStream.copyTo(fos) }

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
                                if (isContinuousMode) mainHandler.postDelayed({ startListening() }, 500)
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
        } catch (_: Exception) {
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
            tts?.setPitch(1.15f)
            tts?.setSpeechRate(1.0f)
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
