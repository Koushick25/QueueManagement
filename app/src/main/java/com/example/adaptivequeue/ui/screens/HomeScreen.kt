package com.example.adaptivequeue.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToVisitor: () -> Unit,
    onNavigateToStaff: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Adaptive Queue System")
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onNavigateToLogin) {
            Text(text = "Login")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateToVisitor) {
            Text(text = "Visitor Portal")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateToStaff) {
            Text(text = "Staff Dashboard")
        }
    }
}
