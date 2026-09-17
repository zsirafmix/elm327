package com.obdmaster.intelligence.data.transport

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Flutter [ObdService] spirit: one shared RX / pending / busy command session
 * for Classic SPP + BLE UART. Both stacks [attach] their write path to [session].
 */
@Singleton
class ObdBluetoothFacade @Inject constructor() {
    val session = ElmByteStreamSession()
}
