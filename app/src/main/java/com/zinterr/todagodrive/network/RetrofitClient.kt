package com.zinterr.todagodrive.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    private const val MAPS_BASE_URL = "https://maps.googleapis.com/maps/api/"
    private const val ROUTE_BASE_URL = "https://routes.googleapis.com/"

    private val retrofit: Retrofit.Builder by lazy {
        Retrofit.Builder().addConverterFactory(GsonConverterFactory.create())
    }

    // WILL BE USED WHEN MIGRATING TO ROUTES API
    val routesService: GoogleRoutesService by lazy {
        retrofit.baseUrl(ROUTE_BASE_URL)
        retrofit.build().create(GoogleRoutesService::class.java)
    }

    val directionsService: GoogleDirectionsService by lazy {
        retrofit.baseUrl(MAPS_BASE_URL)
        retrofit.build().create(GoogleDirectionsService::class.java)
    }

    val distanceMatrixService: GoogleDistanceMatrixService by lazy {
        retrofit.baseUrl(MAPS_BASE_URL)
        retrofit.build().create(GoogleDistanceMatrixService::class.java)
    }

    val geocodeService: GoogleGeocodingService by lazy {
        retrofit.baseUrl(MAPS_BASE_URL)
        retrofit.build().create(GoogleGeocodingService::class.java)
    }
}