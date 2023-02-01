package com.stripe.android.paymentsheet.model

import android.content.Intent
import android.os.Parcelable
import androidx.appcompat.app.AppCompatActivity
import com.stripe.android.paymentsheet.PaymentSheetErrorHandler
import kotlinx.android.parcel.Parcelize

@Parcelize
data class StripeErrorHandler(
    val startHandler: ((AppCompatActivity, PaymentSheetErrorHandler) -> Unit)?,
    val finishHandler: ((AppCompatActivity) -> Intent)?,
    val isNeededCurrency: Boolean
): Parcelable
