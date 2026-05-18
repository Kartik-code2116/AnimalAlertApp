package com.example.animalalert.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val DEFAULT_BASE_URL = "http://10.30.201.240:5000/"
    private var currentBaseUrl: String = DEFAULT_BASE_URL
    private var retrofit: Retrofit? = null

    private val httpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    fun setBaseUrl(url: String) {
        val formattedUrl = if (url.endsWith("/")) url else "$url/"
        currentBaseUrl = formattedUrl
        retrofit = null // Force recreation with new URL
    }

    fun getBaseUrl(): String = currentBaseUrl

    fun resetToDefault() {
        currentBaseUrl = DEFAULT_BASE_URL
        retrofit = null
    }

    val api: ApiService
        get() {
            if (retrofit == null) {
                retrofit = Retrofit.Builder()
                    .baseUrl(currentBaseUrl)
                    .client(httpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
            }
            return retrofit!!.create(ApiService::class.java)
        }
}
