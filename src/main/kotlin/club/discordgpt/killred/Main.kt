package club.discordgpt.killred

import com.theokanning.openai.OpenAiService
import com.theokanning.openai.completion.CompletionRequest
import discord4j.core.DiscordClient
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent
import discord4j.core.`object`.command.ApplicationCommandOption
import discord4j.core.spec.InteractionReplyEditSpec
import discord4j.discordjson.json.ApplicationCommandOptionData
import discord4j.discordjson.json.ApplicationCommandRequest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Duration
import kotlin.concurrent.thread
import kotlin.system.exitProcess


object Main {
    private val format = Json { prettyPrint = true }

    @JvmStatic
    fun main(args: Array<String>) {
        val data = Config("", "", "text-davinci-003", 0.7, 1.0,0.0, 0.0, 20, 10, 21)
        val defaultConfig = format.encodeToString(data)
        val file = File("config.json")

        if (!file.exists()) {
            if (file.createNewFile()) file.writeText(defaultConfig)

            println("The config file does not exist, so it has been automatically created.")
            exitProcess(0)
        }

        val config = format.decodeFromString<Config>(file.readText())

        val service = OpenAiService(config.chatGPTToken, Duration.ofSeconds(config.requestTimeout))

        val client = DiscordClient.create(config.discordToken)
        val gateway = client.login().block() ?: return

        val applicationId = client.applicationId.block() as Long
        val chatGPTCommand =
            ApplicationCommandRequest.builder().name("chatgpt")
                .description("Ask ChatGPT whatever you want to ask.")
                .addOption(ApplicationCommandOptionData.builder().name("message").description("Your message")
                    .type(ApplicationCommandOption.Type.STRING.value).required(true).build()).build()

        client.applicationService.createGlobalApplicationCommand(applicationId, chatGPTCommand).subscribe()

        gateway.on(ChatInputInteractionEvent::class.java).subscribe { event ->
            runCatching {
                if (event.commandName == "chatgpt") {
                    val input = event.getOption("message").flatMap { it.value }.map { it.asString() }.get()

                    event.deferReply()

                    thread {
                        val builder = StringBuilder("> Asking: **$input**\n")
                        var lastReasons = "length"
                        var maxAttempt = 0

                        while (true) {
                            runCatching { //if (maxAttempt == 0) channel.type().block()
                                service.createCompletion(CompletionRequest.builder().model("text-davinci-003")
                                    .temperature(config.temperature)
                                    .topP(config.topP)
                                    .frequencyPenalty(config.frequencyPenalty)
                                    .presencePenalty(config.presencePenalty)
                                    .maxTokens(config.maxTokens)
                                    .prompt(if (maxAttempt == 0) input else "$input $builder")
                                    .build()).choices.forEach {
                                    builder.append(it.text)
                                    lastReasons = it.finish_reason
                                }
                            }.onFailure {
                                event.reply(it.toString()).subscribe()
                                return@thread
                            }

                            if (maxAttempt == 0) {
                                event.reply(builder.toString()).subscribe()
                            } else {
                                event.editReply(InteractionReplyEditSpec.builder().build()
                                    .withContentOrNull(builder.toString())).subscribe()
                            }

                            Thread.sleep(300)

                            maxAttempt++

                            if (maxAttempt > data.maxAttempt || builder.length > 2000 || lastReasons != "length") break
                        }

                    }
                }
            }.onFailure {
                event.reply(it.toString()).subscribe()
            }
        }

        gateway.onDisconnect().block()
    }
}
