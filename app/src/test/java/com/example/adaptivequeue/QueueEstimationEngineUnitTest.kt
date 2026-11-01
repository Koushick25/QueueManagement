package com.example.adaptivequeue

import com.example.adaptivequeue.data.location.RouteState
import com.example.adaptivequeue.data.model.QueueToken
import com.example.adaptivequeue.data.model.ServiceRecord
import com.example.adaptivequeue.data.model.TokenStatus
import com.example.adaptivequeue.data.repository.QueueEstimationEngine
import com.example.adaptivequeue.data.repository.RecommendationType
import com.example.adaptivequeue.data.repository.RoamingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueEstimationEngineUnitTest {

    private val engine = QueueEstimationEngine()

    // 1. Test default service time when there are no records
    @Test
    fun emptyServiceRecordsTest() {
        val result = engine.calculateAverageServiceTimeMinutes(emptyList())
        assertEquals(7.0, result, 0.001)
    }

    // 2. Test average service time with one record
    @Test
    fun singleServiceRecordTest() {
        val records = listOf(
            ServiceRecord(tokenNumber = 1, durationMillis = 120000L)
        )

        val result = engine.calculateAverageServiceTimeMinutes(records)
        assertEquals(2.0, result, 0.001)
    }

    // 3. Test average service time with two records
    @Test
    fun averageServiceTimeTest() {
        val records = listOf(
            ServiceRecord(tokenNumber = 1, durationMillis = 120000L),
            ServiceRecord(tokenNumber = 2, durationMillis = 240000L)
        )

        val result = engine.calculateAverageServiceTimeMinutes(records)
        assertEquals(3.0, result, 0.001)
    }

    // 4. Test that only the first five records are used
    @Test
    fun maximumFiveServiceRecordsTest() {
        val records = listOf(
            ServiceRecord(tokenNumber = 1, durationMillis = 60000L),
            ServiceRecord(tokenNumber = 2, durationMillis = 120000L),
            ServiceRecord(tokenNumber = 3, durationMillis = 180000L),
            ServiceRecord(tokenNumber = 4, durationMillis = 240000L),
            ServiceRecord(tokenNumber = 5, durationMillis = 300000L),
            ServiceRecord(tokenNumber = 6, durationMillis = 600000L)
        )

        val result = engine.calculateAverageServiceTimeMinutes(records)
        assertEquals(3.0, result, 0.001)
    }

    // 5. Test queue waiting-time calculation
    @Test
    fun normalWaitTimeTest() {
        val result = engine.estimateWaitTime(3, 4.0)
        assertEquals(12, result)
    }

    // 6. Test waiting time when nobody is ahead
    @Test
    fun zeroPeopleAheadTest() {
        val result = engine.estimateWaitTime(0, 5.0)
        assertEquals(0, result)
    }

    // 7. Test rounding of estimated waiting time
    @Test
    fun roundedWaitTimeTest() {
        val result = engine.estimateWaitTime(1, 2.6)
        assertEquals(3, result)
    }

    // 8. Test remaining service time without a serving token
    @Test
    fun noServingTokenTest() {
        val result = engine.calculateRemainingServiceMinutes(
            null, 7.0, 600000L
        )

        assertEquals(0.0, result, 0.001)
    }

    // 9. Test serving token without a start time
    @Test
    fun missingServiceStartTimeTest() {
        val token = QueueToken(
            tokenNumber = 10,
            status = TokenStatus.SERVING,
            serviceStartTime = null
        )

        val result = engine.calculateRemainingServiceMinutes(
            token, 7.0, 600000L
        )

        assertEquals(0.0, result, 0.001)
    }

    // 10. Test remaining time after two minutes of service
    @Test
    fun remainingServiceTimeTest() {
        val token = QueueToken(
            tokenNumber = 10,
            status = TokenStatus.SERVING,
            serviceStartTime = 480000L
        )

        val result = engine.calculateRemainingServiceMinutes(
            token, 7.0, 600000L
        )

        assertEquals(5.0, result, 0.001)
    }

    // 11. Test when the expected service duration has elapsed
    @Test
    fun completedServiceDurationTest() {
        val token = QueueToken(
            tokenNumber = 10,
            status = TokenStatus.SERVING,
            serviceStartTime = 0L
        )

        val result = engine.calculateRemainingServiceMinutes(
            token, 7.0, 600000L
        )

        assertEquals(0.0, result, 0.001)
    }

    // 12. Test a service start time in the future
    @Test
    fun futureServiceStartTimeTest() {
        val token = QueueToken(
            tokenNumber = 10,
            status = TokenStatus.SERVING,
            serviceStartTime = 700000L
        )

        val result = engine.calculateRemainingServiceMinutes(
            token, 7.0, 600000L
        )

        assertEquals(7.0, result, 0.001)
    }

    // 13. Test corrected wait for a serving token
    @Test
    fun servingTokenWaitTest() {
        val result = engine.calculateCorrectedWaitMinutes(
            TokenStatus.SERVING, 3, 5.0, null, 600000L
        )

        assertEquals(0.0, result, 0.001)
    }

    // 14. Test corrected wait for a completed token
    @Test
    fun completedTokenWaitTest() {
        val result = engine.calculateCorrectedWaitMinutes(
            TokenStatus.COMPLETED, 3, 5.0, null, 600000L
        )

        assertEquals(0.0, result, 0.001)
    }

    // 15. Test corrected wait for a missed token
    @Test
    fun missedTokenWaitTest() {
        val result = engine.calculateCorrectedWaitMinutes(
            TokenStatus.MISSED, 3, 5.0, null, 600000L
        )

        assertEquals(0.0, result, 0.001)
    }

    // 16. Test waiting time without a current serving token
    @Test
    fun waitingTokenWithoutServingTokenTest() {
        val result = engine.calculateCorrectedWaitMinutes(
            TokenStatus.WAITING, 3, 5.0, null, 600000L
        )

        assertEquals(15.0, result, 0.001)
    }

    // 17. Test that remaining service time is included
    @Test
    fun correctedWaitIncludesRemainingServiceTest() {
        val servingToken = QueueToken(
            tokenNumber = 10,
            status = TokenStatus.SERVING,
            serviceStartTime = 480000L
        )

        val result = engine.calculateCorrectedWaitMinutes(
            TokenStatus.WAITING,
            2,
            7.0,
            servingToken,
            600000L
        )

        assertEquals(19.0, result, 0.001)
    }

    // 18. Test free-roaming budget calculation
    @Test
    fun freeRoamingBudgetTest() {
        val result = engine.calculateRoamingBudget(
            25.0,
            RoamingMode.FREE_ROAMING,
            15.0,
            5.0
        )

        assertEquals(10.0, result.oneWayRoamingBudgetMinutes, 0.001)
        assertFalse(result.hasInsufficientTimeForActivity)
    }

    // 19. Test when waiting time is less than the safety buffer
    @Test
    fun insufficientFreeRoamingTimeTest() {
        val result = engine.calculateRoamingBudget(
            3.0,
            RoamingMode.FREE_ROAMING,
            10.0,
            5.0
        )

        assertEquals(0.0, result.oneWayRoamingBudgetMinutes, 0.001)
    }

    // 20. Test planned activity when sufficient time is available
    @Test
    fun plannedActivityFitsTest() {
        val result = engine.calculateRoamingBudget(
            40.0,
            RoamingMode.PLANNED_ACTIVITY,
            15.0,
            5.0
        )

        assertEquals(10.0, result.oneWayRoamingBudgetMinutes, 0.001)
        assertFalse(result.hasInsufficientTimeForActivity)
        assertNull(result.activityWarningMessage)
    }

    // 21. Test planned activity when insufficient time is available
    @Test
    fun plannedActivityInsufficientTimeTest() {
        val result = engine.calculateRoamingBudget(
            15.0,
            RoamingMode.PLANNED_ACTIVITY,
            15.0,
            5.0
        )

        assertEquals(0.0, result.oneWayRoamingBudgetMinutes, 0.001)
        assertTrue(result.hasInsufficientTimeForActivity)
        assertTrue(result.activityWarningMessage != null)
    }

    // 22. Test recommendation when no token is selected
    @Test
    fun noTokenRecommendationTest() {
        val result = engine.calculateReturnRecommendation(
            null, 0, null, 7.0, RouteState()
        )

        assertEquals(RecommendationType.NO_TOKEN, result.recommendationType)
        assertFalse(result.isDataValid)
    }

    // 23. Test recommendation for a serving token
    @Test
    fun servingRecommendationTest() {
        val token = QueueToken(
            tokenNumber = 5,
            status = TokenStatus.SERVING
        )

        val result = engine.calculateReturnRecommendation(
            token, 0, token, 7.0, RouteState()
        )

        assertEquals(RecommendationType.SERVING, result.recommendationType)
        assertTrue(result.isDataValid)
    }

    // 24. Test recommendation for a completed token
    @Test
    fun completedRecommendationTest() {
        val token = QueueToken(
            tokenNumber = 5,
            status = TokenStatus.COMPLETED
        )

        val result = engine.calculateReturnRecommendation(
            token, 0, null, 7.0, RouteState()
        )

        assertEquals(RecommendationType.COMPLETED, result.recommendationType)
        assertTrue(result.isDataValid)
    }

    // 25. Test recommendation for a missed token
    @Test
    fun missedRecommendationTest() {
        val token = QueueToken(
            tokenNumber = 5,
            status = TokenStatus.MISSED
        )

        val result = engine.calculateReturnRecommendation(
            token, 0, null, 7.0, RouteState()
        )

        assertEquals(RecommendationType.MISSED, result.recommendationType)
        assertTrue(result.isDataValid)
    }

    // 26. Test waiting token without route information
    @Test
    fun routeUnavailableRecommendationTest() {
        val token = QueueToken(
            tokenNumber = 8,
            status = TokenStatus.WAITING
        )

        val result = engine.calculateReturnRecommendation(
            token, 4, null, 5.0, RouteState()
        )

        assertEquals(
            RecommendationType.ROUTE_UNAVAILABLE,
            result.recommendationType
        )

        assertFalse(result.isDataValid)
        assertNull(result.walkingDurationMinutes)
    }

    // 27. Test recommendation when the visitor has time to return later
    @Test
    fun waitingRecommendationTest() {
        val token = QueueToken(
            tokenNumber = 8,
            status = TokenStatus.WAITING
        )

        val route = RouteState(
            distanceMeters = 500.0,
            durationSeconds = 300.0
        )

        val result = engine.calculateReturnRecommendation(
            token,
            4,
            null,
            5.0,
            route,
            safetyBufferMinutes = 5.0
        )

        assertEquals(
            RecommendationType.WAITING_RECOMMENDATION,
            result.recommendationType
        )

        assertEquals(20.0, result.estimatedQueueWaitMinutes, 0.001)
        assertEquals(5.0, result.walkingDurationMinutes!!, 0.001)
        assertEquals(10.0, result.minutesUntilReturn!!, 0.001)
        assertTrue(result.isDataValid)
    }

    // 28. Test recommendation when the visitor should return now
    @Test
    fun returnNowRecommendationTest() {
        val token = QueueToken(
            tokenNumber = 8,
            status = TokenStatus.WAITING
        )

        val route = RouteState(
            distanceMeters = 1000.0,
            durationSeconds = 600.0
        )

        val result = engine.calculateReturnRecommendation(
            token,
            1,
            null,
            5.0,
            route,
            safetyBufferMinutes = 5.0
        )

        assertEquals(
            RecommendationType.WAITING_START_NOW,
            result.recommendationType
        )

        assertEquals(0.0, result.minutesUntilReturn!!, 0.001)
    }

    // 29. Test route with missing distance
    @Test
    fun missingRouteDistanceTest() {
        val token = QueueToken(
            tokenNumber = 8,
            status = TokenStatus.WAITING
        )

        val route = RouteState(
            distanceMeters = null,
            durationSeconds = 300.0
        )

        val result = engine.calculateReturnRecommendation(
            token, 4, null, 5.0, route
        )

        assertEquals(
            RecommendationType.ROUTE_UNAVAILABLE,
            result.recommendationType
        )

        assertFalse(result.isDataValid)
    }

    // 30. Test route with missing duration
    @Test
    fun missingRouteDurationTest() {
        val token = QueueToken(
            tokenNumber = 8,
            status = TokenStatus.WAITING
        )

        val route = RouteState(
            distanceMeters = 500.0,
            durationSeconds = null
        )

        val result = engine.calculateReturnRecommendation(
            token, 4, null, 5.0, route
        )

        assertEquals(
            RecommendationType.ROUTE_UNAVAILABLE,
            result.recommendationType
        )

        assertFalse(result.isDataValid)
    }

    // 31. Test formatting a distance in metres
    @Test
    fun formatDistanceInMetresTest() {
        val route = RouteState(distanceMeters = 500.0)

        assertEquals("500 m", route.formattedDistance)
    }

    // 32. Test formatting a distance in kilometres
    @Test
    fun formatDistanceInKilometresTest() {
        val route = RouteState(distanceMeters = 1500.0)

        assertEquals("1.50 km", route.formattedDistance)
    }

    // 33. Test formatting walking duration
    @Test
    fun formatWalkingDurationTest() {
        val route = RouteState(durationSeconds = 330.0)

        assertEquals("6 min", route.formattedDuration)
    }

    // 34. Test formatting when distance is unavailable
    @Test
    fun unavailableDistanceFormattingTest() {
        val route = RouteState(distanceMeters = null)

        assertNull(route.formattedDistance)
    }

    // 35. Test formatting when duration is unavailable
    @Test
    fun unavailableDurationFormattingTest() {
        val route = RouteState(durationSeconds = null)

        assertNull(route.formattedDuration)
    }
}