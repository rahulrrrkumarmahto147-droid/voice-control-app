package com.example.parser

/**
 * Architecture interface for future hotword / wake-word detection engine
 * such as "Hey VoiceControl".
 */
interface WakeWordDetector {
    fun startListening(onWakeWordDetected: () -> Unit)
    fun stopListening()
    val isSupported: Boolean
}

class SimulatedWakeWordDetector : WakeWordDetector {
    override val isSupported: Boolean = false

    override fun startListening(onWakeWordDetected: () -> Unit) {
        // Architecture placeholder: Real wake-word engines (e.g. Porcupine, PocketSphinx)
        // can be plugged in here without altering UI or Executor logic.
    }

    override fun stopListening() {}
}
