package com.example.adaptivequeue.data.location

import android.util.Log
import com.example.adaptivequeue.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrsRoutingRepository @Inject constructor() {

    private val TAG = "AdaptiveQueueRoute"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchWalkingRoute(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double
    ): Result<Pair<Double, Double>> = withContext(Dispatchers.IO) {
        try {
            val apiKey = BuildConfig.ORS_API_KEY
            if (apiKey.isBlank()) {
                Log.e(TAG, "Routing failed: ORS_API_KEY is blank in BuildConfig")
                return@withContext Result.failure(Exception("API key is missing in local.properties"))
            }

            // ORS coordinates format: start=longitude,latitude ; end=longitude,latitude
            val startCoords = String.format(Locale.US, "%.6f,%.6f", startLng, startLat)
            val endCoords = String.format(Locale.US, "%.6f,%.6f", endLng, endLat)

            val url = "https://api.heigit.org/openrouteservice/v2/directions/foot-walking" +
                    "?api_key=$apiKey" +
                    "&start=$startCoords" +
                    "&end=$endCoords"

            Log.d(TAG, "Sending ORS HTTP GET Request: start=($startLat, $startLng), end=($endLat, $endLng)")

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val statusCode = response.code
            Log.d(TAG, "ORS HTTP Response Code: $statusCode")

            if (!response.isSuccessful) {
                val message = when (statusCode) {
                    401, 403 -> "Invalid or unauthorized ORS API key (HTTP $statusCode)"
                    400, 404 -> "Unable to find a walking route between locations (HTTP $statusCode)"
                    429 -> "Rate limit exceeded. Please try again later (HTTP 429)"
                    else -> "HTTP $statusCode: Unable to calculate route"
                }
                Log.w(TAG, "ORS HTTP Error: $message")
                return@withContext Result.failure(Exception(message))
            }

            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                Log.w(TAG, "ORS Error: Empty response body")
                return@withContext Result.failure(Exception("Empty response from routing server"))
            }

            val json = JSONObject(responseBody)
            if (!json.has("features")) {
                Log.w(TAG, "ORS Error: Response missing 'features' field")
                return@withContext Result.failure(Exception("Malformed route response"))
            }

            val features = json.getJSONArray("features")
            if (features.length() == 0) {
                Log.w(TAG, "ORS Error: 'features' array is empty")
                return@withContext Result.failure(Exception("No walking route found"))
            }

            val summary = features.getJSONObject(0)
                .getJSONObject("properties")
                .getJSONObject("summary")

            val distanceMeters = summary.getDouble("distance")
            val durationSeconds = summary.getDouble("duration")

            Log.d(TAG, "ORS Route Success: dist=${distanceMeters}m, dur=${durationSeconds}s")
            Result.success(Pair(distanceMeters, durationSeconds))
        } catch (e: Exception) {
            val errorMessage = when (e) {
                is java.net.UnknownHostException -> "No internet connection"
                is java.net.SocketTimeoutException -> "Network request timed out"
                else -> e.localizedMessage ?: "Failed to calculate walking route"
            }
            Log.e(TAG, "ORS Routing Exception: $errorMessage", e)
            Result.failure(Exception(errorMessage))
        }
    }
}
