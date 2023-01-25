package com.stripe.android.paymentsheet

import com.stripe.android.paymentsheet.model.StripeErrorHandler

internal interface PaymentSheetLauncher {
    fun presentWithPaymentIntent(
        paymentIntentClientSecret: String,
        configuration: PaymentSheet.Configuration? = null
    )

    fun presentWithSetupIntent(
        setupIntentClientSecret: String,
        configuration: PaymentSheet.Configuration? = null,
        stripeErrorHandler: StripeErrorHandler? = null
    )
}
