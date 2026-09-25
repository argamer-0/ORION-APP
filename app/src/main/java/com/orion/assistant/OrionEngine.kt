package com.orion.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread
import org.json.JSONArray
import org.json.JSONObject

class OrionEngine(private val context: Context, private val onStatus: (String) -> Unit) : TextToSpeech.OnInitListener {
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = TextToSpeech(context, this)

    init {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(p: Bundle?) { onStatus("LISTENING NOW...") }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(r: Float) {}
                    override fun onBufferReceived(b: ByteArray?) {}
                    override fun onEndOfSpeech() { onStatus("PROCESSING...") }
                    override fun onError(e: Int) { onStatus("TAP ORB TO SPEAK") }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val text = matches[0]
                            onStatus("YOU: " + text)
                            queryAi(text)
                        } else {
                            onStatus("TAP ORB TO SPEAK")
                        }
                    }
                    override fun onPartialResults(p: Bundle?) {}
                    override fun onEvent(t: Int, p: Bundle?) {}
                })
            }
        }
    }

    fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "ORION Listening...")
        }
        recognizer?.startListening(intent)
    }

    fun queryAi(prompt: String) {
        val key = context.getSharedPreferences("orion_config", Context.MODE_PRIVATE).getString("groq_key", "") ?: ""
        if (key.isEmpty()) {
            speak("Boss, Profile tab me jaakar Groq API Key save karein.")
            onStatus("Groq Key Required in Profile")
            return
        }

        thread {
            try {
                val conn = (URL("https://api.groq.com/openai/v1/chat/completions").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer " + key)
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 8000
                    readTimeout = 12000
                }

                val body = JSONObject().apply {
                    put("model", "llama-3.3-70b-versatile")
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", "You are ORION, assistant to Ankit. Answer in short Hinglish.")
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                }

                OutputStreamWriter(conn.outputStream).use { it.write(body.toString()); it.flush() }
                val resp = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val reply = JSONObject(resp).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                onStatus("ORION: " + reply)
                speak(reply)
            } catch (e: Exception) {
                onStatus("Groq Connection Error")
                speak("Network error ya invalid key hai Boss.")
            }
        }
    }

    fun speak(msg: String) {
        tts?.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "orion_tts")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("hi", "IN")
        }
    }

    fun destroy() {
        recognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
    }
}
