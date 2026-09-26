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
                if (isContinuousMode) {
                    mainHandler.postDelayed({ startListening() }, 600)
                }
            }
            override fun onError(utteranceId: String?) {
                isSpeakingNow = false
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
            onStatus("THINKING...")
            queryAI(text)
        } else if (isContinuousMode) {
            mainHandler.postDelayed({ startListening() }, 400)
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
                    "Ankit boss, KEYS button par click karke Groq API key aur ElevenLabs key save kar lijiye."
                } else {
                    "Haan Ankit bhai, bataiye kya kaam karna hai, main sun raha hoon."
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
                readTimeout = 15000
                doOutput = true
                doInput = true
            }

            val systemInstruction = "Aapka naam ORION hai. Aap Ankit ke personal, smart aur loyal assistant ho. Ekdum natural, saaf, confident Hindi ya Hinglish me baat karo jaise ek mature dost ya right-hand man baat karta hai. Kisi robot ya sentinel jaisa faltu formality mat karo. Jo poocha jaye uska seedha, badiya aur emotion ke sath answer do. Totle ya ajeeb shabdon ka use bilkul mat karo."

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
                put("temperature", 0.6)
                put("max_tokens", 350)
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
                connectTimeout = 12000
                readTimeout = 15000
                doOutput = true
                doInput = true
            }

            val body = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", "You are Orion, loyal assistant to Ankit. Answer in clean Hindi naturally: $prompt") })
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
        // Text clean karo taaki ElevenLabs atke nahi
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
