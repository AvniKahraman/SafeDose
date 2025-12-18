package com.avnikahraman.safedose.models

import com.google.gson.annotations.SerializedName

data class BarcodeResponse(
    @SerializedName("products")
    val products: List<BarcodeProduct>?
)

data class BarcodeProduct(
    @SerializedName("barcode_number")
    val barcodeNumber: String?,

    @SerializedName("product_name")
    val productName: String?,

    @SerializedName("title")
    val title: String?,

    @SerializedName("description")
    val description: String?,

    @SerializedName("images")
    val images: List<String>?
)