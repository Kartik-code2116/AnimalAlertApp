package com.example.animalalert.network

import com.example.animalalert.model.AlertResponse
import retrofit2.Call
import retrofit2.http.GET

interface ApiService {

    @GET("/latest-alert")
    fun getLatestAlert(): Call<AlertResponse>

}
