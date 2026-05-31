package dev.jisungbin.copyurl

import java.net.URLDecoder

/** URL 쿼리에서 트래커 파라미터만 제거. scheme/host/path/fragment 는 그대로 둔다. */
object TrackerCleaner {

    private val PREFIXES = listOf("utm_", "pk_", "mtm_", "matomo_", "piwik_", "hsa_")

    private val EXACT: Set<String> = setOf(
        // Google
        "gclid", "gclsrc", "dclid", "gbraid", "wbraid", "gad_source", "gad_campaignid",
        "_gl", "gcles", "gbclid", "srsltid",
        // Meta / Facebook
        "fbclid", "fb_action_ids", "fb_action_types", "fb_source", "fb_ref",
        // Microsoft / Bing
        "msclkid",
        // Twitter / X
        "twclid", "ref_src", "ref_url",
        // TikTok
        "ttclid",
        // Instagram
        "igshid", "igsh",
        // Mailchimp
        "mc_cid", "mc_eid",
        // HubSpot
        "_hsenc", "_hsmi", "__hssc", "__hstc", "__hsfp", "hsctatracking",
        // Yandex
        "yclid", "ysclid", "_openstat",
        // Marketo
        "mkt_tok",
        // Adobe
        "s_cid", "s_kwcid", "ef_id",
        // Klaviyo
        "_kx",
        // Drip
        "__s",
        // Olytics
        "oly_anon_id", "oly_enc_id",
        // Vero
        "vero_id", "vero_conv",
        // Pinterest
        "epik",
        // Snapchat
        "sc_cid",
        // Wicked Reports
        "wickedid",
        // Outbrain / Taboola
        "obclid", "tblci",
        // MailerLite
        "ml_subscriber", "ml_subscriber_hash",
        // Sailthru
        "spjobid", "spreportid", "spmailingid", "spuserid",
        // Yahoo
        "guce_referrer", "guccounter", "soc_src", "soc_trk",
        // 뉴스 / 광고 일반
        "cmpid", "ncid", "icid", "wt.mc_id",
    )

    fun isTracker(key: String): Boolean {
        val k = key.lowercase()
        if (k in EXACT) return true
        return PREFIXES.any { k.startsWith(it) }
    }

    data class Result(val url: String, val removed: Int)

    fun clean(rawUrl: String): Result {
        val hashIndex = rawUrl.indexOf('#')
        val fragment = if (hashIndex >= 0) rawUrl.substring(hashIndex) else ""
        val beforeFragment = if (hashIndex >= 0) rawUrl.substring(0, hashIndex) else rawUrl

        val qIndex = beforeFragment.indexOf('?')
        if (qIndex < 0) return Result(rawUrl, 0)

        val base = beforeFragment.substring(0, qIndex)
        val query = beforeFragment.substring(qIndex + 1)
        if (query.isEmpty()) return Result(base + fragment, 0)

        var removed = 0
        val kept = query.split("&").filter { pair ->
            if (pair.isEmpty()) return@filter false
            val rawKey = pair.substringBefore("=")
            val key = try {
                URLDecoder.decode(rawKey, "UTF-8")
            } catch (e: Exception) {
                rawKey
            }
            if (isTracker(key)) {
                removed++
                false
            } else {
                true
            }
        }

        val newUrl = if (kept.isEmpty()) base else base + "?" + kept.joinToString("&")
        return Result(newUrl + fragment, removed)
    }
}
