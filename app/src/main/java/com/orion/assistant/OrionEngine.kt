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
    private val onStatus: (String) -> Unit
) : RecognitionListener, TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    init {
        tts = TextToSpeech(context, this)
        Handler(Looper.getMainLooper()).post {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(this@OrionEngine)
                }
            } else {
                onStatus("Speech Recognition Not Available")
            }
        }
    }

    fun startListening() {
        Handler(Looper.getMainLooper()).post {
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Orion is listening...")
                }
                speechRecognizer?.startListening(intent)
                onStatus("Listening...")
            } catch (e: Exception) {
                onStatus("Mic Error: ${e.message}")
            }
        }
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull() ?: ""
        if (text.isNotEmpty()) {
            onStatus("You: $text")
            queryGroq(text)
        } else {
            onStatus("Nothing recognized")
        }
    }

    private fun queryGroq(userQuery: String) {
        Thread {
            try {
                val prefs = context.getSharedPreferences("orion_config", Context.MODE_PRIVATE)
                val apiKey = prefs.getString("groq_key", "") ?: ""

                if (apiKey.isEmpty()) {
                    speak("Groq API key set nahi hai boss. Screen par diye button se key enter karein.")
                    return@Thread
                }

                val payload = JSONObject().apply {
                    put("model", "llama-3.3-70b-versatile")
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", "You are ORION, an ultra-intelligent cybernetic AI assistant created for Ankit. Reply concisely in natural Hindi/Hinglish.")
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", userQuery)
                        })
                    })
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = payload.toString().toRequestBody(mediaType)
                val req = Request.Builder()
                    .url("https://api.groq.com/openai/v1/chat/completions")
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build()

                val resp = client.newCall(req).execute()
                val resBody = resp.body?.string() ?: ""

                if (resp.isSuccessful) {
                    val root = JSONObject(resBody)
                    val choices = root.getJSONArray("choices")
                    val answer = choices.getJSONObject(0).getJSONObject("message").getString("content")
                    speak(answer)
                } else {
                    speak("Groq API error. Please check your key.")
                }
            } catch (e: Exception) {
                speak("Connection error: ${e.message}")
            }
        }.start()
    }

    fun speak(text: String) {
        onStatus("Orion: $text")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "OrionTTS")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("hi", "IN")
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() { onStatus("Hearing you...") }
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() { onStatus("Processing...") }
    override fun onError(error: Int) { onStatus("Speech Error: $error") }
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    fun destroy() {
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
    }
}
