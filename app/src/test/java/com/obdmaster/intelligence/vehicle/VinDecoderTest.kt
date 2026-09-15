package com.obdmaster.intelligence.vehicle

import com.google.common.truth.Truth.assertThat
import com.obdmaster.intelligence.obd.modes.ObdModes
import org.junit.Test

class VinDecoderTest {
    private val decoder = VinDecoder()

    @Test
    fun decodesBmwWmi() {
        val info = decoder.decode("WBA3A5C50EF123456")
        assertThat(info.brand).isEqualTo("BMW")
        assertThat(info.vin).hasLength(17)
    }

    @Test
    fun supportedBrandsIncludeTesla() {
        assertThat(decoder.supportedBrands()).contains("Tesla")
        assertThat(decoder.supportedBrands()).contains("Volkswagen")
    }

    @Test
    fun parseVinFromMode09() {
        val hex = "49 02 01 57 42 41 33 41 35 43 35 30 45 46 31 32 33 34 35 36"
        val vin = ObdModes.parseVin(hex)
        assertThat(vin).startsWith("WBA")
    }
}
