package com.example.adaptivequeue.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adaptivequeue.data.model.QueueToken
import com.example.adaptivequeue.ui.viewmodel.StaffViewModel

import java.util.Locale

@Composable
fun StaffScreen(
    viewModel: StaffViewModel = hiltViewModel()
) {
    val waitingTokens by viewModel.waitingTokens.collectAsState()
    val currentServing by viewModel.currentServingToken.collectAsState()
    val avgServiceTime by viewModel.averageServiceTimeMinutes.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text(text = "Staff Dashboard", style = MaterialTheme.typography.headlineMedium)
        Text(text = "Avg Service Time: ${String.format(Locale.US, "%.1f", avgServiceTime)} min", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Currently Serving", style = MaterialTheme.typography.titleMedium)
                val serving = currentServing
                if (serving != null) {
                    Text(text = "Token #${serving.tokenNumber} - ${serving.visitorName}", style = MaterialTheme.typography.headlineSmall)
                    
                    // Show current duration
                    val startTime = serving.serviceStartTime ?: System.currentTimeMillis()
                    val durationMins = (System.currentTimeMillis() - startTime) / (1000 * 60)
                    Text(text = "Current Duration: $durationMins min", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(text = "No one being served", style = MaterialTheme.typography.headlineSmall)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.serveNext() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Serve Next")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        Text(text = "Demo/Simulation Mode", style = MaterialTheme.typography.titleSmall)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = { viewModel.addMockServiceRecord(5) }, modifier = Modifier.weight(1f).padding(4.dp)) { Text("Add 5m") }
            Button(onClick = { viewModel.addMockServiceRecord(10) }, modifier = Modifier.weight(1f).padding(4.dp)) { Text("Add 10m") }
            Button(onClick = { viewModel.addMockServiceRecord(15) }, modifier = Modifier.weight(1f).padding(4.dp)) { Text("Add 15m") }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Waiting List (${waitingTokens.size})", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(waitingTokens) { token ->
                TokenItem(token)
            }
        }

        Button(
            onClick = { viewModel.clearQueue() },
            modifier = Modifier.align(Alignment.CenterHorizontally),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Clear Queue")
        }
    }
}

@Composable
fun TokenItem(token: QueueToken) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "#${token.tokenNumber}", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = token.visitorName)
        }
    }
}
