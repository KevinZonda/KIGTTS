package com.lhtstudio.kigtts.app.util

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

enum class QrAppTarget {
    WECHAT,
    ALIPAY
}

data class QrAppClassification(
    val targets: Set<QrAppTarget> = emptySet(),
    val isPotentialAggregatePayment: Boolean = false
) {
    val target: QrAppTarget?
        get() = targets.singleOrNull()

    val requiresAppChoice: Boolean
        get() = targets.size > 1 || (target == null && isPotentialAggregatePayment)
}

object QrAppLinkClassifier {
    private const val MAX_QR_TEXT_LENGTH = 16_384

    private val weChatSchemes = setOf("weixin", "wxp", "wxpay")
    private val alipaySchemes = setOf("alipay", "alipayqr", "alipays")

    private val weChatHosts = setOf(
        "weixin.qq.com",
        "u.wechat.com",
        "wechat.com",
        "work.weixin.qq.com",
        "wecom.qq.com",
        "qy.weixin.qq.com",
        "weixin110.qq.com",
        "wx.tenpay.com",
        "payapp.weixin.qq.com",
        "pay.wechatpay.cn",
        "wechatpay.cn",
        "servicewechat.com",
        "channels.weixin.qq.com"
    )

    private val alipayHosts = setOf(
        "qr.alipay.com",
        "render.alipay.com",
        "openapi.alipay.com",
        "mapi.alipay.com",
        "ulink.alipay.com",
        "ur.alipay.com",
        "ds.alipay.com",
        "d.alipay.com",
        "m.alipay.com",
        "mobilecodec.alipay.com",
        "openauth.alipay.com",
        "auth.alipay.com",
        "memberprod.alipay.com",
        "qr.alipay.hk"
    )

    private val aggregatePaymentHosts = setOf(
        "lakala.com",
        "chinaums.com",
        "95516.com",
        "ums86.com",
        "shouqianba.com",
        "swiftpass.cn",
        "fuiou.com",
        "allinpay.com",
        "qfpay.com",
        "huifu.com",
        "sandpay.com.cn",
        "yeepay.com",
        "payeco.com"
    )

    private val bankHosts = setOf(
        "icbc.com.cn",
        "ccb.com",
        "abchina.com",
        "bankofchina.com",
        "boc.cn",
        "bankcomm.com",
        "cmbchina.com",
        "psbc.com",
        "cebbank.com",
        "cmbc.com.cn",
        "cib.com.cn",
        "spdb.com.cn",
        "pingan.com",
        "citicbank.com",
        "hxb.com.cn",
        "cgbchina.com.cn",
        "bankofbeijing.com.cn",
        "srcb.com",
        "bosc.cn"
    )

    private val channelKeys = setOf(
        "channel",
        "channelcode",
        "type",
        "paychannel",
        "paychannelcode",
        "paytype",
        "paymenttype",
        "paymethod",
        "paymentmethod",
        "method",
        "payway",
        "paywaycode",
        "waycode",
        "wallet",
        "provider",
        "platform",
        "client"
    )

    private val embeddedPairPattern = Regex(
        pattern = """(?i)[\"']?([a-z][a-z0-9_.-]{0,31})[\"']?\s*[:=]\s*[\"']?([^\"'&#,;\s}\]]{1,256})"""
    )

    private val nestedLinkKeys = setOf(
        "url",
        "link",
        "redirect",
        "redirecturl",
        "returnurl",
        "target",
        "targeturl",
        "scheme",
        "deeplink",
        "codeurl",
        "qrcodeurl",
        "payurl"
    )

    private val paymentMarkers = listOf(
        "aggregate",
        "cashier",
        "cashdesk",
        "checkout",
        "scanpay",
        "qrpay",
        "qrcode",
        "merchant",
        "collect",
        "receipt",
        "payment",
        "mch",
        "pay",
        "聚合",
        "收银",
        "收款",
        "付款"
    )

    fun classify(raw: String): QrAppClassification {
        val text = raw.trim().take(MAX_QR_TEXT_LENGTH)
        if (text.isEmpty()) return QrAppClassification()

        val directTargets = linkedSetOf<QrAppTarget>()
        val channelTargets = linkedSetOf<QrAppTarget>()
        var potentialAggregatePayment = false

        val variants = decodedVariants(text)
        variants.forEach { variant ->
            val uri = parseUri(variant)
            val directTarget = directTarget(uri)
            if (directTarget != null) directTargets += directTarget

            val host = uri?.host?.lowercase(Locale.US).orEmpty()
            if (host.matchesAnyHost(aggregatePaymentHosts)) {
                potentialAggregatePayment = true
            }
            if (host.matchesAnyHost(bankHosts) && containsPaymentMarker(uri.toString())) {
                potentialAggregatePayment = true
            }

            queryParameters(uri).forEach { (rawKey, rawValue) ->
                val signal = parameterSignal(rawKey, rawValue)
                channelTargets += signal.targets
                potentialAggregatePayment = potentialAggregatePayment || signal.isPaymentChannel
            }
            embeddedPairPattern.findAll(variant).forEach { match ->
                val signal = parameterSignal(match.groupValues[1], decodeComponent(match.groupValues[2]))
                channelTargets += signal.targets
                potentialAggregatePayment = potentialAggregatePayment || signal.isPaymentChannel
            }

            val parameterNames = queryParameters(uri).map { normalizeToken(it.first) }
            val namesMentionWeChat = parameterNames.any(::isWeChatToken)
            val namesMentionAlipay = parameterNames.any(::isAlipayToken)
            if (namesMentionWeChat) channelTargets += QrAppTarget.WECHAT
            if (namesMentionAlipay) channelTargets += QrAppTarget.ALIPAY
            if (namesMentionWeChat && namesMentionAlipay) potentialAggregatePayment = true
        }

        val targets = if (directTargets.isNotEmpty()) directTargets else channelTargets
        return QrAppClassification(
            targets = targets,
            isPotentialAggregatePayment = potentialAggregatePayment || targets.size > 1
        )
    }

    fun isWeChat(raw: String): Boolean = classify(raw).target == QrAppTarget.WECHAT

    fun isAlipay(raw: String): Boolean = classify(raw).target == QrAppTarget.ALIPAY

    private fun directTarget(uri: URI?): QrAppTarget? {
        if (uri == null) return null
        val scheme = uri.scheme?.lowercase(Locale.US).orEmpty()
        val host = uri.host?.lowercase(Locale.US).orEmpty()
        return when {
            scheme in weChatSchemes || host.matchesAnyHost(weChatHosts) -> QrAppTarget.WECHAT
            scheme in alipaySchemes || host.matchesAnyHost(alipayHosts) -> QrAppTarget.ALIPAY
            else -> null
        }
    }

    private fun queryParameters(uri: URI?): List<Pair<String, String>> {
        val query = uri?.rawQuery ?: return emptyList()
        return query.split('&', ';').mapNotNull { item ->
            if (item.isBlank()) return@mapNotNull null
            val separator = item.indexOf('=')
            val key = decodeComponent(if (separator >= 0) item.substring(0, separator) else item)
            val value = decodeComponent(if (separator >= 0) item.substring(separator + 1) else "")
            key to value
        }
    }

    private fun decodedVariants(raw: String): List<String> {
        val variants = linkedSetOf(raw)
        var current = raw
        repeat(2) {
            val decoded = decodeComponent(current)
            if (decoded == current || decoded.isBlank()) return@repeat
            variants += decoded
            current = decoded
        }
        return variants.toList()
    }

    private fun parameterSignal(rawKey: String, rawValue: String): ParameterSignal {
        val key = normalizeToken(rawKey)
        val value = rawValue.trim()
        val targets = linkedSetOf<QrAppTarget>()
        var isPaymentChannel = false

        val valueTarget = targetFromChannelValue(value)
        if (key in channelKeys && valueTarget != null) {
            targets += valueTarget
            isPaymentChannel = true
        }
        targetFromExplicitLinkKey(key)?.let {
            targets += it
            isPaymentChannel = true
        }
        if (key in nestedLinkKeys || key.endsWith("url") || key.endsWith("link")) {
            directTarget(parseUri(value))?.let { targets += it }
        }
        return ParameterSignal(targets, isPaymentChannel)
    }

    private fun targetFromExplicitLinkKey(key: String): QrAppTarget? {
        val isLinkKey = key.endsWith("url") || key.endsWith("link") || key.endsWith("scheme")
        if (!isLinkKey) return null
        return when {
            isWeChatToken(key) -> QrAppTarget.WECHAT
            isAlipayToken(key) -> QrAppTarget.ALIPAY
            else -> null
        }
    }

    private fun targetFromChannelValue(value: String): QrAppTarget? {
        val token = normalizeToken(value)
        return when {
            isWeChatToken(token) -> QrAppTarget.WECHAT
            isAlipayToken(token) -> QrAppTarget.ALIPAY
            else -> null
        }
    }

    private fun isWeChatToken(token: String): Boolean {
        val normalized = normalizeToken(token)
        return normalized == "wx" ||
            normalized.startsWith("wxpay") ||
            normalized.startsWith("wxnative") ||
            normalized.startsWith("wxqr") ||
            normalized.contains("wechat") ||
            normalized.contains("weixin") ||
            normalized.contains("tenpay")
    }

    private fun isAlipayToken(token: String): Boolean {
        val normalized = normalizeToken(token)
        return normalized == "ali" ||
            normalized.startsWith("alipay") ||
            normalized.startsWith("alinative") ||
            normalized.startsWith("aliqr")
    }

    private fun containsPaymentMarker(value: String): Boolean {
        val lower = value.lowercase(Locale.US)
        return paymentMarkers.any(lower::contains)
    }

    private fun String.matchesAnyHost(hosts: Set<String>): Boolean {
        if (isEmpty()) return false
        return hosts.any { this == it || endsWith(".$it") }
    }

    private fun normalizeToken(value: String): String {
        return value.lowercase(Locale.US).filter(Char::isLetterOrDigit)
    }

    private fun parseUri(value: String): URI? {
        return runCatching { URI(value.trim()) }.getOrNull()
    }

    private fun decodeComponent(value: String): String {
        if ('%' !in value && '+' !in value) return value
        return runCatching {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        }.getOrDefault(value)
    }

    private data class ParameterSignal(
        val targets: Set<QrAppTarget>,
        val isPaymentChannel: Boolean
    )
}
