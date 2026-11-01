package com.example.adaptivequeue.data.repository

import com.example.adaptivequeue.data.location.RouteState
import com.example.adaptivequeue.data.model.QueueToken
import com.example.adaptivequeue.data.model.ServiceRecord
import com.example.adaptivequeue.data.model.TokenStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

enum class RecommendationType {
    NO_TOKEN,
    SERVING,
    COMPLETED,
    MISSED,
    WAITING_RECOMMENDATION,
    WAITING_START_NOW,
    ROUTE_UNAVAILABLE
}

enum class RoamingMode {
    FREE_ROAMING,   // Mode A
    PLANNED_ACTIVITY // Mode B
}

data class RoamingBudgetState(
    val mode: RoamingMode = RoamingMode.FREE_ROAMING,
    val plannedActivityMinutes: Double = 15.0,
    val safetyBufferMinutes: Double = 5.0,
    val oneWayRoamingBudgetMinutes: Double = 0.0,
    val hasInsufficientTimeForActivity: Boolean = false,
    val activityWarningMessage: String? = null
)

data class ReturnRecommendationState(
    val estimatedQueueWaitMinutes: Double = 0.0,
    val walkingDurationMinutes: Double? = null,
    val safetyBufferMinutes: Double = 5.0,
    val minutesUntilReturn: Double? = null,
    val recommendationType: RecommendationType = RecommendationType.NO_TOKEN,
    val displayMessage: String = "Select a visitor to view return recommendation.",
    val isDataValid: Boolean = false,
    val roamingMode: RoamingMode = RoamingMode.FREE_ROAMING,
    val plannedActivityMinutes: Double = 15.0,
    val oneWayRoamingBudgetMinutes: Double = 0.0,
    val hasInsufficientTimeForActivity: Boolean = false,
    val activityWarningMessage: String? = null
)

@Singleton
class QueueEstimationEngine @Inject constructor() {
    
    private val DEFAULT_SERVICE_TIME_MINUTES = 7.0
    private val MAX_SAMPLES = 5
    val DEFAULT_SAFETY_BUFFER_MINUTES = 5.0

    fun calculateAverageServiceTimeMinutes(records: List<ServiceRecord>): Double {
        if (records.isEmpty()) return DEFAULT_SERVICE_TIME_MINUTES
        
        val sumMillis = records.take(MAX_SAMPLES).sumOf { it.durationMillis }
        val averageMillis = sumMillis.toDouble() / minOf(records.size, MAX_SAMPLES)
        return averageMillis / (1000.0 * 60.0) // Convert to minutes
    }

    fun estimateWaitTime(peopleAhead: Int, averageServiceTimeMinutes: Double): Int {
        return (peopleAhead * averageServiceTimeMinutes).roundToInt()
    }

    fun calculateRemainingServiceMinutes(
        currentServingToken: QueueToken?,
        averageServiceTimeMinutes: Double,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): Double {
        if (currentServingToken == null || currentServingToken.serviceStartTime == null) {
            return 0.0
        }
        val elapsedMinutes = max(0.0, (currentTimeMillis - currentServingToken.serviceStartTime) / 60000.0)
        return max(0.0, averageServiceTimeMinutes - elapsedMinutes)
    }

    fun calculateCorrectedWaitMinutes(
        tokenStatus: TokenStatus,
        peopleAhead: Int,
        averageServiceTimeMinutes: Double,
        currentServingToken: QueueToken?,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): Double {
        return when (tokenStatus) {
            TokenStatus.SERVING, TokenStatus.COMPLETED, TokenStatus.MISSED -> 0.0
            TokenStatus.WAITING -> {
                val remainingCurrentService = calculateRemainingServiceMinutes(
                    currentServingToken,
                    averageServiceTimeMinutes,
                    currentTimeMillis
                )
                remainingCurrentService + (peopleAhead * averageServiceTimeMinutes)
            }
        }
    }

    fun calculateRoamingBudget(
        estimatedQueueWaitMinutes: Double,
        mode: RoamingMode,
        plannedActivityMinutes: Double,
        safetyBufferMinutes: Double = DEFAULT_SAFETY_BUFFER_MINUTES
    ): RoamingBudgetState {
        return when (mode) {
            RoamingMode.FREE_ROAMING -> {
                val availableRoamingTime = max(0.0, estimatedQueueWaitMinutes - safetyBufferMinutes)
                val oneWayBudget = availableRoamingTime / 2.0
                RoamingBudgetState(
                    mode = mode,
                    plannedActivityMinutes = plannedActivityMinutes,
                    safetyBufferMinutes = safetyBufferMinutes,
                    oneWayRoamingBudgetMinutes = oneWayBudget,
                    hasInsufficientTimeForActivity = false,
                    activityWarningMessage = null
                )
            }
            RoamingMode.PLANNED_ACTIVITY -> {
                val availableTravelTime = max(0.0, estimatedQueueWaitMinutes - plannedActivityMinutes - safetyBufferMinutes)
                val oneWayBudget = availableTravelTime / 2.0
                val insufficient = (estimatedQueueWaitMinutes - safetyBufferMinutes) < plannedActivityMinutes
                val warningMsg = if (insufficient) {
                    val roundedActivity = Math.round(plannedActivityMinutes).toInt()
                    val roundedWait = Math.round(estimatedQueueWaitMinutes).toInt()
                    "Planned activity ($roundedActivity min) may not fit within current estimated waiting time ($roundedWait min)."
                } else null

                RoamingBudgetState(
                    mode = mode,
                    plannedActivityMinutes = plannedActivityMinutes,
                    safetyBufferMinutes = safetyBufferMinutes,
                    oneWayRoamingBudgetMinutes = oneWayBudget,
                    hasInsufficientTimeForActivity = insufficient,
                    activityWarningMessage = warningMsg
                )
            }
        }
    }

    fun calculateReturnRecommendation(
        selectedToken: QueueToken?,
        peopleAhead: Int,
        currentServingToken: QueueToken?,
        averageServiceTimeMinutes: Double,
        routeState: RouteState,
        roamingMode: RoamingMode = RoamingMode.FREE_ROAMING,
        plannedActivityMinutes: Double = 15.0,
        safetyBufferMinutes: Double = DEFAULT_SAFETY_BUFFER_MINUTES,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): ReturnRecommendationState {
        if (selectedToken == null) {
            return ReturnRecommendationState(
                recommendationType = RecommendationType.NO_TOKEN,
                displayMessage = "Select a visitor to view return recommendation."
            )
        }

        val queueWaitMinutes = calculateCorrectedWaitMinutes(
            selectedToken.status,
            peopleAhead,
            averageServiceTimeMinutes,
            currentServingToken,
            currentTimeMillis
        )

        val roamingBudget = calculateRoamingBudget(
            estimatedQueueWaitMinutes = queueWaitMinutes,
            mode = roamingMode,
            plannedActivityMinutes = plannedActivityMinutes,
            safetyBufferMinutes = safetyBufferMinutes
        )

        return when (selectedToken.status) {
            TokenStatus.SERVING -> {
                ReturnRecommendationState(
                    estimatedQueueWaitMinutes = 0.0,
                    safetyBufferMinutes = safetyBufferMinutes,
                    recommendationType = RecommendationType.SERVING,
                    displayMessage = "Your turn is being served now.",
                    isDataValid = true,
                    roamingMode = roamingMode,
                    plannedActivityMinutes = plannedActivityMinutes,
                    oneWayRoamingBudgetMinutes = 0.0,
                    hasInsufficientTimeForActivity = false,
                    activityWarningMessage = null
                )
            }
            TokenStatus.COMPLETED -> {
                ReturnRecommendationState(
                    estimatedQueueWaitMinutes = 0.0,
                    safetyBufferMinutes = safetyBufferMinutes,
                    recommendationType = RecommendationType.COMPLETED,
                    displayMessage = "Service completed.",
                    isDataValid = true,
                    roamingMode = roamingMode,
                    plannedActivityMinutes = plannedActivityMinutes,
                    oneWayRoamingBudgetMinutes = 0.0,
                    hasInsufficientTimeForActivity = false,
                    activityWarningMessage = null
                )
            }
            TokenStatus.MISSED -> {
                ReturnRecommendationState(
                    estimatedQueueWaitMinutes = 0.0,
                    safetyBufferMinutes = safetyBufferMinutes,
                    recommendationType = RecommendationType.MISSED,
                    displayMessage = "Your turn was missed.",
                    isDataValid = true,
                    roamingMode = roamingMode,
                    plannedActivityMinutes = plannedActivityMinutes,
                    oneWayRoamingBudgetMinutes = 0.0,
                    hasInsufficientTimeForActivity = false,
                    activityWarningMessage = null
                )
            }
            TokenStatus.WAITING -> {
                val durationSeconds = routeState.durationSeconds
                val hasValidRoute = durationSeconds != null && routeState.distanceMeters != null

                val walkingDurationMinutes = if (hasValidRoute) durationSeconds / 60.0 else null
                val rawMinutesUntilReturn = if (walkingDurationMinutes != null) {
                    queueWaitMinutes - walkingDurationMinutes - safetyBufferMinutes
                } else null

                val minutesUntilReturn = if (rawMinutesUntilReturn != null) max(0.0, rawMinutesUntilReturn) else null

                val (type, message) = when {
                    !hasValidRoute -> {
                        RecommendationType.ROUTE_UNAVAILABLE to "Walking time is unavailable. Calculate a walking route to get a return recommendation."
                    }
                    rawMinutesUntilReturn!! > 0 -> {
                        val roundedMins = Math.round(rawMinutesUntilReturn).toInt()
                        val minText = if (roundedMins == 1) "minute" else "minutes"
                        RecommendationType.WAITING_RECOMMENDATION to "Start returning in approximately $roundedMins $minText."
                    }
                    else -> {
                        RecommendationType.WAITING_START_NOW to "Start returning now."
                    }
                }

                ReturnRecommendationState(
                    estimatedQueueWaitMinutes = queueWaitMinutes,
                    walkingDurationMinutes = walkingDurationMinutes,
                    safetyBufferMinutes = safetyBufferMinutes,
                    minutesUntilReturn = minutesUntilReturn,
                    recommendationType = type,
                    displayMessage = message,
                    isDataValid = hasValidRoute,
                    roamingMode = roamingMode,
                    plannedActivityMinutes = plannedActivityMinutes,
                    oneWayRoamingBudgetMinutes = roamingBudget.oneWayRoamingBudgetMinutes,
                    hasInsufficientTimeForActivity = roamingBudget.hasInsufficientTimeForActivity,
                    activityWarningMessage = roamingBudget.activityWarningMessage
                )
            }
        }
    }
}
