package com.tendril.app.domain.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * §0.6.15 / B§6 #18 — the verbs (three, and since 2026-09-22 the fourth, *Mind map* — §10's
 * Claude-generated map, whose reply is a nested list parsed by [parseOutline]) and the shape of
 * what they send. Pure: the request is a
 * value, the response a parse; the one network call lives in [ClaudeClient]. Only the selected
 * text and the verb's instruction go out — never the page title, other blocks, or anything
 * about the person.
 */
enum class AiVerb(val label: String, val instruction: String) {
    REWRITE(
        "Rewrite",
        "Rewrite the text for clarity and flow. Keep its meaning, language, tone and length. " +
            "Return only the rewritten text, with no preamble.",
    ),
    EXPAND(
        "Expand",
        "Expand the text into a fuller version: develop its points, add the detail it implies, " +
            "keep its language and tone. Return only the expanded text, with no preamble.",
    ),
    SUMMARISE(
        "Summarise",
        "Summarise the text in a few short sentences, in its own language. " +
            "Return only the summary, with no preamble.",
    ),
    MIND_MAP(
        "Mind map",
        "Turn the text into a mind map written as a nested Markdown list, in the text's own language: " +
            "exactly one root line naming the subject, then its branches and their points as lines " +
            "beginning with \"- \", indented two spaces per level, each line a few words. " +
            "Return only the list, with no preamble and no headings.",
    ),
}

/** The verbs whose answer replaces or follows the selection as text; [AiVerb.MIND_MAP]'s is rows. */
val AiVerb.isText: Boolean get() = this != AiVerb.MIND_MAP

/** The models the picker offers; the first is the default. Ids as the Messages API names them. */
val AiModels: List<String> = listOf("claude-sonnet-5", "claude-opus-5", "claude-haiku-4-5-20251001")
const val AI_MODEL_KEY = "ai_model"
const val AI_MAX_TOKENS = 1024

@Serializable
data class MessagesRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<Message>,
)

@Serializable
data class Message(val role: String, val content: String)

fun request(verb: AiVerb, model: String, text: String): MessagesRequest =
    MessagesRequest(model = model, maxTokens = AI_MAX_TOKENS, system = verb.instruction, messages = listOf(Message("user", text)))

@Serializable
private data class MessagesResponse(val content: List<ContentBlock> = emptyList(), val error: ApiError? = null)

@Serializable
private data class ContentBlock(val type: String, val text: String? = null)

@Serializable
private data class ApiError(val type: String? = null, val message: String? = null)

private val json = Json { ignoreUnknownKeys = true }

fun encodeRequest(request: MessagesRequest): String = json.encodeToString(MessagesRequest.serializer(), request)

/** The first text block of a reply; an API error body becomes a failure carrying its message. */
fun parseMessagesResponse(body: String): Result<String> {
    val parsed = runCatching { json.decodeFromString(MessagesResponse.serializer(), body) }.getOrElse { return Result.failure(AiFailure("Unreadable reply")) }
    parsed.error?.let { return Result.failure(AiFailure(it.message ?: "The API returned an error")) }
    val text = parsed.content.firstOrNull { it.type == "text" }?.text?.trim()
    return if (text.isNullOrEmpty()) Result.failure(AiFailure("The reply held no text")) else Result.success(text)
}

class AiFailure(message: String) : Exception(message)

/** What the sheet says when a call fails: the HTTP status where it tells the story, the API's
 * own message where it has one, "no connection" for the rest. */
fun describeFailure(status: Int?, body: String?, cause: Throwable?): String = when {
    status == 401 || status == 403 -> "The key was rejected — check it in Settings"
    status == 429 -> "Rate limited — try again in a moment"
    status != null && status >= 500 -> "Anthropic's service is unavailable right now"
    status != null -> parseMessagesResponse(body.orEmpty()).exceptionOrNull()?.message ?: "Request failed ($status)"
    cause != null -> "No connection"
    else -> "Request failed"
}
