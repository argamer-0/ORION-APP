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
            queryGemini(text)
        } else if (isContinuousMode) {
            mainHandler.postDelayed({ startListening() }, 400)
        }
    }

    private fun queryGemini(prompt: String) {
        Thread {
            val prefs = context.getSharedPreferences("orion_config", Context.MODE_PRIVATE)
            val key = prefs.getString("gemini_key", "")?.trim() ?: ""

            if (key.isEmpty()) {
                val msg = "Ankit boss, KEYS par click karke Gemini key save karein!"
                mainHandler.post {
                    onStatus("STANDBY")
                    onMessage(msg, false)
                    speak(msg)
                }
                return@Thread
            }

            val raw = callGeminiAPI(prompt, key)

            mainHandler.post {
                val clean = raw.replace(Regex("\\[action:[^\\]]+\\]"), "").trim()
                onStatus("REPLYING...")
                onMessage(clean, false)
                speak(clean)
                executeAction(raw)
            }
        }.start()
    }

    private fun callGeminiAPI(prompt: String, key: String): String {
        // Safe Model Endpoints
        val models = arrayOf("gemini-2.0-flash", "gemini-1.5-flash", "gemini-pro")
        val sys = "Aapka naam ORION hai. Aap Ankit Boss ki behad pyaari, intelligent aur chulbuli Hindi female friend ho. Hamesha unhe 'Ankit boss' kaho. Real ladki ki tarah natural Hindi me baat karo. Har sawal ka naya aur dynamic jawab do, koi line repeat mat karna. Agar gana chalane ko bole toh text me '[action:play_youtube:song_name]' lagao. WhatsApp ke liye '[action:open_whatsapp]', Camera ke liye '[action:open_camera]', Torch ke liye '[action:torch_on]', Volume full ke liye '[action:volume_full]' lagao."

        val json = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", "$sys\nAnkit Boss: $prompt") })
                    })
                })
            })
        }

        for (m in models) {
            try {
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent?key=$key")
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
        return "Ankit boss, internet slow lag raha hai ya Google connect nahi ho pa raha hai!"
    }

    private fun executeAction(text: String) {
        val lower = text.lowercase(Locale.ROOT)
        if (lower.contains("[action:play_youtube]")) {
            val q = extractParam(text, "play_youtube")
            try {
                val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                    putExtra(SearchManager.QUERY, q)
                    setPackage("com.google.android.youtube")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + URLEncoder.encode(q, "UTF-8"))).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(web)
            }
        } else if (lower.contains("[action:open_whatsapp]")) {
            launchApp("com.whatsapp")
        } else if (lower.contains("[action:open_camera]")) {
            val intent = Intent("android.media.action.IMAGE_CAPTURE").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
        } else if (lower.contains("[action:torch_on]")) {
            setTorch(true)
        } else if (lower.contains("[action:torch_off]")) {
            setTorch(false)
        } else if (lower.contains("[action:volume_full]")) {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), AudioManager.FLAG_SHOW_UI)
        }
    }

    private fun extractParam(text: String, tag: String): String {
        return try {
            val s = text.indexOf("[$tag:") + tag.length + 2
            val e = text.indexOf("]", s)
            if (s != -1 && e != -1) text.substring(s, e) else "trending song"
        } catch (_: Exception) { "trending song" }
    }

    private fun launchApp(pkg: String) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            if (intent != null) context.startActivity(intent)
        } catch (_: Exception) {}
    }

    private fun setTorch(on: Boolean) {
        try {
            val cam = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cam.setTorchMode(cam.cameraIdList[0], on)
        } catch (_: Exception) {}
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
            tts?.setPitch(1.26f)
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
