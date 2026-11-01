package com.example.adaptivequeue.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.adaptivequeue.data.location.LocationState
import com.example.adaptivequeue.data.location.RouteState
import com.example.adaptivequeue.data.location.VenueLocation
import com.example.adaptivequeue.data.model.QueueToken
import com.example.adaptivequeue.data.repository.RecommendationType
import com.example.adaptivequeue.data.repository.ReturnRecommendationState
import com.example.adaptivequeue.data.repository.RoamingMode
import com.example.adaptivequeue.ui.viewmodel.VisitorViewModel

import java.util.Locale

@Composable
fun VisitorScreen(
    viewModel: VisitorViewModel = hiltViewModel()
) {
    val activeToken by viewModel.activeToken.collectAsState()
    val currentServing by viewModel.currentServingToken.collectAsState()
    val avgServiceTime by viewModel.averageServiceTimeMinutes.collectAsState()
    val joinedTokens by viewModel.joinedTokens.collectAsState()
    val locationState by viewModel.locationState.collectAsState()
    val venueLocation by viewModel.venueLocationState.collectAsState()
    val routeState by viewModel.routeState.collectAsState()
    val returnRecommendationState by viewModel.returnRecommendationState.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onPermissionResult(isGranted)
    }

    LaunchedEffect(Unit) {
        if (viewModel.hasLocationPermission()) {
            viewModel.checkPermissionAndStartLocationUpdates()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Visitor Portal", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        JoinQueueSection(onJoin = { viewModel.joinQueue(it) })

        if (joinedTokens.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "My Visitors", style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.fillMaxWidth()) {
                joinedTokens.forEach { token ->
                    val isSelected = activeToken?.id == token.id
                    Card(
                        onClick = { viewModel.selectToken(token.id) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                                             else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "${token.visitorName} (#${token.tokenNumber})", modifier = Modifier.weight(1f))
                            Text(text = token.status.name, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "No visitors currently in queue.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        }

        Spacer(modifier = Modifier.height(16.dp))

        LocationStatusSection(
            locationState = locationState,
            venueLocation = venueLocation,
            onRequestPermission = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            },
            onSetVenueLocation = {
                viewModel.setVenueLocationFromCurrentGps()
            },
            onSetCustomVenueLocation = { lat, lng ->
                viewModel.setCustomVenueLocation(lat, lng)
            },
            onClearVenueLocation = {
                viewModel.clearVenueLocation()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        WalkingRouteSection(
            routeState = routeState,
            hasVenue = venueLocation.isSet,
            onRefreshRoute = {
                viewModel.refreshRouteManually()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        RoamingBudgetSection(
            recommendationState = returnRecommendationState,
            onModeSelected = { viewModel.setRoamingMode(it) },
            onActivityDurationChanged = { viewModel.setPlannedActivityMinutes(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        AdaptiveReturnSection(
            recommendationState = returnRecommendationState
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (activeToken != null) {
            TokenStatusSection(
                token = activeToken!!,
                currentServing = currentServing,
                avgServiceTime = avgServiceTime,
                peopleAheadFlow = viewModel.getPeopleAhead(activeToken!!.tokenNumber),
                recommendationState = returnRecommendationState
            )
        }
    }
}

@Composable
fun RoamingBudgetSection(
    recommendationState: ReturnRecommendationState,
    onModeSelected: (RoamingMode) -> Unit,
    onActivityDurationChanged: (Double) -> Unit
) {
    var customMinutesInput by remember { mutableStateOf("") }
    var isCustomSelected by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Adaptive Roaming Budget",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Mode Selection
            Text(text = "Select Roaming Mode:", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isFree = recommendationState.roamingMode == RoamingMode.FREE_ROAMING
                val isPlanned = recommendationState.roamingMode == RoamingMode.PLANNED_ACTIVITY

                if (isFree) {
                    Button(
                        onClick = { onModeSelected(RoamingMode.FREE_ROAMING) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Free Roaming")
                    }
                } else {
                    OutlinedButton(
                        onClick = { onModeSelected(RoamingMode.FREE_ROAMING) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Free Roaming")
                    }
                }

                if (isPlanned) {
                    Button(
                        onClick = { onModeSelected(RoamingMode.PLANNED_ACTIVITY) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Planned Activity")
                    }
                } else {
                    OutlinedButton(
                        onClick = { onModeSelected(RoamingMode.PLANNED_ACTIVITY) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Planned Activity")
                    }
                }
            }

            // Mode B: Activity Duration Selector
            if (recommendationState.roamingMode == RoamingMode.PLANNED_ACTIVITY) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "Planned Activity Duration:", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(10.0, 15.0, 20.0, 30.0).forEach { mins ->
                        val isSelected = !isCustomSelected && recommendationState.plannedActivityMinutes == mins
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                isCustomSelected = false
                                onActivityDurationChanged(mins)
                            },
                            label = { Text("${mins.toInt()}m") }
                        )
                    }
                    FilterChip(
                        selected = isCustomSelected,
                        onClick = { isCustomSelected = true },
                        label = { Text("Custom") }
                    )
                }

                if (isCustomSelected) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customMinutesInput,
                        onValueChange = { input ->
                            customMinutesInput = input
                            val parsed = input.toDoubleOrNull()
                            if (parsed != null && parsed > 0) {
                                onActivityDurationChanged(parsed)
                            }
                        },
                        label = { Text("Duration (minutes)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Output / Budget Summary
            val budgetMins = recommendationState.oneWayRoamingBudgetMinutes
            val formattedBudget = if (budgetMins > 0) {
                String.format(Locale.US, "%.1f min", budgetMins)
            } else {
                "0 min"
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (recommendationState.hasInsufficientTimeForActivity)
                        MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Provisional One-Way Roaming Budget",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = formattedBudget,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Estimated walking time in one direction before turning back. (Safety buffer: ${recommendationState.safetyBufferMinutes.toInt()} min)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )

                    if (recommendationState.hasInsufficientTimeForActivity && recommendationState.activityWarningMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = recommendationState.activityWarningMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AdaptiveReturnSection(
    recommendationState: ReturnRecommendationState
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (recommendationState.recommendationType) {
                RecommendationType.WAITING_START_NOW -> MaterialTheme.colorScheme.errorContainer
                RecommendationType.WAITING_RECOMMENDATION -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Adaptive Return Recommendation",
                style = MaterialTheme.typography.titleMedium,
                color = when (recommendationState.recommendationType) {
                    RecommendationType.WAITING_START_NOW -> MaterialTheme.colorScheme.onErrorContainer
                    RecommendationType.WAITING_RECOMMENDATION -> MaterialTheme.colorScheme.onSecondaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (recommendationState.isDataValid && recommendationState.walkingDurationMinutes != null) {
                val queueWaitInt = Math.round(recommendationState.estimatedQueueWaitMinutes).toInt()
                val walkingTimeInt = Math.round(recommendationState.walkingDurationMinutes).toInt()
                val bufferInt = Math.round(recommendationState.safetyBufferMinutes).toInt()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "Queue Wait", style = MaterialTheme.typography.labelSmall)
                        Text(text = "$queueWaitInt min", style = MaterialTheme.typography.bodyMedium)
                    }
                    Column {
                        Text(text = "Walking Time", style = MaterialTheme.typography.labelSmall)
                        Text(text = "$walkingTimeInt min", style = MaterialTheme.typography.bodyMedium)
                    }
                    Column {
                        Text(text = "Safety Buffer", style = MaterialTheme.typography.labelSmall)
                        Text(text = "$bufferInt min", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text(
                text = recommendationState.displayMessage,
                style = MaterialTheme.typography.titleSmall,
                color = when (recommendationState.recommendationType) {
                    RecommendationType.WAITING_START_NOW -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
fun WalkingRouteSection(
    routeState: RouteState,
    hasVenue: Boolean,
    onRefreshRoute: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Walking Route (Visitor → Venue)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))

            when {
                !hasVenue -> {
                    Text(
                        text = "Set the venue location to calculate walking route.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                routeState.isLoading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Calculating walking route...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                else -> {
                    if (routeState.distanceMeters != null && routeState.durationSeconds != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Walking Distance",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = routeState.formattedDistance ?: "--",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Column {
                                Text(
                                    text = "Walking Time",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = routeState.formattedDuration ?: "--",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    } else if (routeState.errorMessage == null) {
                        Text(
                            text = "Press 'Refresh Walking Route' to calculate.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    if (routeState.errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = routeState.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (routeState.cooldownMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = routeState.cooldownMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onRefreshRoute,
                enabled = !routeState.isLoading && hasVenue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Refresh Walking Route")
            }
        }
    }
}

@Composable
fun LocationStatusSection(
    locationState: LocationState,
    venueLocation: VenueLocation,
    onRequestPermission: () -> Unit,
    onSetVenueLocation: () -> Unit,
    onSetCustomVenueLocation: (Double, Double) -> Unit,
    onClearVenueLocation: () -> Unit
) {
    var showCustomFields by remember { mutableStateOf(false) }
    var customLatInput by remember { mutableStateOf("") }
    var customLngInput by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "MY LOCATION",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))

            when {
                !locationState.hasPermission -> {
                    Text(
                        text = locationState.errorMessage ?: "Location permission is required.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onRequestPermission) {
                        Text("Grant Location Permission")
                    }
                }
                locationState.isAvailable && locationState.latitude != null && locationState.longitude != null -> {
                    Text(
                        text = "Latitude: ${String.format(Locale.US, "%.6f", locationState.latitude)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "Longitude: ${String.format(Locale.US, "%.6f", locationState.longitude)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "Accuracy: ${String.format(Locale.US, "%.1f", locationState.accuracy ?: 0f)} m",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                else -> {
                    Text(
                        text = locationState.errorMessage ?: "Acquiring GPS location...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "VENUE LOCATION",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))

            if (venueLocation.isSet && venueLocation.latitude != null && venueLocation.longitude != null) {
                Text(
                    text = "Latitude: ${String.format(Locale.US, "%.6f", venueLocation.latitude)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "Longitude: ${String.format(Locale.US, "%.6f", venueLocation.longitude)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            } else {
                Text(
                    text = "Venue location not set yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val canSetVenue = locationState.isAvailable && locationState.latitude != null && locationState.longitude != null
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSetVenueLocation,
                    enabled = canSetVenue,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Set GPS as Venue")
                }

                if (venueLocation.isSet) {
                    OutlinedButton(
                        onClick = onClearVenueLocation,
                        modifier = Modifier.weight(0.8f)
                    ) {
                        Text("Clear Venue")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = { showCustomFields = !showCustomFields },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(if (showCustomFields) "Hide Custom Venue Input" else "Enter Custom Venue Coordinates")
            }

            if (showCustomFields) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = customLatInput,
                    onValueChange = { customLatInput = it },
                    label = { Text("Venue Latitude (e.g. 12.980000)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = customLngInput,
                    onValueChange = { customLngInput = it },
                    label = { Text("Venue Longitude (e.g. 79.170000)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                val parsedLat = customLatInput.toDoubleOrNull()
                val parsedLng = customLngInput.toDoubleOrNull()
                val canSaveCustom = parsedLat != null && parsedLng != null

                Button(
                    onClick = {
                        if (parsedLat != null && parsedLng != null) {
                            onSetCustomVenueLocation(parsedLat, parsedLng)
                            showCustomFields = false
                        }
                    },
                    enabled = canSaveCustom,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Custom Venue Coordinates")
                }
            }
        }
    }
}

@Composable
fun JoinQueueSection(onJoin: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Your Name") },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = { if (name.isNotBlank()) onJoin(name) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Join Queue")
    }
}

@Composable
fun TokenStatusSection(
    token: QueueToken,
    currentServing: QueueToken?,
    avgServiceTime: Double,
    peopleAheadFlow: kotlinx.coroutines.flow.Flow<Int>,
    recommendationState: ReturnRecommendationState
) {
    val peopleAhead by peopleAheadFlow.collectAsState(initial = 0)
    val correctedWaitInt = Math.round(recommendationState.estimatedQueueWaitMinutes).toInt()

    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Visitor: ${token.visitorName}", style = MaterialTheme.typography.titleMedium)
            Text(text = "Your Token: #${token.tokenNumber}", style = MaterialTheme.typography.titleLarge)
            Text(text = "Status: ${token.status}")
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "People Ahead: $peopleAhead")
            Text(text = "Average Service Time: ${String.format(Locale.US, "%.1f", avgServiceTime)} min")
            Text(
                text = "Estimated Wait: $correctedWaitInt min",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Currently Serving", style = MaterialTheme.typography.titleMedium)
            if (currentServing != null) {
                Text(text = "Token #${currentServing.tokenNumber} (${currentServing.visitorName})")
            } else {
                Text(text = "None")
            }
        }
    }
}
