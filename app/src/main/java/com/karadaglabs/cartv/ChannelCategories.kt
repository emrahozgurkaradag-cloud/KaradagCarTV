package com.karadaglabs.cartv

/**
 * Sunucu kanal kategorilerinden dinamik kategori listesi (telefon ve araç
 * arayüzü ortak kullanır). Kategori adları sunucudan gelir; burada yalnızca
 * sıralama ve Türkçe görünen ad eşlemesi yapılır. Bilinmeyen kategori
 * olduğu gibi gösterilir, hiçbir kanal kategori yüzünden gizlenmez.
 */
object ChannelCategories {

    private val preferredOrder = listOf(
        "news", "sports", "kids", "music", "movies", "entertainment", "general",
        "business", "documentary", "education", "culture", "travel", "lifestyle",
        "religious", "animation", "series", "undefined"
    )

    private val displayNames = mapOf(
        "news" to "Haber",
        "sports" to "Spor",
        "kids" to "Çocuk",
        "music" to "Müzik",
        "movies" to "Film",
        "entertainment" to "Eğlence",
        "business" to "İş Dünyası",
        "documentary" to "Belgesel",
        "education" to "Eğitim",
        "culture" to "Kültür",
        "travel" to "Seyahat",
        "lifestyle" to "Yaşam",
        "religious" to "Dini",
        "animation" to "Animasyon",
        "series" to "Dizi",
        "general" to "Genel",
        "undefined" to "Diğer"
    )

    /** Kanalların taşıdığı tüm kategoriler (tekrarsız, büyük/küçük harf birleşik). */
    fun build(channels: List<IptvChannel>): List<String> {
        val seen = LinkedHashMap<String, String>()
        channels.flatMap { it.categories }.forEach { raw ->
            seen.putIfAbsent(raw.lowercase(), raw)
        }
        return seen.values.sortedWith(
            compareBy<String>({ rank(it) }, { it.lowercase() })
        )
    }

    /** Kategori başına kanal sayısı ([build] sırasıyla). */
    fun counts(channels: List<IptvChannel>): List<Pair<String, Int>> =
        build(channels).map { cat -> cat to channels.count { belongs(it, cat) } }

    fun belongs(channel: IptvChannel, category: String?): Boolean {
        if (category.isNullOrBlank()) return true
        return channel.categories.any { it.equals(category, ignoreCase = true) }
    }

    fun displayName(category: String): String =
        displayNames[category.trim().lowercase()] ?: category.trim()

    private fun rank(category: String): Int {
        val index = preferredOrder.indexOf(category.trim().lowercase())
        return if (index == -1) 999 else index
    }
}
