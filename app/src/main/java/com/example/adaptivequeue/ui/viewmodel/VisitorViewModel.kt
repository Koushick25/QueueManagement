package com.example.adaptivequeue.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.adaptivequeue.data.location.LocationState
import com.example.adaptivequeue.data.location.LocationTracker
import com.example.adaptivequeue.data.location.OrsRoutingRepository
import com.example.adaptivequeue.data.location.RouteState
import com.example.adaptivequeue.data.location.VenueLocation
import com.example.adaptivequeue.data.location.VenueLocationStore
import com.example.adaptivequeue.data.model.QueueToken
import com.example.adaptivequeue.data.repository.QueueEstimationEngine
import com.example.adaptivequeue.data.repository.QueueRepository
import com.example.adaptivequeue.data.repository.ReturnRecommendationState
import com.example.adaptivequeue.data.repository.RoamingMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VisitorViewModel @Inject constructor(
    private val repository: QueueRepository,
    private val locationTracker: LocationTracker,
    private val venueLocationStore: VenueLocationStore,
    private val routingRepository: OrsRoutingRepository,
    private val estimationEngine: QueueEstimationEngine
) : ViewModel() {

    private val _activeTokenId = MutableStateFlow<Int?>(null)
    
    val activeToken: StateFlow<QueueToken?> = _activeTokenId.flatMapLatest { id ->
        if (id == null) flowOf(null)
        else repository.getTokenById(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val joinedTokens: StateFlow<List<QueueToken>> = repository.getActiveTokens()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentServingToken = repository.getCurrentServingToken()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val averageServiceTimeMinutes = repository.getAverageServiceTimeMinutes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 7.0)

    private val _locationState = MutableStateFlow(LocationState())
    val locationState: StateFlow<LocationState> = _locationState.asStateFlow()

    val venueLocationState: StateFlow<VenueLocation> = venueLocationStore.venueLocation

    private val _routeState = MutableStateFlow(RouteState())
    val routeState: StateFlow<RouteState> = _routeState.asStateFlow()

    private val _roamingMode = MutableStateFlow(RoamingMode.FREE_ROAMING)
    val roamingMode: StateFlow<RoamingMode> = _roamingMode.asStateFlow()

    private val _plannedActivityMinutes = MutableStateFlow(15.0)
    val plannedActivityMinutes: StateFlow<Double> = _plannedActivityMinutes.asStateFlow()

    private val _tickerFlow = MutableStateFlow(System.currentTimeMillis())

    private data class CombineState(
        val active: QueueToken?,
        val serving: QueueToken?,
        val avgTime: Double,
        val route: RouteState,
        val mode: RoamingMode,
        val activityMins: Double,
        val tickerTime: Long
    )

    val returnRecommendationState: StateFlow<ReturnRecommendationState> = combine(
        activeToken,
        currentServingToken,
        averageServiceTimeMinutes,
        routeState,
        roamingMode,
        plannedActivityMinutes,
        _tickerFlow
    ) { flows: Array<Any?> ->
        CombineState(
            active = flows[0] as QueueToken?,
            serving = flows[1] as QueueToken?,
            avgTime = flows[2] as Double,
            route = flows[3] as RouteState,
            mode = flows[4] as RoamingMode,
            activityMins = flows[5] as Double,
            tickerTime = flows[6] as Long
        )
    }.flatMapLatest { state ->
        val active = state.active
        if (active == null) {
            flowOf(
                estimationEngine.calculateReturnRecommendation(
                    selectedToken = null,
                    peopleAhead = 0,
                    currentServingToken = state.serving,
                    averageServiceTimeMinutes = state.avgTime,
                    routeState = state.route,
                    roamingMode = state.mode,
                    plannedActivityMinutes = state.activityMins,
                    currentTimeMillis = state.tickerTime
                )
            )
        } else {
            repository.getPeopleAhead(active.tokenNumber).map { peopleAhead ->
                estimationEngine.calculateReturnRecommendation(
                    selectedToken = active,
                    peopleAhead = peopleAhead,
                    currentServingToken = state.serving,
                    averageServiceTimeMinutes = state.avgTime,
                    routeState = state.route,
                    roamingMode = state.mode,
                    plannedActivityMinutes = state.activityMins,
                    currentTimeMillis = state.tickerTime
                )
            }
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ReturnRecommendationState()
    )

    fun setRoamingMode(mode: RoamingMode) {
        _roamingMode.value = mode
    }

    fun setPlannedActivityMinutes(minutes: Double) {
        _plannedActivityMinutes.value = minutes
    }

    private var lastRouteFetchTime = 0L
    private var lastRouteVisitorLat: Double? = null
    private var lastRouteVisitorLng: Double? = null
    private var isRouteRequestInFlight = false
    private var currentRouteRequestId = 0L

    private val MANUAL_COOLDOWN_MS = 10_000L // 10 seconds for manual button tap
    private val AUTO_COOLDOWN_MS = 15_000L   // 15 seconds for automatic updates
    private val MIN_MOVEMENT_METERS = 20f   // 20 meters movement threshold

    private var locationJob: Job? = null

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(30000L) // 30 seconds lightweight ticker
                _tickerFlow.value = System.currentTimeMillis()
            }
        }
        viewModelScope.launch {
            venueLocationState.collect { venue ->
                if (venue.isSet) {
                    evaluateAutoRouteFetch()
                }
            }
        }
    }

    fun setVenueLocationFromCurrentGps() {
        val currentLoc = _locationState.value
        val lat = currentLoc.latitude
        val lng = currentLoc.longitude
        if (lat != null && lng != null) {
            venueLocationStore.saveVenueLocation(lat, lng)
            lastRouteVisitorLat = null
            lastRouteVisitorLng = null
            evaluateAutoRouteFetch()
        }
    }

    fun setCustomVenueLocation(lat: Double, lng: Double) {
        venueLocationStore.saveVenueLocation(lat, lng)
        lastRouteVisitorLat = null
        lastRouteVisitorLng = null
        evaluateAutoRouteFetch()
    }

    fun clearVenueLocation() {
        venueLocationStore.clearVenueLocation()
        _routeState.value = RouteState()
    }

    fun hasLocationPermission(): Boolean = locationTracker.hasLocationPermission()

    fun checkPermissionAndStartLocationUpdates() {
        if (locationTracker.hasLocationPermission()) {
            locationJob?.cancel()
            locationJob = viewModelScope.launch {
                locationTracker.getLocationUpdates().collect { state ->
                    _locationState.value = state
                    evaluateAutoRouteFetch()
                }
            }
        } else {
            _locationState.value = LocationState(
                hasPermission = false,
                errorMessage = "Location permission required"
            )
        }
    }

    fun onPermissionResult(isGranted: Boolean) {
        if (isGranted) {
            checkPermissionAndStartLocationUpdates()
        } else {
            _locationState.value = LocationState(
                hasPermission = false,
                errorMessage = "Location permission denied"
            )
        }
    }

    fun refreshRouteManually() {
        requestRouteFetch(isManual = true)
    }

    private fun evaluateAutoRouteFetch() {
        requestRouteFetch(isManual = false)
    }

    private fun requestRouteFetch(isManual: Boolean) {
        viewModelScope.launch {
            val visitorLoc = _locationState.value
            val venueLoc = venueLocationState.value

            val visitorLat = visitorLoc.latitude
            val visitorLng = visitorLoc.longitude
            val venueLat = venueLoc.latitude
            val venueLng = venueLoc.longitude

            if (visitorLat == null || visitorLng == null || venueLat == null || venueLng == null) {
                if (isManual) {
                    _routeState.value = _routeState.value.copy(
                        cooldownMessage = "Set the venue location to calculate walking route."
                    )
                }
                return@launch
            }

            val currentTime = System.currentTimeMillis()
            val elapsedTime = currentTime - lastRouteFetchTime

            if (isManual) {
                if (isRouteRequestInFlight) {
                    android.util.Log.d("AdaptiveQueueRoute", "Manual request skipped: Another route request is already in flight")
                    return@launch
                }
                if (elapsedTime < MANUAL_COOLDOWN_MS && lastRouteFetchTime > 0) {
                    val remainingSec = ((MANUAL_COOLDOWN_MS - elapsedTime) / 1000) + 1
                    android.util.Log.d("AdaptiveQueueRoute", "Manual request skipped: Cooldown active ($remainingSec s remaining)")
                    _routeState.value = _routeState.value.copy(
                        cooldownMessage = "Please wait $remainingSec sec before refreshing the route."
                    )
                    return@launch
                }
            } else {
                if (isRouteRequestInFlight) {
                    android.util.Log.d("AdaptiveQueueRoute", "Auto request skipped: Request in flight")
                    return@launch
                }

                if (elapsedTime < AUTO_COOLDOWN_MS && lastRouteFetchTime > 0) {
                    android.util.Log.d("AdaptiveQueueRoute", "Auto request skipped: Cooldown active (${(AUTO_COOLDOWN_MS - elapsedTime) / 1000} s remaining)")
                    return@launch
                }

                if (lastRouteVisitorLat != null && lastRouteVisitorLng != null) {
                    val results = FloatArray(1)
                    android.location.Location.distanceBetween(
                        lastRouteVisitorLat!!, lastRouteVisitorLng!!,
                        visitorLat, visitorLng,
                        results
                    )
                    val movedMeters = results[0]
                    if (movedMeters < MIN_MOVEMENT_METERS) {
                        android.util.Log.d("AdaptiveQueueRoute", "Auto request skipped: Moved $movedMeters m < threshold $MIN_MOVEMENT_METERS m")
                        return@launch
                    } else {
                        android.util.Log.d("AdaptiveQueueRoute", "Auto request triggered: Moved $movedMeters m >= threshold $MIN_MOVEMENT_METERS m")
                    }
                }
            }

            val requestId = ++currentRouteRequestId
            isRouteRequestInFlight = true
            lastRouteFetchTime = currentTime

            android.util.Log.d("AdaptiveQueueRoute", "Executing ORS Request #$requestId (Manual=$isManual): Visitor=($visitorLat, $visitorLng), Venue=($venueLat, $venueLng)")

            _routeState.value = _routeState.value.copy(
                isLoading = true,
                errorMessage = null,
                cooldownMessage = null
            )

            val result = routingRepository.fetchWalkingRoute(visitorLat, visitorLng, venueLat, venueLng)
            isRouteRequestInFlight = false

            if (requestId != currentRouteRequestId) {
                android.util.Log.w("AdaptiveQueueRoute", "Discarded stale route response #$requestId (current: $currentRouteRequestId)")
                return@launch
            }

            result.fold(
                onSuccess = { (distance, duration) ->
                    lastRouteVisitorLat = visitorLat
                    lastRouteVisitorLng = visitorLng

                    val newState = RouteState(
                        distanceMeters = distance,
                        durationSeconds = duration,
                        isLoading = false,
                        errorMessage = null,
                        cooldownMessage = null,
                        lastUpdateTime = lastRouteFetchTime
                    )
                    _routeState.value = newState
                    android.util.Log.d("AdaptiveQueueRoute", "Updated Route State: dist=${newState.formattedDistance}, duration=${newState.formattedDuration}")
                },
                onFailure = { error ->
                    val errorText = error.localizedMessage ?: "Route calculation failed"
                    android.util.Log.e("AdaptiveQueueRoute", "Route request #$requestId failed: $errorText")
                    _routeState.value = _routeState.value.copy(
                        isLoading = false,
                        errorMessage = errorText
                    )
                }
            )
        }
    }

    fun joinQueue(name: String) {
        viewModelScope.launch {
            val token = repository.generateToken(name)
            _activeTokenId.value = token.id
        }
    }

    fun selectToken(id: Int) {
        _activeTokenId.value = id
    }

    fun getPeopleAhead(tokenNumber: Int): Flow<Int> {
        return repository.getPeopleAhead(tokenNumber)
    }
}
