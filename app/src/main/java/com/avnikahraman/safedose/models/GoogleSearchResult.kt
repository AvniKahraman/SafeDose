package com.avnikahraman.safedose.models

import com.google.gson.annotations.SerializedName

data class GoogleSearchResponse(
    @SerializedName("items")
    val items: List<GoogleSearchItem>?
)

data class GoogleSearchItem(
    @SerializedName("title")
    val title: String?,

    @SerializedName("link")
    val link: String?,

    @SerializedName("snippet")
    val snippet: String?,

    @SerializedName("pagemap")
    val pagemap: PageMap?
)

data class PageMap(
    @SerializedName("cse_image")
    val images: List<ImageItem>?,

    @SerializedName("metatags")
    val metatags: List<Map<String, String>>?
)

data class ImageItem(
    @SerializedName("src")
    val src: String?
)