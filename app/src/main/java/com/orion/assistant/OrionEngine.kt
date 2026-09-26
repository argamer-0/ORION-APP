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
    private var mediaPlayer: MediaPlayer? = null
    var isContinuousMode = false
    private var isSpeakingNow = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val idleCheckRunnable = object : Runnable {
        override fun run() {
            if (isContinuousMode && !isSpeakingNow) {
                val funnyLines = arrayOf(
                    "Kya hua Ankit boss? Gussa ho kya, kuch bol kyun nahi rahe?",
                    "Arey bolo na boss! Itna sannata kyun hai, main bore ho rahi hoon!",
                    "Boss, phone me doob gaye ya mujhe bhool gaye? Kuch toh bolo!",
                    "Sun rahe ho na Ankit? Khamosh rehne se kaam nahi chalega, hukum karo!"
                )
                val randomPick = funnyLines[Random.nextInt(funnyLines.size)]
                replyImmediate(randomPick)
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
                if (isContinuousMode) mainHandler.postDelayed({ startListening() }, 800)
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
                mediaPlayer?.stop()
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

        // 1. YouTube & Music (Pure Hindi + English keywords)
        if (lower.contains("youtube") || prompt.contains("यूट्यूब") || prompt.contains("युटुब") || prompt.contains("गाना") || lower.contains("song")) {
            val q = prompt.replace(Regex("(?i)youtube|यूट्यूब|युटुब|open|kholo|chalao|bajao|laga do|song|gana|play"), "").trim()
            val finalQuery = if (q.isEmpty()) "trending bollywood songs" else q
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + URLEncoder.encode(finalQuery, "UTF-8"))).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                replyImmediate("YouTube par $finalQuery play kar rahi hoon Ankit, maze karo!")
            } catch (_: Exception) {
                launchApp("com.google.android.youtube", "YouTube open kar diya hai boss!")
            }
            return
        }

        // 2. WhatsApp Auto Send / Open
        if (lower.contains("whatsapp") || prompt.contains("व्हाट्सएप") || prompt.contains("वाट्सएप")) {
            if (lower.contains("message") || prompt.contains("भेजो") || lower.contains("bhejo") || lower.contains("send")) {
                OrionAutomationService.shouldAutoSendWhatsApp = true
                val sendIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://api.whatsapp.com/send?text=" + URLEncoder.encode(prompt, "UTF-8"))
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(sendIntent)
                replyImmediate("WhatsApp message bhej rahi hoon Ankit!")
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
                replyImmediate("Camera open kar diya hai, mast photo aani chahiye!")
            } catch (_: Exception) {
                replyImmediate("Camera open nahi ho raha hai boss.")
            }
            return
        }

        // 4. Free Fire MAX & Games
        if (lower.contains("free fire") || prompt.contains("फ्री फायर") || lower.contains("ff")) {
            val ff = launchApp("com.dts.freefiremax", "Free Fire MAX shuru! Aaj sabko pel dena Ankit!")
            if (!ff) launchApp("com.dts.freefireth", "Free Fire launch ho raha hai boss!")
            return
        }

        // 5. Chrome / Downloads / Web Tasks
        if (lower.contains("chrome") || prompt.contains("क्रोम") || lower.contains("website") || lower.contains("download") || prompt.contains("डाउनलोड")) {
            val query = prompt.replace(Regex("(?i)chrome|open|kholo|browser|search|pe jao"), "").trim()
            val target = if (query.startsWith("http")) query else "https://www.google.com/search?q=" + URLEncoder.encode(query, "UTF-8")
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
                replyImmediate("Chrome par search khol diya hai Ankit!")
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

        // 7. Dynamic App Launch via Package Scanner
        if (lower.contains("open") || prompt.contains("खोल") || prompt.contains("ओपन")) {
            val name = prompt.replace(Regex("(?i)open|kholo|ओपन|करो|khol|chalao|app"), "").trim()
            if (name.isNotEmpty() && launchAnyApp(name)) return
        }

        // 8. General Gemini AI Talk (Romance, Comedy, Loyalty)
        onStatus("THINKING...")
        queryGemini(prompt)
    }

    private fun launchAnyApp(target: String): Boolean {
        try {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(0)
            for (app in apps) {
                val label = pm.getApplicationLabel(app).toString().lowercase(Locale.ROOT)
                if (label.contains(target.lowercase(Locale.ROOT))) {
                    val intent = pm.getLaunchIntentForPackage(app.packageName)?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
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
                replyImmediate("Ye app phone me nahi mila, Play Store par khol diya hai.")
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
            replyImmediate(if (on) "Torch on kar di hai maine Ankit!" else "Torch band kar di hai.")
        } catch (_: Exception) {
            replyImmediate("Flashlight on nahi hui boss.")
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
                    "Ankit boss, KEYS button dabakar apni Gemini API key save kar lo na, tabhi to maza aayega baat karne me!"
                } else {
                    "Arey Ankit! Main hamesha tumhare sath hoon, batao kya masti karni hai?"
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
            }

            val systemInstruction = "Aapka naam ORION hai. Aap Ankit ki behad pyaari, smart, caring aur mazakiya female AI friend/companion ho. Ekdum natural, filmy, chulbuli aur sweet Hindi/Hinglish me baat karo. Bilkul robot ya kitabi bhasha mat bolo. Totle shabd mat use karo. Ankit ko 'Ankit' ya 'boss' bulakar dosti, mazaak aur pyaar se jawab do. Har sawal ka crisp aur dynamic answer do."

            val json = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", "$systemInstruction\nUser ne bola: $prompt") })
                        })
                    })
                })
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(json.toString()); it.flush() }

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
        val voiceId = prefs.getString("elevenlabs_voice_id", "EXAVITQu4vr4xnSDxMaL")?.trim() ?: "EXAVITQu4vr4xnSDxMaL"

        isSpeakingNow = true
        speechRecognizer?.stopListening()

        if (elevenKey.isNotEmpty()) {
            Thread {
                val success = streamElevenLabs(cleanText, elevenKey, voiceId)
                if (!success) mainHandler.post { speakDefaultTts(cleanText) }
            }.start()
        } else {
            speakDefaultTts(cleanText)
        }
    }

    private fun streamElevenLabs(text: String, apiKey: String, voiceId: String): Boolean {
        return try {
            val url = URL("https://api.elevenlabs.io/v1/text-to-speech/$voiceId")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("xi-api-key", apiKey)
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "audio/mpeg")
                connectTimeout = 10000
                readTimeout = 14000
                doOutput = true
            }

            val json = JSONObject().apply {
                put("text", text)
                put("model_id", "eleven_multilingual_v2")
                put("voice_settings", JSONObject().apply {
                    put("stability", 0.38)
                    put("similarity_boost", 0.90)
                    put("style", 0.40)
                    put("use_speaker_boost", true)
                })
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(json.toString()); it.flush() }

            if (conn.responseCode == 200) {
                val tempMp3 = File.createTempFile("orion_female", ".mp3", context.cacheDir)
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
                        speakDefaultTts(text)
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

    private fun speakDefaultTts(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "Orion_${System.currentTimeMillis()}")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("hi", "IN")
            tts?.setPitch(1.3f)
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
        mainHandler.removeCallbacks(idleCheckRunnable)
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        mediaPlayer?.release()
    }
}
