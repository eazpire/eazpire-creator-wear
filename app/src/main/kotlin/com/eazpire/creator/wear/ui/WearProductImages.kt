package com.eazpire.creator.wear.ui

import com.eazpire.creator.core.api.CreatorApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal fun normalizeWearImageUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return if (url.startsWith("//")) "https:$url" else url
}

internal fun resolvePublishedProductImage(obj: JSONObject): String? {
    fun fromAny(v: Any?): String? = when (v) {
        is String -> v.takeIf { it.isNotBlank() }
        is JSONObject -> v.optString("src", "").takeIf { it.isNotBlank() }
            ?: v.optString("url", "").takeIf { it.isNotBlank() }
        else -> null
    }
    val featured = fromAny(obj.opt("featured_image"))
    return normalizeWearImageUrl(
        fromAny(obj.opt("image_url"))
            ?: featured
            ?: fromAny(obj.opt("preview_url"))
            ?: fromAny(obj.opt("thumbnail_url"))
            ?: obj.optJSONArray("images")?.let { arr ->
                if (arr.length() > 0) fromAny(arr.opt(0)) else null
            },
    )
}

/** Published products + mockup_templates via get-products-by-keys (web parity). */
internal suspend fun loadWearProductCarouselItems(
    api: CreatorApi,
    ownerId: String,
): List<WearCarouselItem> = withContext(Dispatchers.IO) {
    val res = api.getPublishedProducts(ownerId)
    if (!res.optBoolean("ok", false)) return@withContext emptyList()
    val arr: JSONArray = res.optJSONArray("products") ?: JSONArray()
    val rows = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val key = o.optString("product_key", "").trim()
            val name = o.optString("product_name", "").trim()
            val img = resolvePublishedProductImage(o)
            add(Triple(key, name, img))
        }
    }
    val needMockupKeys = rows.filter { it.third.isNullOrBlank() && it.first.isNotBlank() }.map { it.first }
    val mockupByKey = if (needMockupKeys.isEmpty()) {
        emptyMap()
    } else {
        val keysParam = needMockupKeys.distinct().joinToString(",")
        val mockRes = api.getProductsByKeys(ownerId, keysParam)
        val map = mutableMapOf<String, String>()
        if (mockRes.optBoolean("ok", false)) {
            val mockArr = mockRes.optJSONArray("products") ?: JSONArray()
            for (i in 0 until mockArr.length()) {
                val m = mockArr.optJSONObject(i) ?: continue
                val pk = m.optString("product_key", "").trim()
                val url = normalizeWearImageUrl(m.optString("image_url", ""))
                if (pk.isNotBlank() && !url.isNullOrBlank()) map[pk] = url
            }
        }
        map
    }
    rows.map { (key, name, img) ->
        val url = img ?: mockupByKey[key]
        WearCarouselItem(imageUrl = url, label = name.takeIf { it.isNotBlank() })
    }
}
