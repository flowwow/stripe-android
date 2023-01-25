package com.stripe.android.paymentsheet

import android.content.Intent

interface PaymentSheetErrorHandler {

    fun stripeHandleError(intent: Intent)

}