package com.example

import android.app.Application
import com.example.ai.GeminiService
import com.example.bot.CustomReplyEngine
import com.example.bot.MessageProcessor
import com.example.data.SecureApiKeyStorage
import com.example.data.local.AppDatabase
import com.example.data.repository.ActivityLogRepository
import com.example.data.repository.BotSettingsRepository
import com.example.data.repository.CustomReplyRepository
import com.example.data.repository.ProcessedMessageRepository
import com.example.service.WhatsAppAccessibilityService
import com.example.whatsapp.WhatsAppAccessibilityController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class QuantumBotApp : Application() {

    companion object {
        lateinit var instance: QuantumBotApp
            private set
    }

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var database: AppDatabase
        private set

    lateinit var secureApiKeyStorage: SecureApiKeyStorage
        private set

    lateinit var botSettingsRepository: BotSettingsRepository
        private set

    lateinit var customReplyRepository: CustomReplyRepository
        private set

    lateinit var activityLogRepository: ActivityLogRepository
        private set

    lateinit var processedMessageRepository: ProcessedMessageRepository
        private set

    lateinit var geminiService: GeminiService
        private set

    lateinit var customReplyEngine: CustomReplyEngine
        private set

    lateinit var accessibilityController: WhatsAppAccessibilityController
        private set

    lateinit var messageProcessor: MessageProcessor
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getInstance(this)
        secureApiKeyStorage = SecureApiKeyStorage(this)
        botSettingsRepository = BotSettingsRepository(this)
        customReplyRepository = CustomReplyRepository(database.customReplyDao())
        activityLogRepository = ActivityLogRepository(database.activityLogDao())
        processedMessageRepository = ProcessedMessageRepository(database.processedMessageDao())

        geminiService = GeminiService(secureApiKeyStorage)
        customReplyEngine = CustomReplyEngine()

        accessibilityController = WhatsAppAccessibilityController {
            WhatsAppAccessibilityService.instance
        }

        messageProcessor = MessageProcessor(
            settingsRepository = botSettingsRepository,
            customReplyRepository = customReplyRepository,
            processedMessageRepository = processedMessageRepository,
            activityLogRepository = activityLogRepository,
            customReplyEngine = customReplyEngine,
            geminiService = geminiService,
            accessibilityController = accessibilityController,
            scope = applicationScope
        )

        // Populate initial starter custom reply templates if the database is fresh
        applicationScope.launch(Dispatchers.IO) {
            customReplyRepository.populateDefaultsIfEmpty()
        }
    }
}
