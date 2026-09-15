package com.obdmaster.intelligence.obd.uds

/**
 * UDS ISO 14229 service builders.
 * Allowed for capability/UI: 0x10 (DiagnosticSession — default session only as probe),
 * 0x19 (ReadDTC), 0x22 (ReadDataByIdentifier).
 * BLOCKED by SafetyGate: 0x11 Reset, 0x2F IO Control, 0x31 RoutineControl, 0x27 SecurityAccess.
 */
object UdsServices {
    const val SID_DIAG_SESSION = 0x10
    const val SID_ECU_RESET = 0x11
    const val SID_READ_DTC = 0x19
    const val SID_READ_DATA = 0x22
    const val SID_SECURITY = 0x27
    const val SID_IO_CONTROL = 0x2F
    const val SID_ROUTINE = 0x31

    fun readDataByIdentifier(did: Int): String =
        "22%04X".format(did)

    fun readDtcByStatusMask(mask: Int = 0xFF): String =
        "19 02 %02X".format(mask)

    fun defaultSessionProbe(): String = "10 01"

    /** Labels only — never send these in READ ONLY. */
    fun blockedCapabilityLabels(): List<String> = listOf(
        "0x11 ECU Reset — BLOCKED",
        "0x2F InputOutputControl — BLOCKED",
        "0x31 RoutineControl — BLOCKED",
        "0x27 SecurityAccess — BLOCKED"
    )

    fun isBlockedSid(sid: Int): Boolean =
        sid in setOf(SID_ECU_RESET, SID_IO_CONTROL, SID_ROUTINE, SID_SECURITY)
}
