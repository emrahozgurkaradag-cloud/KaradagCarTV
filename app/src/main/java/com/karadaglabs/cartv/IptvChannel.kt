package com.karadaglabs.cartv

data class IptvChannel(
    val id: Int,
    val name: String,
    val streamUrl: String,
    val logoUrl: String?,
    val category: String?
) {

    val mediaId: String
        get() = "live-tv-$id"
}
