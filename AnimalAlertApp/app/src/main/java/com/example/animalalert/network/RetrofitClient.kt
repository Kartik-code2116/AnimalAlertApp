package com.example.animalalert.network

import android.content.Context
import com.example.animalalert.utils.PreferenceManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val DEFAULT_BASE_URL = "https://10.244.200.127:5000/"
    private var currentBaseUrl: String = DEFAULT_BASE_URL
    private var retrofit: Retrofit? = null
    private var authContext: Context? = null

    private val httpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        
        // Create a trust manager that does not validate certificate chains (for development ad-hoc SSL)
        val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        })
        val sslContext = javax.net.ssl.SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        
        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val requestBuilder = chain.request().newBuilder()
                authContext?.let { ctx ->
                    PreferenceManager(ctx).getAuthToken()?.let { token ->
                        requestBuilder.addHeader("Authorization", "Bearer $token")
                    }
                }
                chain.proceed(requestBuilder.build())
            }
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    fun setBaseUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        var processedUrl = trimmed
        if (processedUrl.startsWith("http://")) {
            processedUrl = processedUrl.replaceFirst("http://", "https://")
        } else if (!processedUrl.startsWith("https://")) {
            processedUrl = "https://$processedUrl"
        }
        val formattedUrl = if (processedUrl.endsWith("/")) processedUrl else "$processedUrl/"
        currentBaseUrl = formattedUrl
        retrofit = null // Force recreation with new URL
    }

    fun getBaseUrl(): String = currentBaseUrl

    fun resetToDefault() {
        currentBaseUrl = DEFAULT_BASE_URL
        retrofit = null
    }

    /** Read server URL from settings and rebuild Retrofit if it changed. */
    fun configure(context: Context) {
        authContext = context.applicationContext
        setBaseUrl(PreferenceManager(context).getServerUrl())
    }

    fun getApi(context: Context): ApiService {
        configure(context)
        return api
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
