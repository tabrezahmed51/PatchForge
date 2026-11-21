package com.example.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

interface FastApiService {
    @GET("health")
    suspend fun getHealth(): HealthResponse

    @POST("api/v1/reviews/analyze")
    suspend fun submitAnalysis(@Body request: AnalyzeRequest): AnalyzeJobResponse

    @GET("api/v1/reviews/{jobId}")
    suspend fun getAnalysisJob(@Path("jobId") jobId: String): AnalyzeJobResponse

    @POST("api/v1/patches/apply")
    suspend fun applyPatch(@Body request: PatchApplyRequest): PatchApplyResponse
}

object FastApiClientFactory {
    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    fun buildOkHttpClient(bearerToken: String): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                    .header("Accept", "application/json")
                    .header("X-Client", "PatchForge-Android")
                if (bearerToken.isNotBlank()) {
                    builder.header("Authorization", "Bearer $bearerToken")
                }
                chain.proceed(builder.build())
            }
            .addInterceptor(logging)
            .build()
    }

    fun createService(baseUrl: String, bearerToken: String): FastApiService? {
        val normalized = baseUrl.trim().trimEnd('/') + "/"
        val parsed = normalized.toHttpUrlOrNull() ?: return null
        if (parsed.scheme != "http" && parsed.scheme != "https") return null

        val retrofit = Retrofit.Builder()
            .baseUrl(parsed)
            .client(buildOkHttpClient(bearerToken))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        return retrofit.create(FastApiService::class.java)
    }
}
