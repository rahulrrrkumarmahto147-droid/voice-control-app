package com.example

import android.app.Application
import com.example.applauncher.AppResolver
import com.example.data.database.AppDatabase
import com.example.data.repository.HistoryRepository
import com.example.data.repository.SettingsRepository
import com.example.parser.RuleBasedCommandParser
import com.example.voice.TtsManager

class VoiceControlApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var historyRepository: HistoryRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var appResolver: AppResolver
        private set

    lateinit var commandParser: RuleBasedCommandParser
        private set

    lateinit var ttsManager: TtsManager
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        historyRepository = HistoryRepository(database.commandHistoryDao())
        settingsRepository = SettingsRepository(this)
        appResolver = AppResolver(this)
        commandParser = RuleBasedCommandParser()
        ttsManager = TtsManager(this) { success ->
            // Apply saved settings once initialized
            val currentSettings = settingsRepository.settings.value
            ttsManager.setSpeechRate(currentSettings.speechRate)
            ttsManager.setLanguage(currentSettings.languageCode)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        ttsManager.shutdown()
    }
}
