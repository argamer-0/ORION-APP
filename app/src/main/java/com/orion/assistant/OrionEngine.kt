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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

class OrionEngine(
    private val context: Context,
    private val onStatus: (String) -> Unit,
    private val onMessage: (String, Boolean) -> Unit
) : RecognitionListener, TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    var isContinuousMode = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    init {
        tts = TextToSpeech(context, this)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (isContinuousMode) {
                    mainHandler.postDelayed({ startListening() }, 500)
                }
            }
            override fun onError(utteranceId: String?) {
                if (isContinuousMode) {
                    mainHandler.postDelayed({ startListening() }, 800)
                }
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
                onStatus("STT Init Error: ${e.message}")
            }
        }
    }

    fun startListening() {
        mainHandler.post {
            try {
                if (speechRecognizer == null) {
                    initRecognizer()
                }
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
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                tts?.stop()
                onStatus("STANDBY")
            } catch (_: Exception) {}
        }
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull()?.trim() ?: ""
        if (text.isNotEmpty()) {
            onMessage(text, true)
            onStatus("THINKING...")
            queryAI(text)
        } else if (isContinuousMode) {
            startListening()
        }
    }

    private fun queryAI(prompt: String) {
        Thread {
            val prefs = context.getSharedPreferences("orion_config", Context.MODE_PRIVATE)
            val groqKey = prefs.getString("groq_key", "") ?: ""
            val geminiKey = prefs.getString("gemini_key", "") ?: ""

            var answer = ""
            if (groqKey.isNotEmpty()) {
                answer = callGroq(prompt, groqKey)
            }
            if (answer.isEmpty() && geminiKey.isNotEmpty()) {
                answer = callGemini(prompt, geminiKey)
            }

            if (answer.isEmpty()) {
                answer = if (groqKey.isEmpty() && geminiKey.isEmpty()) {
                    "Boss, screen par diye KEYS button se Groq ya Gemini API key save karein."
                } else {
                    "Server connect nahi ho pa raha hai, please check internet ya API key."
                }
            }

            mainHandler.post {
                onMessage(answer, false)
                speak(answer)
            }
        }.start()
    }

    private fun callGroq(prompt: String, key: String): String {
        return try {
            val json = JSONObject().apply {
                put("model", "llama-3.3-70b-versatile")
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", "You are ORION, an ultra-advanced AI built for Ankit. Respond concisely and naturally in clear Hindi/Hinglish.")
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
            }
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .header("Authorization", "Bearer $key")
                .post(body)
                .build()
            val res = client.newCall(req).execute()
            val resStr = res.body?.string() ?: ""
            if (res.isSuccessful) {
                JSONObject(resStr).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            } else ""
        } catch (_: Exception) { "" }
    }

    private fun callGemini(prompt: String, key: String): String {
        return try {
            val json = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
            }
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$key")
                .post(body)
                .build()
            val res = client.newCall(req).execute()
            val resStr = res.body?.string() ?: ""
            if (res.isSuccessful) {
                JSONObject(resStr).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
            } else ""
        } catch (_: Exception) { "" }
    }

    fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "Orion_${System.currentTimeMillis()}")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("hi", "IN")
        }
    }

    override fun onError(error: Int) {
        if (isContinuousMode) {
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
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
    }
}
