package com.zinterr.todagodrive.network

import com.zinterr.todagodrive.model.DirectionsResponse
import retrofit2.Call
import retrofit2.http.*

interface GoogleDirectionsService {
    @GET("directions/json")
    fun getDirections(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("key") apiKey: String,
        @Query("waypoints") waypoints: String? = null,
        @Query("departure_time") departureTime: String = "now",
        @Query("traffic_model") trafficModel: String = "best_guess",
        @Query("mode") mode: String = "driving"
    ): Call<DirectionsResponse>
}