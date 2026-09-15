package com.obdmaster.intelligence.scoring

import com.obdmaster.intelligence.domain.model.AdapterCapabilities
import com.obdmaster.intelligence.domain.model.AdapterType
import com.obdmaster.intelligence.domain.model.EcuCategory
import com.obdmaster.intelligence.domain.model.EcuNode
import com.obdmaster.intelligence.domain.model.ObdProtocol
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScoreEngineTest {
    private val engine = ScoreEngine()

    @Test
    fun highEndAdapterScoresHigh() {
        val caps = AdapterCapabilities(
            type = AdapterType.STN2120,
            firmware = "STN2120",
            supportsObd2 = true,
            supportsCan = true,
            supportsUds = true,
            supportsIsoTp = true,
            supportsProtocols = listOf(ObdProtocol.ISO_15765_CAN_11BIT_500),
            baudRates = listOf(500_000)
        )
        val ecus = listOf(
            EcuNode("7E0", "Engine", EcuCategory.ENGINE, online = true)
        )
        val score = engine.score(caps, caps.supportsProtocols, ecus, vinOk = true)
        assertThat(score.totalPercent).isAtLeast(70f)
        assertThat(score.stars).isAtLeast(3)
        assertThat(score.categories).hasSize(4)
    }

    @Test
    fun starsFromBoundaries() {
        assertThat(engine.starsFrom(95f)).isEqualTo(5)
        assertThat(engine.starsFrom(80f)).isEqualTo(4)
        assertThat(engine.starsFrom(65f)).isEqualTo(3)
        assertThat(engine.starsFrom(45f)).isEqualTo(2)
        assertThat(engine.starsFrom(25f)).isEqualTo(1)
        assertThat(engine.starsFrom(5f)).isEqualTo(0)
    }
}
