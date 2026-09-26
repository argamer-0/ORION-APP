package com.orion.assistant

import org.json.JSONArray
import org.json.JSONObject

object ToolRegistry {

    fun getToolsJson(): JSONArray {
        val toolsArray = JSONArray()
        val funcArray = JSONArray()

        // 1. open_app
        funcArray.put(JSONObject().apply {
            put("name", "open_app")
            put("description", "Opens any installed Android app by its name (e.g. Chrome, Free Fire MAX, WhatsApp, Settings, Camera, Gallery).")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("app_name", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Name of the application to open.")
                    })
                })
                put("required", JSONArray().apply { put("app_name") })
            })
        })

        // 2. play_youtube
        funcArray.put(JSONObject().apply {
            put("name", "play_youtube")
            put("description", "Directly searches and plays a song, artist, video or playlist on YouTube (e.g. Pawan Singh latest song).")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("query", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Song name or video search query.")
                    })
                })
                put("required", JSONArray().apply { put("query") })
            })
        })

        // 3. open_whatsapp
        funcArray.put(JSONObject().apply {
            put("name", "open_whatsapp")
            put("description", "Opens WhatsApp and optionally prepares message for a contact.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("contact", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Contact name or phone number.")
                    })
                    put("message", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Message text to send.")
                    })
                })
            })
        })

        // 4. open_camera
        funcArray.put(JSONObject().apply {
            put("name", "open_camera")
            put("description", "Opens the phone camera capture screen.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject())
            })
        })

        // 5. toggle_torch
        funcArray.put(JSONObject().apply {
            put("name", "toggle_torch")
            put("description", "Turns the phone flashlight ON or OFF.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("enable", JSONObject().apply {
                        put("type", "BOOLEAN")
                        put("description", "True to turn on, False to turn off.")
                    })
                })
                put("required", JSONArray().apply { put("enable") })
            })
        })

        // 6. set_volume
        funcArray.put(JSONObject().apply {
            put("name", "set_volume")
            put("description", "Sets device media volume percentage (0 to 100).")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("percent", JSONObject().apply {
                        put("type", "INTEGER")
                        put("description", "Volume percentage from 0 to 100.")
                    })
                })
                put("required", JSONArray().apply { put("percent") })
            })
        })

        toolsArray.put(JSONObject().apply {
            put("function_declarations", funcArray)
        })

        return toolsArray
    }
}
