package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.TokoYogaDatabase
import com.example.data.repository.TokoYogaRepository
import com.example.mesh3d.AssemblyPart
import com.example.ui.*
import com.example.ui.theme.TokoYogaTheme
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
    object Studio3D : Screen("studio_3d", "3D Studio", Icons.Default.ViewInAr)
    object ProfileCanvas : Screen("profile_canvas", "Profil 2D", Icons.Default.Draw)
    object POS : Screen("pos", "Kasir POS", Icons.Default.PointOfSale)
    object Katalog : Screen("katalog", "Katalog", Icons.Default.Inventory2)
    object Riwayat : Screen("riwayat", "Riwayat Nota", Icons.Default.ReceiptLong)
    object Blueprint : Screen("blueprint", "Flutter", Icons.Default.Code)
}

class MainActivity : ComponentActivity() {

    private lateinit var repository: TokoYogaRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = TokoYogaDatabase.getInstance(applicationContext)
        repository = TokoYogaRepository(db)

        setContent {
            TokoYogaTheme {
                TokoYogaApp(repository = repository)
            }
        }
    }
}

@Composable
fun TokoYogaApp(repository: TokoYogaRepository) {
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            repository.checkAndSeedInitialData()
        }
    }

    // Default screen: 3D Studio (Moblo style multi-object assembly)
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Studio3D) }
    var activeFormMode by remember { mutableStateOf<Int?>(null) } // null: no form, -1: add new, >0: edit ID
    var pendingAssemblyParts by remember { mutableStateOf<List<AssemblyPart>?>(null) }
    var pendingCustomPart by remember { mutableStateOf<AssemblyPart?>(null) }

    BackHandler(enabled = activeFormMode != null || currentScreen != Screen.Studio3D) {
        if (activeFormMode != null) {
            activeFormMode = null
        } else if (currentScreen != Screen.Studio3D) {
            currentScreen = Screen.Studio3D
        }
    }

    if (activeFormMode != null) {
        FormProfilPage(
            repository = repository,
            produkId = if (activeFormMode == -1) null else activeFormMode,
            onNavigateBack = { activeFormMode = null },
            onSuccessSaved = { activeFormMode = null }
        )
    } else {
        // Layout Landscape Utama: NavigationRail di sisi kiri + Area Konten di kanan
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            NavigationRail(
                modifier = Modifier
                    .width(72.dp)
                    .fillMaxHeight()
                    .testTag("nav_rail_landscape"),
                containerColor = Color(0xFF0F172A),
                contentColor = Color.White
            ) {
                Spacer(Modifier.height(8.dp))
                val navItems = listOf(
                    Screen.Dashboard,
                    Screen.Studio3D,
                    Screen.ProfileCanvas,
                    Screen.POS,
                    Screen.Katalog,
                    Screen.Riwayat,
                    Screen.Blueprint
                )

                navItems.forEach { screen ->
                    val isSelected = currentScreen == screen
                    NavigationRailItem(
                        selected = isSelected,
                        onClick = { currentScreen = screen },
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color(0xFFF59E0B),
                            indicatorColor = Color(0xFFD97706),
                            unselectedIconColor = Color(0xFF94A3B8),
                            unselectedTextColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier.testTag("nav_${screen.route}")
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                when (currentScreen) {
                    Screen.Dashboard -> {
                        InventoryDashboardScreen(
                            repository = repository,
                            onNavigateToCanvas = { currentScreen = Screen.ProfileCanvas },
                            onNavigateTo3DStudio = { _ -> currentScreen = Screen.Studio3D },
                            onNavigateToKatalog = { currentScreen = Screen.Katalog },
                            onNavigateToPOS = { currentScreen = Screen.POS }
                        )
                    }
                    Screen.Studio3D -> {
                        Studio3DScreen(
                            repository = repository,
                            onSendToPOS = { parts ->
                                pendingAssemblyParts = parts
                                currentScreen = Screen.POS
                            },
                            incomingCustomPart = pendingCustomPart,
                            onCustomPartHandled = { pendingCustomPart = null }
                        )
                    }
                    Screen.ProfileCanvas -> {
                        ProfileCanvasScreen(
                            repository = repository,
                            onNavigateToStudioWithPart = { part ->
                                pendingCustomPart = part
                                currentScreen = Screen.Studio3D
                            },
                            onNavigateBack = {
                                currentScreen = Screen.Studio3D
                            }
                        )
                    }
                    Screen.POS -> {
                        POSScreen(
                            repository = repository,
                            incomingAssemblyParts = pendingAssemblyParts,
                            onNavigateToStudio = { currentScreen = Screen.Studio3D },
                            onNavigateToKatalog = { currentScreen = Screen.Katalog },
                            onNavigateToRiwayat = { currentScreen = Screen.Riwayat }
                        )
                    }
                    Screen.Katalog -> {
                        KatalogScreen(
                            repository = repository,
                            onNavigateToAdd = { activeFormMode = -1 },
                            onNavigateToEdit = { id -> activeFormMode = id },
                            onNavigateTo3D = { _, _, _ ->
                                currentScreen = Screen.Studio3D
                            },
                            onNavigateToCanvas = {
                                currentScreen = Screen.ProfileCanvas
                            },
                            onNavigateToDashboard = {
                                currentScreen = Screen.Dashboard
                            }
                        )
                    }
                    Screen.Riwayat -> {
                        RiwayatScreen(repository = repository)
                    }
                    Screen.Blueprint -> {
                        FlutterBlueprintScreen()
                    }
                }
            }
        }
    }
}
