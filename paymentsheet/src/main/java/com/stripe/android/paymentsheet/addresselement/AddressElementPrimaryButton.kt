package com.stripe.android.paymentsheet.addresselement

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.stripe.android.uicore.StripeTheme
import com.stripe.android.uicore.getBackgroundColor
import com.stripe.android.uicore.getBorderStrokeColor
import com.stripe.android.uicore.getOnBackgroundColor

@Composable
internal fun AddressElementPrimaryButton(
    isEnabled: Boolean,
    text: String,
    onButtonClick: () -> Unit
) {
    // We need to use PaymentsTheme.primaryButtonStyle instead of MaterialTheme
    // because of the rules API for primary button.
    val context = LocalContext.current
    val background = Color(StripeTheme.primaryButtonStyle.getBackgroundColor(context))
    val onBackground = Color(StripeTheme.primaryButtonStyle.getOnBackgroundColor(context))
    val borderStroke = BorderStroke(
        StripeTheme.primaryButtonStyle.shape.borderStrokeWidth.dp,
        Color(StripeTheme.primaryButtonStyle.getBorderStrokeColor(context))
    )
    val shape = RoundedCornerShape(
        StripeTheme.primaryButtonStyle.shape.cornerRadius
    )
    val fontFamily = StripeTheme.primaryButtonStyle.typography.fontFamily
    val textStyle = TextStyle(
        fontFamily = if (fontFamily != null) FontFamily(Font(fontFamily)) else FontFamily.Default,
        fontSize = StripeTheme.primaryButtonStyle.typography.fontSize
    )

    CompositionLocalProvider(
        LocalContentAlpha provides if (isEnabled) ContentAlpha.high else ContentAlpha.disabled
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            TextButton(
                onClick = onButtonClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                enabled = isEnabled,
                shape = shape,
                border = borderStroke,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = background,
                    disabledBackgroundColor = background
                )
            ) {
                Text(
                    text = text,
                    color = onBackground.copy(alpha = LocalContentAlpha.current),
                    style = textStyle
                )
            }
        }
    }
}
