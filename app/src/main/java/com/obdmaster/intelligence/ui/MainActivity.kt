package com.obdmaster.intelligence.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.obdmaster.intelligence.domain.model.ConnectionState
import com.obdmaster.intelligence.ui.navigation.NavRoutes
import com.obdmaster.intelligence.ui.screens.adapter.AdapterScreen
import com.obdmaster.intelligence.ui.screens.ai.AiScreen
import com.obdmaster.intelligence.ui.screens.connect.ConnectScreen
import com.obdmaster.intelligence.ui.screens.dashboard.DashboardScreen
import com.obdmaster.intelligence.ui.screens.db.DbScreen
import com.obdmaster.intelligence.ui.screens.ecu.EcuScreen
import com.obdmaster.intelligence.ui.screens.knowledge.KnowledgeScreen
import com.obdmaster.intelligence.ui.screens.protocol.ProtocolScreen
import com.obdmaster.intelligence.ui.screens.report.ReportScreen
import com.obdmaster.intelligence.ui.screens.vehicle.VehicleScreen
import com.obdmaster.intelligence.ui.theme.ObdMasterTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ObdMasterTheme {
                ObdMasterAppRoot()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObdMasterAppRoot(vm: MainViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val readOnly by vm.isReadOnly.collectAsState()
    val conn by vm.connectionState.collectAsState()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    "OBD Master Intelligence",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium
                )
                if (readOnly) {
                    AssistChip(
                        onClick = {},
                        label = { Text("READ ONLY") },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            when (conn) {
                                ConnectionState.CONNECTED -> "CONNECTED"
                                ConnectionState.CONNECTING -> "CONNECTING…"
                                ConnectionState.ERROR -> "ERROR"
                                else -> "DISCONNECTED"
                            }
                        )
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                NavRoutes.menu.forEach { item ->
                    NavigationDrawerItem(
                        label = { Text("${item.titleHu} / ${item.titleEn}") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            nav.navigate(item.route) { launchSingleTop = true }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("OBD Master Intelligence Tester AI") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    }
                )
            }
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = NavRoutes.Connect.route,
                modifier = Modifier.padding(padding)
            ) {
                composable(NavRoutes.Connect.route) { ConnectScreen(vm) }
                composable(NavRoutes.Dashboard.route) { DashboardScreen(vm) }
                composable(NavRoutes.Adapter.route) { AdapterScreen(vm) }
                composable(NavRoutes.Protocol.route) { ProtocolScreen(vm) }
                composable(NavRoutes.Vehicle.route) { VehicleScreen(vm) }
                composable(NavRoutes.Ecu.route) { EcuScreen(vm) }
                composable(NavRoutes.Knowledge.route) { KnowledgeScreen(vm) }
                composable(NavRoutes.Ai.route) { AiScreen(vm) }
                composable(NavRoutes.Report.route) { ReportScreen(vm) }
                composable(NavRoutes.Db.route) { DbScreen(vm) }
            }
        }
    }
}
