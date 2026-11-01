package com.example.adaptivequeue

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.activity.compose.LocalActivity
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.adaptivequeue.ui.screens.HomeScreen
import com.example.adaptivequeue.ui.screens.LoginScreen
import com.example.adaptivequeue.ui.screens.StaffScreen
import com.example.adaptivequeue.ui.screens.VisitorScreen
import com.example.adaptivequeue.ui.theme.AdaptiveQueueTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AdaptiveQueueTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    QueueApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun QueueApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier
    ) {
        composable("home") {
            HomeScreen(
                onNavigateToLogin = { navController.navigate("login") },
                onNavigateToVisitor = { navController.navigate("visitor") },
                onNavigateToStaff = { navController.navigate("staff") }
            )
        }
        composable("login") {
            LoginScreen(
                onLoginSuccess = { navController.navigate("home") }
            )
        }
        composable("visitor") {
            val activity = LocalActivity.current as ComponentActivity
            val viewModel: com.example.adaptivequeue.ui.viewmodel.VisitorViewModel = 
                androidx.hilt.navigation.compose.hiltViewModel(activity)
            VisitorScreen(viewModel = viewModel)
        }
        composable("staff") {
            StaffScreen()
        }
    }
}
