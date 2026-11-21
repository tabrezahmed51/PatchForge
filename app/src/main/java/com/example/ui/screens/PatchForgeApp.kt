package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.local.PatchStatus
import com.example.ui.theme.ForgeTheme
import com.example.ui.viewmodel.PatchForgeViewModel

const val ROUTE_SCAN = "scan_workspace"
const val ROUTE_PATCHES = "patch_workbench"
const val ROUTE_AGENT = "agent_chat"
const val ROUTE_CONTROL = "control_plane"

private data class NavDestinationItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val TopLevelDestinations = listOf(
    NavDestinationItem(
        route = ROUTE_SCAN,
        labelRes = R.string.nav_scan,
        selectedIcon = Icons.Filled.Radar,
        unselectedIcon = Icons.Outlined.Radar,
        testTag = "nav_scan"
    ),
    NavDestinationItem(
        route = ROUTE_PATCHES,
        labelRes = R.string.nav_patches,
        selectedIcon = Icons.Filled.Build,
        unselectedIcon = Icons.Outlined.Build,
        testTag = "nav_patches"
    ),
    NavDestinationItem(
        route = ROUTE_AGENT,
        labelRes = R.string.nav_agent,
        selectedIcon = Icons.Filled.SmartToy,
        unselectedIcon = Icons.Outlined.SmartToy,
        testTag = "nav_agent"
    ),
    NavDestinationItem(
        route = ROUTE_CONTROL,
        labelRes = R.string.nav_control,
        selectedIcon = Icons.Filled.Dns,
        unselectedIcon = Icons.Outlined.Dns,
        testTag = "nav_control"
    )
)

@Composable
fun PatchForgeApp(
    viewModel: PatchForgeViewModel
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_SCAN
    val snackbarHostState = remember { SnackbarHostState() }
    val forge = ForgeTheme.colors

    val allFindings by viewModel.allFindings.collectAsStateWithLifecycle()
    val pendingQueue by viewModel.pendingSyncActions.collectAsStateWithLifecycle()

    val pendingPatchCount = allFindings.count {
        it.patchStatus == PatchStatus.PENDING || it.patchStatus == PatchStatus.APPROVED
    }
    val activeQueueCount = pendingQueue.count { it.status == "QUEUED" || it.status == "FAILED" }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val useNavRail = maxWidth >= 600.dp

        if (useNavRail) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = {
                    SnackbarHost(snackbarHostState) { data ->
                        Snackbar(
                            snackbarData = data,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            shape = MaterialTheme.shapes.extraSmall
                        )
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        TopLevelDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            val badgeCount = when (dest.route) {
                                ROUTE_PATCHES -> pendingPatchCount
                                ROUTE_CONTROL -> activeQueueCount
                                else -> 0
                            }
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    BadgedIcon(
                                        icon = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes),
                                        badgeCount = badgeCount
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(dest.labelRes),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = forge.amber,
                                    selectedTextColor = forge.amber,
                                    indicatorColor = forge.amberContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                    VerticalDivider(color = forge.borderHairline)
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        PatchForgeNavHost(
                            navController = navController,
                            viewModel = viewModel,
                            onNavigateToPatches = { navigateTo(ROUTE_PATCHES) }
                        )
                    }
                }
            }
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.statusBars,
                snackbarHost = {
                    SnackbarHost(snackbarHostState) { data ->
                        Snackbar(
                            snackbarData = data,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            shape = MaterialTheme.shapes.extraSmall
                        )
                    }
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        TopLevelDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            val badgeCount = when (dest.route) {
                                ROUTE_PATCHES -> pendingPatchCount
                                ROUTE_CONTROL -> activeQueueCount
                                else -> 0
                            }
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    BadgedIcon(
                                        icon = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes),
                                        badgeCount = badgeCount
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(dest.labelRes),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = forge.amber,
                                    selectedTextColor = forge.amber,
                                    indicatorColor = forge.amberContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    PatchForgeNavHost(
                        navController = navController,
                        viewModel = viewModel,
                        onNavigateToPatches = { navigateTo(ROUTE_PATCHES) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BadgedIcon(
    icon: ImageVector,
    contentDescription: String,
    badgeCount: Int
) {
    val forge = ForgeTheme.colors
    if (badgeCount > 0) {
        BadgedBox(
            badge = {
                Badge(
                    containerColor = forge.amber,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Text(text = if (badgeCount > 99) "99+" else "$badgeCount")
                }
            }
        ) {
            Icon(imageVector = icon, contentDescription = contentDescription)
        }
    } else {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

@Composable
private fun PatchForgeNavHost(
    navController: androidx.navigation.NavHostController,
    viewModel: PatchForgeViewModel,
    onNavigateToPatches: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = ROUTE_SCAN
    ) {
        composable(ROUTE_SCAN) {
            ScanWorkspaceScreen(
                viewModel = viewModel,
                onNavigateToPatches = onNavigateToPatches
            )
        }
        composable(ROUTE_PATCHES) {
            PatchWorkbenchScreen(viewModel = viewModel)
        }
        composable(ROUTE_AGENT) {
            AgentChatScreen(viewModel = viewModel)
        }
        composable(ROUTE_CONTROL) {
            ControlPlaneScreen(viewModel = viewModel)
        }
    }
}
