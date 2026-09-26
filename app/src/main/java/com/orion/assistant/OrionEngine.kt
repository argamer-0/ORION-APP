package com.orion.assistant

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
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
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        tts = TextToSpeech(context, this)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (isContinuousMode) mainHandler.postDelayed({ startListening() }, 500)
            }
            override fun onError(utteranceId: String?) {
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
                onStatus("STT Init Error: ${e.message}")
            }
        }
    }

    fun startListening() {
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
                answer = if (groqKey.isEmpty() && geminiKey.isEmpty()) {
                    "Ankit boss, KEYS button par click karke Groq aur ElevenLabs API key daal dijiye."
                } else {
                    "Internet connection check karein boss, response fetch nahi ho paya."
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
            val url = URL("https://api.groq.com/openai/v1/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $key")
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.doOutput = true

            val json = JSONObject().apply {
                put("model", "llama-3.3-70b-versatile")
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", "You are ORION, a badass loyal male AI assistant created by Ankit. Speak in crisp, direct, respectful Hindi/Hinglish as a loyal brother and sentinel.")
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
            }

            val os = conn.outputStream
            os.write(json.toString().toByteArray(Charsets.UTF_8))
            os.flush()
            os.close()

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                val res = reader.readText()
                reader.close()
                JSONObject(res).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            } else ""
        } catch (_: Exception) { "" }
    }

    private fun callGemini(prompt: String, key: String): String {
        return try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$key")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.doOutput = true

            val json = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
            }

            val os = conn.outputStream
            os.write(json.toString().toByteArray(Charsets.UTF_8))
            os.flush()
            os.close()

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                val res = reader.readText()
                reader.close()
                JSONObject(res).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
            } else ""
        } catch (_: Exception) { "" }
    }

    fun speak(text: String) {
        val prefs = context.getSharedPreferences("orion_config", Context.MODE_PRIVATE)
        val elevenKey = prefs.getString("elevenlabs_key", "")?.trim() ?: ""
        val voiceId = prefs.getString("elevenlabs_voice_id", "pNInz6obpgDQGcFmaJgB")?.trim() ?: "pNInz6obpgDQGcFmaJgB"

        if (elevenKey.isNotEmpty()) {
            Thread {
                val success = streamElevenLabsVoice(text, elevenKey, voiceId)
                if (!success) {
                    mainHandler.post { speakOfflineTts(text) }
                }
            }.start()
        } else {
            speakOfflineTts(text)
        }
    }

    private fun streamElevenLabsVoice(text: String, apiKey: String, voiceId: String): Boolean {
        return try {
            val url = URL("https://api.elevenlabs.io/v1/text-to-speech/$voiceId")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("xi-api-key", apiKey)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "audio/mpeg")
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.doOutput = true

            val json = JSONObject().apply {
                put("text", text)
                put("model_id", "eleven_multilingual_v2")
                put("voice_settings", JSONObject().apply {
                    put("stability", 0.5)
                    put("similarity_boost", 0.85)
                })
            }

            val os = conn.outputStream
            os.write(json.toString().toByteArray(Charsets.UTF_8))
            os.flush()
            os.close()

            if (conn.responseCode == 200) {
                val tempMp3 = File.createTempFile("orion_voice", ".mp3", context.cacheDir)
                val fos = FileOutputStream(tempMp3)
                conn.inputStream.copyTo(fos)
                fos.close()

                mainHandler.post {
                    try {
                        mediaPlayer?.release()
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(tempMp3.absolutePath)
                            prepare()
                            start()
                            setOnCompletionListener {
                                tempMp3.delete()
                                if (isContinuousMode) {
                                    mainHandler.postDelayed({ startListening() }, 500)
                                }
                            }
                        }
                    } catch (_: Exception) {
                        speakOfflineTts(text)
                    }
                }
                true
            } else false
        } catch (_: Exception) {
            false
        }
    }

    private fun speakOfflineTts(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "Orion_${System.currentTimeMillis()}")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("hi", "IN")
            // Deep Robotic Male Pitch
            tts?.setPitch(0.65f)
            tts?.setSpeechRate(0.95f)
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
        mediaPlayer?.release()
    }
}
