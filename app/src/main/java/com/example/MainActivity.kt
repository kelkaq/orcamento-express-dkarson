package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.billing.EntitlementStore
import com.example.billing.PlayBillingManager
import com.example.data.database.AppDatabase
import com.example.data.repository.QuoteRepository
import com.example.ui.components.AppBottomNav
import com.example.ui.navigation.Screen
import com.example.ui.screens.catalog.CatalogScreen
import com.example.ui.screens.clients.ClientsScreen
import com.example.ui.screens.company.CompanyScreen
import com.example.ui.screens.quote_detail.QuoteDetailScreen
import com.example.ui.screens.quote_edit.QuoteEditorScreen
import com.example.ui.screens.quotes.QuotesScreen
import com.example.ui.screens.pro.ProPlanScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.CatalogViewModel
import com.example.ui.viewmodel.CatalogViewModelFactory
import com.example.ui.viewmodel.ClientsViewModel
import com.example.ui.viewmodel.ClientsViewModelFactory
import com.example.ui.viewmodel.CompanyViewModel
import com.example.ui.viewmodel.CompanyViewModelFactory
import com.example.ui.viewmodel.QuoteEditorViewModel
import com.example.ui.viewmodel.QuoteEditorViewModelFactory
import com.example.ui.viewmodel.QuotesViewModel
import com.example.ui.viewmodel.QuotesViewModelFactory

class MainActivity : ComponentActivity() {
    private lateinit var manager: PlayBillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(applicationContext)
        val entitlement = EntitlementStore(applicationContext)
        manager = PlayBillingManager(applicationContext, entitlement)
        val repository = QuoteRepository(database) { entitlement.isProNow() }

        setContent {
            MyApplicationTheme {
                OrcamentoExpressApp(repository = repository, billingManager = manager)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::manager.isInitialized) manager.restorePurchases()
    }

    override fun onDestroy() {
        if (::manager.isInitialized) manager.close()
        super.onDestroy()
    }
}

@Composable
fun OrcamentoExpressApp(repository: QuoteRepository, billingManager: PlayBillingManager) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val billingState by billingManager.state.collectAsStateWithLifecycle()
    val goToPro: () -> Unit = {
        navController.navigate(Screen.Pro.route) { launchSingleTop = true }
    }

    // ViewModels for persistent tabs
    val quotesViewModel: QuotesViewModel = viewModel(
        factory = QuotesViewModelFactory(repository)
    )
    val clientsViewModel: ClientsViewModel = viewModel(
        factory = ClientsViewModelFactory(repository)
    )
    val catalogViewModel: CatalogViewModel = viewModel(
        factory = CatalogViewModelFactory(repository)
    )
    val companyViewModel: CompanyViewModel = viewModel(
        factory = CompanyViewModelFactory(repository)
    )

    val isRootScreen = currentRoute in listOf(
        Screen.Quotes.route,
        Screen.Clients.route,
        Screen.Catalog.route,
        Screen.Company.route
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (isRootScreen) {
                AppBottomNav(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(Screen.Quotes.route) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Quotes.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            // Quotes Tab (Home)
            composable(Screen.Quotes.route) {
                QuotesScreen(
                    viewModel = quotesViewModel,
                    companyViewModel = companyViewModel,
                    isPro = billingState.isPro,
                    onUpgrade = goToPro,
                    onNewQuote = {
                        navController.navigate(Screen.QuoteEditor.createRoute())
                    },
                    onEditQuote = { quoteId ->
                        navController.navigate(Screen.QuoteEditor.createRoute(quoteId))
                    },
                    onViewDetail = { quoteId ->
                        navController.navigate(Screen.QuoteDetail.createRoute(quoteId))
                    },
                    onRestoreData = {
                        navController.navigate(Screen.Company.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            // Clients Tab
            composable(Screen.Clients.route) {
                ClientsScreen(viewModel = clientsViewModel)
            }

            // Catalog Tab
            composable(Screen.Catalog.route) {
                CatalogScreen(viewModel = catalogViewModel)
            }

            // Company & Settings Tab
            composable(Screen.Company.route) {
                CompanyScreen(viewModel = companyViewModel, isPro = billingState.isPro,
                    onOpenPlan = goToPro)
            }

            composable(Screen.Pro.route) {
                ProPlanScreen(manager = billingManager, onBack = { navController.popBackStack() })
            }

            // Quote Editor Screen (New or Edit)
            composable(
                route = Screen.QuoteEditor.route,
                arguments = listOf(
                    navArgument("quoteId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val quoteIdStr = backStackEntry.arguments?.getString("quoteId")
                val quoteId = quoteIdStr?.toLongOrNull()

                val editorViewModel: QuoteEditorViewModel = viewModel(
                    key = "quote_editor_$quoteId",
                    factory = QuoteEditorViewModelFactory(repository, quoteId)
                )

                QuoteEditorScreen(
                    viewModel = editorViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                        quotesViewModel.refreshMetrics()
                    },
                    isPro = billingState.isPro,
                    onUpgrade = goToPro,
                    onQuoteSaved = { savedId ->
                        navController.popBackStack()
                        navController.navigate(Screen.QuoteDetail.createRoute(savedId))
                        quotesViewModel.refreshMetrics()
                    }
                )
            }

            // Quote Detail Screen
            composable(
                route = Screen.QuoteDetail.route,
                arguments = listOf(
                    navArgument("quoteId") {
                        type = NavType.LongType
                    }
                )
            ) { backStackEntry ->
                val quoteId = backStackEntry.arguments?.getLong("quoteId") ?: 0L
                QuoteDetailScreen(
                    quoteId = quoteId,
                    repository = repository,
                    canUsePro = billingState.isPro,
                    onUpgrade = goToPro,
                    onNavigateBack = {
                        navController.popBackStack()
                        quotesViewModel.refreshMetrics()
                    },
                    onEditQuote = { id ->
                        navController.navigate(Screen.QuoteEditor.createRoute(id))
                    }
                )
            }
        }
    }
}
