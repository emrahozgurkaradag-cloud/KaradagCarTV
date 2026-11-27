package com.karadaglabs.cartv

data class IptvChannel(
    val id: Int,
    val name: String,
    val streamUrl: String,
    val logoUrl: String?,
    val category: String?,
    /** API `is_featured` (yönetici panelinde "öne çıkan" işaretli). */
    val isFeatured: Boolean = false,
    /** API `sort_order`; yoksa sona. */
    val sortOrder: Int = Int.MAX_VALUE
) {

    val mediaId: String
        get() = "live-tv-$id"

    /** `category` alanı `;` ile birden çok kategori taşıyabilir. */
    val categories: List<String>
        get() = category
            ?.split(";")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
}
