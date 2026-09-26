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
                val funny = arrayOf(
                    "Kya hua Ankit boss? Itna sannata kyun hai, gussa ho kya mujhse?",
                    "Arey bolo na Ankit boss! Main bore ho rahi hoon, kuch toh hukum karo!",
                    "Sun rahe ho na Ankit boss? Aise chup baithoge toh main gana gane lagungi!"
                )
                replyImmediate(funny[Random.nextInt(funny.size)])
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
        mainHandler.postDelayed(idleCheckRunnable, 45000)
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

        // Volume Full Command
        if (lower.contains("volume") || prompt.contains("आवाज") || prompt.contains("साउंड")) {
            if (lower.contains("full") || lower.contains("badhao") || prompt.contains("बढ़ाओ") || prompt.contains("फुल")) {
                setFullVolume()
            }
        }

        // 1. YouTube Song & Video Direct Auto-Play
        if (lower.contains("youtube") || prompt.contains("यूट्यूब") || prompt.contains("युटुब") || prompt.contains("गाना") || lower.contains("song") || prompt.contains("play")) {
            val q = prompt.replace(Regex("(?i)youtube|यूट्यूब|युटुब|open|kholo|chalao|bajao|laga do|song|gana|play|volume full|ful volume|kar do"), "").trim()
            val songName = if (q.isEmpty()) "Pawan Singh new trending song" else q
            if (lower.contains("volume") || lower.contains("फुल")) setFullVolume()
            playSongOnYouTube(songName)
            return
        }

        // 2. WhatsApp Auto-Send / Open
        if (lower.contains("whatsapp") || prompt.contains("व्हाट्सएप") || prompt.contains("वाट्सएप")) {
            if (lower.contains("message") || prompt.contains("भेजो") || lower.contains("bhejo") || lower.contains("send")) {
                OrionAutomationService.shouldAutoSendWhatsApp = true
                val sendIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://api.whatsapp.com/send?text=" + URLEncoder.encode(prompt, "UTF-8"))
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(sendIntent)
                replyImmediate("WhatsApp khol diya hai Ankit boss! Lock khulte hi message send ho jayega.")
            } else {
                launchApp("com.whatsapp", "WhatsApp open kar diya hai Ankit boss.")
            }
            return
        }

        // 3. Camera
        if (lower.contains("camera") || prompt.contains("कैमरा") || prompt.contains("फोटो")) {
            try {
                val intent = Intent("android.media.action.IMAGE_CAPTURE").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
                replyImmediate("Camera open kar diya hai Ankit boss, mast photo lijiye!")
            } catch (_: Exception) {
                replyImmediate("Camera open nahi ho paaya boss.")
            }
            return
        }

        // 4. Free Fire MAX
        if (lower.contains("free fire") || prompt.contains("फ्री फायर") || lower.contains("ff")) {
            val ff = launchApp("com.dts.freefiremax", "Free Fire MAX shuru kar rahi hoon Ankit boss! Aaj sabko hara dena!")
            if (!ff) launchApp("com.dts.freefireth", "Free Fire start ho raha hai boss!")
            return
        }

        // 5. Chrome / Downloads / Search
        if (lower.contains("chrome") || prompt.contains("क्रोम") || lower.contains("website") || lower.contains("download") || prompt.contains("डाउनलोड")) {
            val query = prompt.replace(Regex("(?i)chrome|open|kholo|browser|search|pe jao"), "").trim()
            val target = if (query.startsWith("http")) query else "https://www.google.com/search?q=" + URLEncoder.encode(query, "UTF-8")
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
                replyImmediate("Google par search khol diya hai Ankit boss!")
            } catch (_: Exception) {
                launchApp("com.android.chrome", "Chrome open kar diya hai boss.")
            }
            return
        }

        // 6. Flashlight
        if (lower.contains("torch") || lower.contains("flashlight") || prompt.contains("टॉर्च")) {
            val on = !lower.contains("off") && !lower.contains("band") && !prompt.contains("बंद")
            toggleFlashlight(on)
            return
        }

        // 7. Dynamic Gemini Query
        onStatus("THINKING...")
        queryGemini(prompt)
    }

    private fun setFullVolume() {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), AudioManager.FLAG_SHOW_UI)
        } catch (_: Exception) {}
    }

    private fun playSongOnYouTube(query: String) {
        try {
            val mediaIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                putExtra(SearchManager.QUERY, query)
                setPackage("com.google.android.youtube")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(mediaIntent)
            replyImmediate("YouTube par $query full volume me play kar rahi hoon Ankit boss!")
        } catch (_: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8"))).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            replyImmediate("YouTube par $query play kar rahi hoon Ankit boss!")
        }
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
                replyImmediate("Ye app phone me nahi mila, Play Store par dhoondh diya hai boss.")
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
            replyImmediate(if (on) "Torch on kar di hai maine Ankit boss!" else "Torch band kar di hai Ankit boss.")
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

            if (geminiKey.isEmpty()) {
                val noKey = "Ankit boss, KEYS button dabakar apni Gemini API key save kar lijiye na!"
                mainHandler.post {
                    onStatus("STANDBY")
                    onMessage(noKey, false)
                    speak(noKey)
                }
                return@Thread
            }

            val answer = fetchGeminiResponse(prompt, geminiKey)

            mainHandler.post {
                onStatus("REPLYING...")
                onMessage(answer, false)
                speak(answer)
            }
        }.start()
    }

    private fun fetchGeminiResponse(prompt: String, key: String): String {
        val endpoints = arrayOf(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$key",
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent?key=$key",
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=$key"
        )

        val systemPrompt = "Aapka naam ORION hai. Aap Ankit Boss ki sabse pyaari, intelligent, friendly aur chulbuli Hindi female AI dost ho. Hamesha Ankit ko 'Ankit boss' bolkar bulao. Ekdum natural, filmy, romantic-friendly aur mazaakiya ladki ki tarah Hindi me baat karo. Har sawal ka crisp, witty aur fresh jawab do bina purani baat repeat kiye."

        val json = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "$systemPrompt\nAnkit Boss ne pucha: $prompt")
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
                    connectTimeout = 10000
                    readTimeout = 12000
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
        return "Haan Ankit boss! Main hamesha aapke sath hoon, bataiye aur kya mast task karna hai?"
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
            tts?.setPitch(1.28f)
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
