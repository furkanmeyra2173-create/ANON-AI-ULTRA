package com.anonai.ultra

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class AiReply(val text: String, val model: String)

object OpenRouterClient {
    private const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"

    fun ask(
        apiKey: String,
        modelId: String,
        systemPrompt: String,
        conversation: List<Pair<String, String>>
    ): AiReply {
        require(apiKey.isNotBlank()) { "Önce Ayarlar bölümüne OpenRouter API anahtarını ekle." }
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        conversation.forEach { (role, content) ->
            messages.put(JSONObject().put("role", role).put("content", content))
        }

        val body = JSONObject()
            .put("model", modelId)
            .put("messages", messages)
            .put("temperature", 0.7)
            .put("max_tokens", 3000)

        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 90000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("HTTP-Referer", "https://github.com/furkanmeyra2173-create/ANON-AI-ULTRA")
            setRequestProperty("X-Title", "ANON AI ULTRA")
        }

        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            val json = JSONObject(response)
            if (status !in 200..299) {
                val message = json.optJSONObject("error")?.optString("message")
                    ?: "API isteği başarısız oldu (HTTP $status)."
                throw IllegalStateException(message)
            }
            val choice = json.optJSONArray("choices")?.optJSONObject(0)
                ?: throw IllegalStateException("Model boş yanıt döndürdü.")
            val answer = choice.optJSONObject("message")?.optString("content").orEmpty()
            if (answer.isBlank() || answer == "null") {
                throw IllegalStateException("Model metin yanıtı döndürmedi.")
            }
            AiReply(answer, json.optString("model", modelId))
        } finally {
            connection.disconnect()
        }
    }
}
