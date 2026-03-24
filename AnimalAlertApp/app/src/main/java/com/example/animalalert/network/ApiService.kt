package com.example.animalalert.network

import com.example.animalalert.model.AlertResponse
import com.example.animalalert.model.CameraDetectRequest
import com.example.animalalert.model.CameraDetectResponse
import com.example.animalalert.model.CameraRegisterRequest
import com.example.animalalert.model.GenericBackendResponse
import com.example.animalalert.model.HealthResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {

    @GET("/latest-alert")
    fun getLatestAlert(): Call<AlertResponse>

    @GET("/health")
    fun getHealth(): Call<HealthResponse>

    @POST("/register/camera")
    fun registerCamera(@Body body: CameraRegisterRequest): Call<GenericBackendResponse>

    @POST("/camera/detect")
    fun detectFromCamera(@Body body: CameraDetectRequest): Call<CameraDetectResponse>

}
