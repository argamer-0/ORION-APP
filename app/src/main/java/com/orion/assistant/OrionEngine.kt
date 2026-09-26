package com.orion.assistant

import android.content.Context
import android.content.Intent
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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
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
    private val actionExecutor = ActionExecutor(context)

    init {
        tts = TextToSpeech(context, this)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { isSpeakingNow = true }
            override fun onDone(id: String?) {
                isSpeakingNow = false
                if (isContinuousMode) mainHandler.postDelayed({ startListening() }, 500)
            }
            override fun onError(id: String?) {
                isSpeakingNow = false
                if (isContinuousMode) mainHandler.postDelayed({ startListening() }, 700)
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
                onStatus("STANDBY")
            } catch (_: Exception) {}
        }
    }

    override fun onResults(results: Bundle?) {
        if (isSpeakingNow) return
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull()?.trim() ?: ""
        if (text.isNotEmpty()) {
            onMessage(text, true)
            onStatus("THINKING...")
            processUserQuery(text)
        } else if (isContinuousMode) {
            mainHandler.postDelayed({ startListening() }, 400)
        }
    }

    private fun processUserQuery(prompt: String) {
        Thread {
            val prefs = context.getSharedPreferences("orion_config", Context.MODE_PRIVATE)
            val key = prefs.getString("gemini_key", "")?.trim() ?: ""

            if (key.isEmpty()) {
                val msg = "Boss, upar KEYS par click karke Gemini API key daal dijiye tabhi main kaam kar paungi!"
                mainHandler.post {
                    onStatus("STANDBY")
                    onMessage(msg, false)
                    speak(msg)
                }
                return@Thread
            }

            // Execute Gemini with Function Calling Tool Registry
            val aiOutcome = executeGeminiTurn(prompt, key)

            mainHandler.post {
                onStatus("REPLYING...")
                onMessage(aiOutcome, false)
                speak(aiOutcome)
            }
        }.start()
    }

    private fun executeGeminiTurn(prompt: String, key: String): String {
        // High-quota model: gemini-2.0-flash with fallback to gemini-2.5-flash
        val models = arrayOf("gemini-2.0-flash", "gemini-2.5-flash")
        
        for (model in models) {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
            val systemInstruction = "Aapka naam ORION hai. Aap Ankit Boss ke personal AI assistant aur dost ho. Friendly, natural aur mazaakiya Hindi/Hinglish me baat karo. Jab Ankit kisi phone action ke liye bole (jaise YouTube par gaana chalana, Chrome kholna, Free Fire kholna, camera, torch ya volume), toh text me gappe marne ke badle STRICTLY tool call function generate karo. KABHI BHI bina action execute hue jhootha mat bolna ki chala diya."

            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", "$systemInstruction\nUser: $prompt") })
                        })
                    })
                })
                put("tools", ToolRegistry.getToolsJson())
            }

            try {
                val url = URL(endpoint)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("x-goog-api-key", key)
                    connectTimeout = 12000
                    readTimeout = 15000
                    doOutput = true
                    doInput = true
                }

                OutputStreamWriter(conn.outputStream, "UTF-8").use {
                    it.write(requestJson.toString())
                    it.flush()
                }

                val code = conn.responseCode
                if (code == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                    val res = reader.readText()
                    reader.close()

                    val root = JSONObject(res)
                    val candidate = root.getJSONArray("candidates").getJSONObject(0)
                    val contentParts = candidate.getJSONObject("content").getJSONArray("parts")
                    
                    var spokenResponse = ""
                    var functionCallObj: JSONObject? = null

                    for (i in 0 until contentParts.length()) {
                        val part = contentParts.getJSONObject(i)
                        if (part.has("text")) {
                            spokenResponse += part.getString("text") + " "
                        }
                        if (part.has("functionCall")) {
                            functionCallObj = part.getJSONObject("functionCall")
                        }
                    }

                    // REAL ACTION EXECUTION
                    if (functionCallObj != null) {
                        val fnName = functionCallObj.getString("name")
                        val args = functionCallObj.optJSONObject("args") ?: JSONObject()
                        val actionResult = dispatchRealAction(fnName, args)

                        return if (actionResult.first) {
                            if (spokenResponse.isNotBlank()) spokenResponse.trim() else "Boss, ${actionResult.second}!"
                        } else {
                            "Boss, action fail ho gaya: ${actionResult.second}"
                        }
                    }

                    if (spokenResponse.isNotBlank()) {
                        return spokenResponse.trim()
                    }
                } else if (code == 429) {
                    // Safe Quota Error Handling - No fake retry loop
                    return "Boss, Gemini API ki free tier limit abhi puri ho gayi hai (HTTP 429). Thodi der baad try karte hain!"
                } else {
                    val errStream = conn.errorStream ?: conn.inputStream
                    val errText = errStream?.bufferedReader()?.use { it.readText() } ?: ""
                    return "[HTTP $code on $model]: $errText"
                }
            } catch (e: Exception) {
                // Try next candidate model
            }
        }
        return "Boss, internet connection me dikkat aa rahi hai, check kijiye na!"
    }

    private fun dispatchRealAction(name: String, args: JSONObject): Pair<Boolean, String> {
        return when (name) {
            "open_app" -> {
                val app = args.optString("app_name", "")
                actionExecutor.openApp(app)
            }
            "play_youtube" -> {
                val q = args.optString("query", "latest songs")
                actionExecutor.playYouTube(q)
            }
            "open_whatsapp" -> {
                val c = args.optString("contact", "")
                val m = args.optString("message", "")
                actionExecutor.openWhatsApp(c, m)
            }
            "open_camera" -> {
                actionExecutor.openCamera()
            }
            "toggle_torch" -> {
                val en = args.optBoolean("enable", true)
                actionExecutor.setTorch(en)
            }
            "set_volume" -> {
                val p = args.optInt("percent", 80)
                actionExecutor.setVolume(p)
            }
            else -> Pair(false, "Unknown action $name")
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
            tts?.setPitch(1.22f)
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
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
    }
}
