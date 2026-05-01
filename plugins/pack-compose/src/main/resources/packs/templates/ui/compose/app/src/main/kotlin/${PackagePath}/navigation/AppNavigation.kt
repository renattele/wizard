package ${Package}.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
${FeatureComposableImports}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = "${StartFeatureRoute}",
    ) {
${FeatureComposableDestinations}
    }
}
