package com.orion.assistant

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.MediaStore
import java.net.URLEncoder
import java.util.Locale

class ActionExecutor(private val context: Context) {

    fun openApp(appName: String): Pair<Boolean, String> {
        val pm = context.packageManager
        val cleanName = appName.lowercase(Locale.ROOT).trim()

        // 1. Direct package search across installed packages
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        var targetPkg: String? = null
        var foundLabel: String? = null

        for (app in installedApps) {
            val label = pm.getApplicationLabel(app).toString().lowercase(Locale.ROOT)
            if (label == cleanName || label.contains(cleanName) || cleanName.contains(label)) {
                targetPkg = app.packageName
                foundLabel = pm.getApplicationLabel(app).toString()
                break
            }
        }

        // Aliases fallback
        if (targetPkg == null) {
            targetPkg = when {
                cleanName.contains("free fire max") || cleanName.contains("ff max") -> "com.dts.freefiremax"
                cleanName.contains("free fire") || cleanName.contains("ff") -> "com.dts.freefireth"
                cleanName.contains("youtube") -> "com.google.android.youtube"
                cleanName.contains("whatsapp") -> "com.whatsapp"
                cleanName.contains("chrome") -> "com.android.chrome"
                cleanName.contains("camera") -> "com.android.camera"
                cleanName.contains("gallery") || cleanName.contains("photos") -> "com.google.android.apps.photos"
                else -> null
            }
        }

        if (targetPkg != null) {
            val intent = pm.getLaunchIntentForPackage(targetPkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent != null) {
                context.startActivity(intent)
                return Pair(true, "Successfully opened ${foundLabel ?: appName}")
            }
        }
        return Pair(false, "App '$appName' is not installed on this device.")
    }

    fun playYouTube(query: String): Pair<Boolean, String> {
        return try {
            val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                putExtra(SearchManager.QUERY, query)
                setPackage("com.google.android.youtube")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Pair(true, "Playing '$query' on YouTube")
        } catch (_: Exception) {
            try {
                val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8"))).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(web)
                Pair(true, "Searching and playing '$query' on YouTube")
            } catch (e: Exception) {
                Pair(false, "Failed to launch YouTube: ${e.message}")
            }
        }
    }

    fun openWhatsApp(contact: String?, message: String?): Pair<Boolean, String> {
        return try {
            val url = if (!message.isNullOrEmpty()) {
                "https://api.whatsapp.com/send?text=" + URLEncoder.encode(message, "UTF-8")
            } else {
                "https://api.whatsapp.com/send"
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Pair(true, "Opened WhatsApp for $contact")
        } catch (e: Exception) {
            Pair(false, "WhatsApp is not available: ${e.message}")
        }
    }

    fun openCamera(): Pair<Boolean, String> {
        return try {
            val intent = Intent("android.media.action.IMAGE_CAPTURE").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Pair(true, "Camera opened")
        } catch (e: Exception) {
            Pair(false, "Camera launch failed: ${e.message}")
        }
    }

    fun setTorch(enable: Boolean): Pair<Boolean, String> {
        return try {
            val cam = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cam.setTorchMode(cam.cameraIdList[0], enable)
            Pair(true, if (enable) "Torch turned ON" else "Torch turned OFF")
        } catch (e: Exception) {
            Pair(false, "Torch control error: ${e.message}")
        }
    }

    fun setVolume(percent: Int): Pair<Boolean, String> {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val target = ((percent.coerceIn(0, 100) / 100.0) * max).toInt()
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
            Pair(true, "Media volume set to $percent%")
        } catch (e: Exception) {
            Pair(false, "Failed to change volume: ${e.message}")
        }
    }
}
