package club.discordgpt.killred

import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val discordToken: String,
    val chatGPTToken: String,
    val model: String,
    val temperature: Double,
    val topP: Double,
    val frequencyPenalty: Double,
    val presencePenalty: Double,
    val maxTokens: Int,
    val maxAttempt: Int,
    val requestTimeout: Long,
)