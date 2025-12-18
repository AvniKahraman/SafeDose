package com.avnikahraman.safedose.network

import com.avnikahraman.safedose.models.BarcodeResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface BarcodeApiService {

    @GET("v3/products")
    suspend fun lookupBarcode(
        @Query("barcode") barcode: String,
        @Query("formatted") formatted: String = "y",
        @Query("key") apiKey: String
    ): Response<BarcodeResponse>
}