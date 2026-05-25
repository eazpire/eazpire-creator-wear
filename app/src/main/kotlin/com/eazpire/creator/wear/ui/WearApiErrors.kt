package com.eazpire.creator.wear.ui

import com.eazpire.creator.core.i18n.WearTranslationStore

internal fun formatWearApiError(
    translationStore: WearTranslationStore,
    errorCode: String?,
    message: String?,
): String {
    val code = errorCode?.trim().orEmpty()
    val msg = message?.trim().orEmpty()
    val friendly = when (code) {
        "trial_generate_limit" -> translationStore.t(
            "wear.err_trial_generate_limit",
            "Trial generate limit reached. Upgrade your creator plan on eazpire.com.",
        )
        "generation_service_unavailable" -> translationStore.t(
            "wear.err_gen_unavailable",
            "Design generation is temporarily unavailable.",
        )
        "rate_limit_exceeded", "generation_limit_reached" -> translationStore.t(
            "wear.err_rate_limit",
            "Too many jobs running. Wait and try again.",
        )
        "insufficient_balance", "insufficient_eaz" -> translationStore.t(
            "wear.err_insufficient_eaz",
            "Not enough EAZ. Top up on eazpire.com.",
        )
        else -> ""
    }
    if (friendly.isNotBlank()) return friendly
    if (msg.isNotBlank() && !msg.contains('_')) return msg
    if (code.isNotBlank()) {
        return translationStore.t("wear.err_generic", "Something went wrong. Try again later.")
    }
    return translationStore.t("wear.err_generic", "Something went wrong. Try again later.")
}
