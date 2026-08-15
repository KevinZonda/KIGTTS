package com.lhtstudio.kigtts.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrAppLinkClassifierTest {
    @Test
    fun `recognizes current and legacy WeChat payment links`() {
        assertTarget("weixin://pay.wechatpay.cn/bizpayurl/up?pr=test", QrAppTarget.WECHAT)
        assertTarget("weixin://wxpay/bizpayurl/up?pr=test", QrAppTarget.WECHAT)
        assertTarget("https://payapp.weixin.qq.com/materialqr/entry/home?id=test", QrAppTarget.WECHAT)
    }

    @Test
    fun `recognizes WeCom merchant contact links`() {
        assertTarget("https://work.weixin.qq.com/ca/cawcde123", QrAppTarget.WECHAT)
        assertTarget("https://wecom.qq.com/q/merchant", QrAppTarget.WECHAT)
        assertTarget("https://u.wechat.com/example", QrAppTarget.WECHAT)
    }

    @Test
    fun `recognizes additional Alipay links`() {
        assertTarget("https://ulink.alipay.com/?scheme=alipays%3A%2F%2Fplatformapi%2Fstartapp", QrAppTarget.ALIPAY)
        assertTarget("https://ur.alipay.com/test", QrAppTarget.ALIPAY)
        assertTarget("https://ds.alipay.com/?from=merchant", QrAppTarget.ALIPAY)
    }

    @Test
    fun `recognizes explicit aggregate payment channel`() {
        assertTarget("https://cashier.example.com/pay?wayCode=WX_NATIVE", QrAppTarget.WECHAT)
        assertTarget("https://cashier.example.com/pay?payType=alipay", QrAppTarget.ALIPAY)
        assertTarget("https://qr.lakala.com/order?id=1&type=alipay", QrAppTarget.ALIPAY)
        assertTarget(
            "https://cashier.example.com/pay?payload=%7B%22payMethod%22%3A%22wechat%22%7D",
            QrAppTarget.WECHAT
        )
    }

    @Test
    fun `recognizes encoded nested application link`() {
        assertTarget(
            "https://cashier.example.com/pay?redirect=weixin%3A%2F%2Fwxpay%2Fbizpayurl%2Fup%3Fpr%3Dtest",
            QrAppTarget.WECHAT
        )
    }

    @Test
    fun `requires a choice for opaque aggregate payment link`() {
        val result = QrAppLinkClassifier.classify("https://qr.lakala.com/order/merchant-code")

        assertTrue(result.isPotentialAggregatePayment)
        assertTrue(result.requiresAppChoice)
        assertEquals(emptySet<QrAppTarget>(), result.targets)
    }

    @Test
    fun `requires a choice when aggregate link contains both application routes`() {
        val result = QrAppLinkClassifier.classify(
            "https://pay.example.com/cashier?wxUrl=weixin%3A%2F%2Fwxpay%2Ftest" +
                "&alipayUrl=alipays%3A%2F%2Fplatformapi%2Fstartapp"
        )

        assertEquals(setOf(QrAppTarget.WECHAT, QrAppTarget.ALIPAY), result.targets)
        assertTrue(result.requiresAppChoice)
    }

    @Test
    fun `requires a choice for bank payment page without explicit channel`() {
        val result = QrAppLinkClassifier.classify("https://merchant.ccb.com/qrpay/order/123")

        assertTrue(result.isPotentialAggregatePayment)
        assertTrue(result.requiresAppChoice)
    }

    @Test
    fun `does not classify ordinary links or numeric codes`() {
        val web = QrAppLinkClassifier.classify("https://example.com/article/about-payments")
        val numeric = QrAppLinkClassifier.classify("123456789012345678")

        assertEquals(null, web.target)
        assertFalse(web.isPotentialAggregatePayment)
        assertEquals(null, numeric.target)
        assertFalse(numeric.isPotentialAggregatePayment)
    }

    private fun assertTarget(raw: String, target: QrAppTarget) {
        val result = QrAppLinkClassifier.classify(raw)
        assertEquals(target, result.target)
        assertFalse(result.requiresAppChoice)
    }
}
