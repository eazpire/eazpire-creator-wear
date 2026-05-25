package com.eazpire.creator.core.api

import com.eazpire.creator.core.auth.AuthConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Slim creator-engine client for Wear (stats, jobs, generator).
 */
class CreatorApi(
    private val baseUrl: String = AuthConfig.CREATOR_ENGINE_URL,
    private val jwt: String? = null,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json".toMediaType()

    suspend fun call(
        op: String,
        params: Map<String, String> = emptyMap(),
        method: String = "GET",
        jsonBody: String? = null,
    ): JSONObject = withContext(Dispatchers.IO) {
        val url = buildString {
            append("$baseUrl/apps/creator-dispatch?op=$op")
            if (method == "GET") append("&_t=${System.currentTimeMillis()}")
            params.forEach { (k, v) ->
                if (v.isNotBlank()) append("&${k}=${java.net.URLEncoder.encode(v, "UTF-8")}")
            }
        }
        val body = when {
            method == "POST" && jsonBody != null -> jsonBody.toRequestBody(jsonType)
            method == "POST" -> okhttp3.RequestBody.create(null, byteArrayOf())
            else -> null
        }
        val request = Request.Builder()
            .url(url)
            .apply { jwt?.let { addHeader("Authorization", "Bearer $it") } }
            .method(method, body)
            .build()
        JSONObject(client.newCall(request).execute().body?.string() ?: "{}")
    }

    suspend fun getDesignSourceCounts(ownerId: String): JSONObject =
        call("get-design-source-counts", mapOf("owner_id" to ownerId))

    suspend fun getPublishStats(ownerId: String): JSONObject =
        call("get-publish-stats", mapOf("owner_id" to ownerId))

    suspend fun getCreatorSales(ownerId: String): JSONObject =
        call("get-creator-sales", mapOf("owner_id" to ownerId))

    suspend fun getCreatorPayoutOverview(ownerId: String, days: Int = 90): JSONObject =
        call("get-creator-payout-overview", mapOf("owner_id" to ownerId, "days" to days.toString()))

    suspend fun listJobs(ownerId: String, limit: Int = 10): JSONObject =
        call("list-jobs", mapOf("owner_id" to ownerId, "limit" to limit.toString()))

    suspend fun getLevel(ownerId: String): JSONObject =
        call("get-level", mapOf("owner_id" to ownerId))

    suspend fun getOnboardingProgress(ownerId: String): JSONObject =
        call("get-onboarding-progress", mapOf("owner_id" to ownerId))

    suspend fun listR2Designs(ownerId: String, limit: Int = 24): JSONObject =
        call("list-r2", mapOf("owner_id" to ownerId, "limit" to limit.toString()))

    suspend fun getBalance(ownerId: String): JSONObject =
        call("get-balance", mapOf("owner_id" to ownerId))

    suspend fun listSavedDesigns(ownerId: String, limit: Int = 40): JSONObject =
        call(
            "list",
            mapOf(
                "owner_id" to ownerId,
                "limit" to limit.toString(),
                "library_status" to "active",
            ),
        )

    suspend fun listInactiveDesigns(ownerId: String, limit: Int = 40): JSONObject =
        call("list-generated", mapOf("owner_id" to ownerId, "limit" to limit.toString()))

    suspend fun getPublishedProducts(
        ownerId: String,
        shop: String? = com.eazpire.creator.core.auth.AuthConfig.SHOP_DOMAIN,
    ): JSONObject {
        val params = mutableMapOf("owner_id" to ownerId)
        shop?.takeIf { it.isNotBlank() }?.let { params["shop"] = it }
        return call("get-published-products", params)
    }

    suspend fun getProductsByKeys(ownerId: String, productKeys: String): JSONObject =
        call(
            "get-products-by-keys",
            mapOf("owner_id" to ownerId, "product_keys" to productKeys),
        )

    suspend fun wearGenerate(ownerId: String, prompt: String? = null, imageUrl: String? = null): JSONObject {
        val body = JSONObject().put("owner_id", ownerId)
        if (!prompt.isNullOrBlank()) body.put("prompt", prompt)
        if (!imageUrl.isNullOrBlank()) body.put("image_url", imageUrl)
        return call("wear-generate", mapOf("owner_id" to ownerId), method = "POST", jsonBody = body.toString())
    }

    suspend fun createPhoneUploadSession(ownerId: String): JSONObject =
        postJson("$baseUrl/api/creator-phone-upload/session", JSONObject().put("owner_id", ownerId).toString())

    suspend fun pollPhoneUploadSession(sessionId: String, ownerId: String): JSONObject = withContext(Dispatchers.IO) {
        val url = buildString {
            append("$baseUrl/api/creator-phone-upload/session")
            append("?id=").append(java.net.URLEncoder.encode(sessionId, "UTF-8"))
            append("&owner_id=").append(java.net.URLEncoder.encode(ownerId, "UTF-8"))
        }
        val request = Request.Builder()
            .url(url)
            .apply { jwt?.let { addHeader("Authorization", "Bearer $it") } }
            .get()
            .build()
        JSONObject(client.newCall(request).execute().body?.string() ?: "{}")
    }

    fun phoneUploadQrUrl(sessionId: String): String =
        "$baseUrl/api/creator-phone-upload/qr-image?session=${java.net.URLEncoder.encode(sessionId, "UTF-8")}"

    /** POST ?op=upload-design — image fetched from URL (after phone QR upload). */
    suspend fun uploadDesignFromImageUrl(
        ownerId: String,
        imageUrl: String,
        creatorName: String? = null,
    ): JSONObject = withContext(Dispatchers.IO) {
        val bytesReq = Request.Builder().url(imageUrl).get().build()
        val bytesRes = client.newCall(bytesReq).execute()
        if (!bytesRes.isSuccessful) {
            return@withContext JSONObject().put("ok", false).put("error", "image_fetch_failed")
        }
        val bodyBytes = bytesRes.body?.bytes() ?: ByteArray(0)
        if (bodyBytes.isEmpty()) {
            return@withContext JSONObject().put("ok", false).put("error", "empty_image")
        }
        val mime = bytesRes.header("Content-Type")?.substringBefore(";")?.trim()
            ?: "image/png"
        val ext = when {
            mime.contains("jpeg", ignoreCase = true) || mime.contains("jpg", ignoreCase = true) -> "jpg"
            mime.contains("webp", ignoreCase = true) -> "webp"
            mime.contains("svg", ignoreCase = true) -> "svg"
            else -> "png"
        }
        val tmp = File.createTempFile("wear-upload-", ".$ext")
        try {
            tmp.writeBytes(bodyBytes)
            val fileBody = tmp.asRequestBody(mime.toMediaType())
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", "design.$ext", fileBody)
            creatorName?.takeIf { it.isNotBlank() }?.let {
                multipart.addFormDataPart("creator_name", it)
            }
            val url = buildString {
                append("$baseUrl/apps/creator-dispatch?op=upload-design")
                append("&owner_id=").append(java.net.URLEncoder.encode(ownerId, "UTF-8"))
                append("&_t=").append(System.currentTimeMillis())
            }
            val request = Request.Builder()
                .url(url)
                .post(multipart.build())
                .apply { jwt?.let { addHeader("Authorization", "Bearer $it") } }
                .build()
            JSONObject(client.newCall(request).execute().body?.string() ?: "{}")
        } finally {
            tmp.delete()
        }
    }

    private suspend fun postJson(url: String, jsonBody: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody(jsonType))
            .apply { jwt?.let { addHeader("Authorization", "Bearer $it") } }
            .build()
        JSONObject(client.newCall(request).execute().body?.string() ?: "{}")
    }
}
