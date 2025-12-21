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

    @GET("customsearch/v1")
    suspend fun searchImage(
        @Query("key") apiKey: String,
        @Query("cx") searchEngineId: String,
        @Query("q") query: String,

        @Query("searchType") searchType: String = "image",
        @Query("imgType") imgType: String = "photo",
        @Query("imgSize") imgSize: String = "medium",
        @Query("safe") safe: String = "active",

        @Query("num") num: Int = 1
    ): Response<GoogleSearchResponse>

}