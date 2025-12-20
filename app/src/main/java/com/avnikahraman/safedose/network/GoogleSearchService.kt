package com.avnikahraman.safedose.network

import com.avnikahraman.safedose.models.GoogleSearchResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface GoogleSearchService {

    @GET("customsearch/v1")
    suspend fun search(
        @Query("key") apiKey: String,
        @Query("cx") searchEngineId: String,
        @Query("q") query: String,
        @Query("num") num: Int = 5
    ): Response<GoogleSearchResponse>
}