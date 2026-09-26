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
import android.util.Log
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
                onStatus("STT Error: ${e.message}")
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

            // 1. Try Groq (Llama-3.3)
            if (groqKey.isNotEmpty()) {
                answer = callGroq(prompt, groqKey)
            }

            // 2. Try Gemini Fallback agar Groq fail ho
            if (answer.isEmpty() && geminiKey.isNotEmpty()) {
                answer = callGemini(prompt, geminiKey)
            }

            // 3. Agar fir bhi empty rahe
            if (answer.isEmpty()) {
                answer = if (groqKey.isEmpty() && geminiKey.isEmpty()) {
                    "Ankit boss, KEYS button par click karke Groq ya Gemini API key daal dijiye."
                } else {
                    "Mera naam Orion hai boss. Main aapka personal AI sentinel hoon. Bataiye kya madad karun?"
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
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Mozilla/5.0")
                connectTimeout = 12000
                readTimeout = 12000
                doOutput = true
                doInput = true
            }

            val body = JSONObject().apply {
                put("model", "llama-3.3-70b-versatile")
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", "Aapka naam ORION hai. Aap Ankit ke sabse powerful aur loyal male AI assistant ho. Har sawal ka jawab Hindi ya Hinglish me smart, respectful aur direct do. Faltoo lamba bhashan mat do.")
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
                put("temperature", 0.7)
                put("max_tokens", 400)
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(body.toString())
                it.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val rootJson = JSONObject(responseText)
                rootJson.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
            } else {
                Log.e("ORION_GROQ", "Error code: $responseCode")
                ""
            }
        } catch (e: Exception) {
            Log.e("ORION_GROQ", "Exception: ${e.message}")
            ""
        }
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
                doInput = true
            }

            val body = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(body.toString())
                it.flush()
            }

            if (conn.responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(responseText)
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .trim()
            } else ""
        } catch (e: Exception) {
            Log.e("ORION_GEMINI", "Exception: ${e.message}")
            ""
        }
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
                    put("stability", 0.5)
                    put("similarity_boost", 0.85)
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
                                onStatus("STANDBY")
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
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun speakOfflineTts(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "Orion_${System.currentTimeMillis()}")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("hi", "IN")
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
