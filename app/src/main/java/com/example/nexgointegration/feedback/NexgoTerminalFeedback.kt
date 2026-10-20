package com.example.nexgointegration.feedback

import android.content.Context
import com.nexgo.oaf.apiv3.APIProxy
import com.nexgo.oaf.apiv3.device.led.LightModeEnum
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Implémentation Nexgo : seule classe qui parle au LED driver et au buzzer.
 * Chaque bip fait clignoter la LED en même temps, comme un petit rythme lumineux.
 */
class NexgoTerminalFeedback(
    context: Context,
    private val scope: CoroutineScope
) : TerminalFeedback {

    private val deviceEngine = APIProxy.getDeviceEngine(context)
    private val led = deviceEngine.ledDriver
    private val beeper = deviceEngine.beeper

    private var job: Job? = null

    /** Un bip (durée) suivi d'un silence (pause). */
    private data class Pulse(val beepMs: Int, val pauseMs: Int)

    // « ta-ta-taaa » : deux notes courtes puis une longue, ça sonne comme une victoire.
    private val successMelody = listOf(Pulse(90, 70), Pulse(90, 70), Pulse(280, 0))

    // « bom… bom… booooom » : lent et grave, ça sonne comme un refus.
    private val failureMelody = listOf(Pulse(450, 130), Pulse(450, 130), Pulse(850, 0))

    override fun success() = play(LightModeEnum.GREEN, successMelody, holdLedMs = 1_500)

    override fun failure() = play(LightModeEnum.RED, failureMelody, holdLedMs = 2_000)

    override fun reset() {
        job?.cancel()
        allLedsOff()
    }

    private fun play(color: LightModeEnum, melody: List<Pulse>, holdLedMs: Long) {
        job?.cancel()
        job = scope.launch(Dispatchers.IO) {
            try {
                for (pulse in melody) {
                    led.setLed(color, true)
                    beeper.beep(pulse.beepMs)
                    delay(pulse.beepMs.toLong())
                    led.setLed(color, false)
                    delay(pulse.pauseMs.toLong())
                }
                // La LED reste allumée un moment après la dernière note.
                led.setLed(color, true)
                delay(holdLedMs)
            } finally {
                withContext(NonCancellable) { allLedsOff() }
            }
        }
    }

    private fun allLedsOff() {
        LightModeEnum.values().forEach { runCatching { led.setLed(it, false) } }
    }
}