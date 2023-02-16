package com.stripe.android.paymentsheet.model

import android.os.Parcelable
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.parcel.Parcelize

@Parcelize
data class StripeErrorHandler(
    val startHandler: ((AppCompatActivity) -> Unit)?,
    val isNeededCurrency: Boolean
): Parcelable
