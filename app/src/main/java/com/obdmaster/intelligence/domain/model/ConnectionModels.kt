package com.obdmaster.intelligence.domain.model

data class AdapterDevice(
    val id: String,
    val name: String,
    val transport: TransportType,
    val address: String,
    val extra: String = "",
    val bonded: Boolean = false,
    val isBle: Boolean = false
)

data class ConnectionTarget(
    val transport: TransportType,
    val address: String,
    val displayName: String = address,
    /** WiFi only */
    val port: Int = 35000,
    /** BLE characteristic UUIDs optional overrides */
    val bleServiceUuid: String? = null,
    val bleWriteUuid: String? = null,
    val bleNotifyUuid: String? = null
)

class NotConnectedException(message: String = "No OBD adapter connected. Connect via Bluetooth, WiFi, or USB first.") :
    IllegalStateException(message)

class TransportException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
