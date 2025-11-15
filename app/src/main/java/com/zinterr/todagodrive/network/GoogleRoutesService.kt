package com.zinterr.todagodrive.network

import com.zinterr.todagodrive.model.*
import retrofit2.Call
import retrofit2.http.*

interface GoogleRoutesService {
    @POST("directions/v2:computeRoutes")
    fun getRoutes(
        @Header("X-Goog-Api-Key") apiKey: String,
        @Header("X-Goog-FieldMask") fieldMask: String,
        @Body request: RoutesRequest
    ): Call<RoutesResponse>
}