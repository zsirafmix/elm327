package com.obdmaster.intelligence.ui.navigation

sealed class NavRoutes(val route: String, val titleHu: String, val titleEn: String) {
    data object Connect : NavRoutes("connect", "Kapcsolat", "Connect")
    data object Dashboard : NavRoutes("dashboard", "Műszerfal", "Dashboard")
    data object Adapter : NavRoutes("adapter", "Adapter teszt", "Adapter test")
    data object Protocol : NavRoutes("protocol", "OBD protokoll", "OBD protocol")
    data object Vehicle : NavRoutes("vehicle", "Jármű felismerés", "Vehicle recognition")
    data object Ecu : NavRoutes("ecu", "ECU kereső", "ECU finder")
    data object Knowledge : NavRoutes("knowledge", "Járműspecifikus tudás", "Vehicle knowledge")
    data object Ai : NavRoutes("ai", "AI elemzés", "AI analysis")
    data object Report : NavRoutes("report", "PDF jelentés", "PDF report")
    data object Db : NavRoutes("db", "Adatbázis / katalógus", "DB / catalog")

    companion object {
        val menu = listOf(Connect, Dashboard, Adapter, Protocol, Vehicle, Ecu, Knowledge, Ai, Report, Db)
    }
}
