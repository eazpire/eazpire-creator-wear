package com.eazpire.creator.wear.ui

/** Sample rows for debug emulator preview (no API / phone required). */
object WearDemo {
    val dashboardLines = listOf(
        "Products online: 12",
        "Products offline: 3",
        "Sales: 47",
        "Designs generated: 28",
        "Designs uploaded: 19",
        "Available payout: 142.50 EUR",
    )

    val jobs = listOf(
        WearJobRow("Sunset graphic tee", "running"),
        WearJobRow("Logo refresh batch", "queued"),
    )
}
