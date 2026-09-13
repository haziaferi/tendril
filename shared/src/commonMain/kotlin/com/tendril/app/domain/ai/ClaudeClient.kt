package com.tendril.app.domain.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * §0.6.15 — the one network call in the app that is not sync. Built per press and dropped after,
 * so nothing holds the key or a connection while no verb is running; `HttpURLConnection`, as
 * the Google Calendar sync already uses (no new dependency — the build is offline). The key
 * travels as a header, never in the body ([AiVerbsTest] pins that).
 */
class ClaudeClient(private val apiKey: String, private val model: String) {

    suspend fun complete(verb: AiVerb, text: String): Result<String> = withContext(Dispatchers.IO) {
        val body = encodeRequest(request(verb, model, text))
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("content-type", "application/json")
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("anthropic-version", API_VERSION)
            }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val reply = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status in 200..299) parseMessagesResponse(reply)
            else Result.failure(AiFailure(describeFailure(status, reply, null)))
        } catch (e: Exception) {
            Result.failure(AiFailure(describeFailure(null, null, e)))
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        const val API_VERSION = "2023-06-01"
        const val TIMEOUT_MS = 30_000
    }
}
