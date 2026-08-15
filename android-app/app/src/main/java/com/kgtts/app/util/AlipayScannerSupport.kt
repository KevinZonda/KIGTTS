package com.lhtstudio.kigtts.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri

object AlipayScannerSupport {
    const val ALIPAY_PACKAGE_NAME = "com.eg.android.AlipayGphone"
    const val ALIPAY_SCANNER_URI = "alipayqr://platformapi/startapp?saId=10000007"
    const val ALIPAY_BROWSER_FALLBACK_URL = "https://www.alipay.com/"

    fun isAlipayQrContent(raw: String): Boolean {
        return QrAppLinkClassifier.isAlipay(raw)
    }

    fun launchScanner(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ALIPAY_SCANNER_URI)).apply {
            setPackage(ALIPAY_PACKAGE_NAME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (
            runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
        ) {
            return true
        }
        val fallbackIntent = context.packageManager.getLaunchIntentForPackage(ALIPAY_PACKAGE_NAME)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        return runCatching {
            if (fallbackIntent != null) {
                context.startActivity(fallbackIntent)
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }
}
