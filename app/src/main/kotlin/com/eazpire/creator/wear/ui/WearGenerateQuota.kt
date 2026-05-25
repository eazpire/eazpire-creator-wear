package com.eazpire.creator.wear.ui

import com.eazpire.creator.core.api.CreatorApi
import com.eazpire.creator.core.i18n.WearTranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

suspend fun loadWearGenerateConfirmModel(
    api: CreatorApi,
    ownerId: String,
    translationStore: WearTranslationStore,
): WearGenerateConfirmModel = withContext(Dispatchers.IO) {
    val bal = try {
        api.getBalance(ownerId)
    } catch (_: Exception) {
        JSONObject()
    }

    val walletActive = bal.optBoolean("eaz_wallet_active", false)
    val costs = bal.optJSONObject("eaz_costs")
    val features = bal.optJSONObject("eaz_feature_active")
    val costRaw = costs?.opt("design_generate")
    val costNum = when (costRaw) {
        is Number -> costRaw.toDouble()
        is String -> costRaw.toDoubleOrNull()
        else -> null
    }
    val featureOff = features?.optBoolean("design_generate") == false
    val isFree = featureOff || (costNum != null && costNum <= 0.0)
    val eazCost = if (costNum != null && costNum > 0) costNum else 10.0

    if (walletActive) {
        val balance = bal.optDouble("balance_total", bal.optDouble("balance_eaz", 0.0))
        if (!isFree && balance < eazCost) {
            return@withContext WearGenerateConfirmModel(
                useEaz = true,
                isFree = false,
                eazBalance = balance,
                eazCost = eazCost,
                canProceed = false,
                blockMessage = translationStore.t(
                    "wear.err_insufficient_eaz",
                    "Not enough EAZ. Top up on eazpire.com.",
                ),
            )
        }
        return@withContext WearGenerateConfirmModel(
            useEaz = true,
            isFree = isFree,
            eazBalance = balance,
            eazCost = if (isFree) 0.0 else eazCost,
            canProceed = true,
        )
    }

    val remaining = bal.optInt("trial_generate_remaining", -1)
    if (remaining <= 0) {
        return@withContext WearGenerateConfirmModel(
            useEaz = false,
            isFree = false,
            trialRemaining = 0,
            canProceed = false,
            blockMessage = translationStore.t(
                "wear.err_trial_generate_limit",
                "Trial generate limit reached. Upgrade your creator plan on eazpire.com.",
            ),
        )
    }
    WearGenerateConfirmModel(
        useEaz = false,
        isFree = isFree,
        trialRemaining = remaining.coerceAtLeast(0),
        canProceed = true,
    )
}
