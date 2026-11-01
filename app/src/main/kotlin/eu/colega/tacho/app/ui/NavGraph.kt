package eu.colega.tacho.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.flow.StateFlow
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import eu.colega.tacho.app.R

/** Bottom navigation destinations. */
enum class TachoDestination(val route: String) {
    CARD("card"),
    TIMELINE("timeline"),
    CALENDAR("calendar"),
    INSIGHTS("insights"),
}

/** One CardViewModel per Activity, shared by all navigation tabs. */
@Composable
private fun sharedCardViewModel(): CardViewModel = hiltViewModel(LocalActivity.current as ComponentActivity)

@Composable
fun TachoNavGraph(
    cardViewModel: CardViewModel,
    attachedReader: StateFlow<android.hardware.usb.UsbDevice?>,
    onReadViaReader: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    val backStack = navController.currentBackStackEntryAsState()
    val current = backStack.value?.destination?.route ?: TachoDestination.CARD.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 0.dp,
            ) {
                TachoDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = current == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(TachoDestination.CARD.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            when (destination) {
                                TachoDestination.CARD -> Icon(Icons.Filled.CreditCard, null)
                                TachoDestination.TIMELINE -> Icon(Icons.Filled.Timeline, null)
                                TachoDestination.CALENDAR -> Icon(Icons.Filled.CalendarMonth, null)
                                TachoDestination.INSIGHTS -> Icon(Icons.Filled.Insights, null)
                            }
                        },
                        label = {
                            Text(
                                when (destination) {
                                    TachoDestination.CARD -> stringResource(R.string.tab_card)
                                    TachoDestination.TIMELINE -> stringResource(R.string.tab_timeline)
                                    TachoDestination.CALENDAR -> stringResource(R.string.tab_calendar)
                                    TachoDestination.INSIGHTS -> stringResource(R.string.tab_insights)
                                }
                            )
                        },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TachoDestination.CARD.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(TachoDestination.CARD.route) { val cardViewModel = sharedCardViewModel()
                CardScreen(cardViewModel, attachedReader, onReadViaReader)
            }
            composable(TachoDestination.TIMELINE.route) { val cardViewModel = sharedCardViewModel()
                TimelineScreen(cardViewModel)
            }
            composable(TachoDestination.CALENDAR.route) { val cardViewModel = sharedCardViewModel()
                CalendarScreen(cardViewModel)
            }
            composable(TachoDestination.INSIGHTS.route) { val cardViewModel = sharedCardViewModel()
                InsightsScreen(cardViewModel)
            }
        }
    }
}
