package com.example.animalalert.network

import com.example.animalalert.model.AlertResponse
import com.example.animalalert.model.AuthResponse
import com.example.animalalert.model.CameraDetectRequest
import com.example.animalalert.model.CameraDetectResponse
import com.example.animalalert.model.CameraInfo
import com.example.animalalert.model.CameraRegisterRequest
import com.example.animalalert.model.GenericBackendResponse
import com.example.animalalert.model.HealthResponse
import com.example.animalalert.model.LoginRequest
import com.example.animalalert.model.RegisterRequest
import com.example.animalalert.model.ServerAlert
import com.example.animalalert.model.SystemStatus
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST

interface ApiService {

    // ── Existing endpoints ──────────────────────────────────────
    @GET("/latest-alert")
    fun getLatestAlert(): Call<AlertResponse>

    @GET("/health")
    fun getHealth(): Call<HealthResponse>

    @POST("/register/camera")
    fun registerCamera(@Body body: CameraRegisterRequest): Call<GenericBackendResponse>

    @POST("/camera/detect")
    fun detectFromCamera(@Body body: CameraDetectRequest): Call<CameraDetectResponse>

    // ── Shared CCTV control (website admin → all app users) ───
    @GET("/api/system/status")
    fun getSystemStatus(): Call<SystemStatus>

    // ── Task 2: Camera list from server ─────────────────────────
    @GET("/api/cameras")
    fun getCameras(): Call<List<CameraInfo>>

    // ── Task 3: Alert history from MongoDB ──────────────────────
    @GET("/api/alerts")
    fun getAlertHistory(): Call<List<ServerAlert>>

    @DELETE("/api/alerts")
    fun clearAlertHistory(): Call<GenericBackendResponse>

    // ── Task 4: Server-side authentication ──────────────────────
    @POST("/api/auth/login")
    fun login(@Body body: LoginRequest): Call<AuthResponse>

    @POST("/api/auth/register")
    fun register(@Body body: RegisterRequest): Call<AuthResponse>
}
