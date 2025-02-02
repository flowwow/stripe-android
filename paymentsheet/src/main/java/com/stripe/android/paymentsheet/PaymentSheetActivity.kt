package com.stripe.android.paymentsheet

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.stripe.android.googlepaylauncher.GooglePayPaymentMethodLauncherContract
import com.stripe.android.paymentsheet.databinding.ActivityPaymentSheetBinding
import com.stripe.android.paymentsheet.model.PaymentSheetViewState
import com.stripe.android.paymentsheet.state.WalletsContainerState
import com.stripe.android.paymentsheet.ui.BaseSheetActivity
import com.stripe.android.paymentsheet.ui.GooglePayDividerUi
import com.stripe.android.paymentsheet.ui.PrimaryButton
import com.stripe.android.paymentsheet.utils.launchAndCollectIn
import com.stripe.android.uicore.StripeTheme
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import java.security.InvalidParameterException
import java.util.*

internal class PaymentSheetActivity : BaseSheetActivity<PaymentSheetResult>(), PaymentSheetErrorHandler {
    @VisibleForTesting
    internal val viewBinding by lazy {
        ActivityPaymentSheetBinding.inflate(layoutInflater)
    }

    @VisibleForTesting
    internal var viewModelFactory: ViewModelProvider.Factory = PaymentSheetViewModel.Factory {
        requireNotNull(starterArgs)
    }

    override val viewModel: PaymentSheetViewModel by viewModels { viewModelFactory }

    private val starterArgs: PaymentSheetContract.Args? by lazy {
        PaymentSheetContract.Args.fromIntent(intent)
    }

    override val rootView: ViewGroup by lazy { viewBinding.root }
    override val bottomSheet: ViewGroup by lazy { viewBinding.bottomSheet }
    override val appbar: AppBarLayout by lazy { viewBinding.appbar }
    override val linkAuthView: ComposeView by lazy { viewBinding.linkAuth }
    override val toolbar: MaterialToolbar by lazy { viewBinding.toolbar }
    override val testModeIndicator: TextView by lazy { viewBinding.testmode }
    override val scrollView: ScrollView by lazy { viewBinding.scrollView }
    override val header: ComposeView by lazy { viewBinding.header }
    override val fragmentContainerParent: ViewGroup by lazy { viewBinding.fragmentContainerParent }
    override val messageView: TextView by lazy { viewBinding.message }
    override val notesView: ComposeView by lazy { viewBinding.notes }
    override val primaryButton: PrimaryButton by lazy { viewBinding.buyButton }
    override val bottomSpacer: View by lazy { viewBinding.bottomSpacer }

    private val buttonContainer: ViewGroup by lazy { viewBinding.buttonContainer }
    private val topContainer by lazy { viewBinding.topContainer }
    private val googlePayButton by lazy { viewBinding.googlePayButton }
    private val linkButton by lazy { viewBinding.linkButton }
    private val topMessage by lazy { viewBinding.topMessage }
    private val googlePayDivider by lazy { viewBinding.googlePayDivider }

    val registerForActivityLanguageResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            finish()
            startActivity(starterArgs?.stripeErrorHandler?.finishHandler?.invoke(this))
        }
    }

    override fun stripeHandleError(intent: Intent) {
        registerForActivityLanguageResult.launch(intent)
    }

    override fun attachBaseContext(newBase: Context?) {
        val locale = Locale(ConfigurationHelper.lang)
        Locale.setDefault(locale)
        val configuration = newBase?.resources?.configuration
        configuration?.setLocale(locale)
        newBase?.resources?.updateConfiguration(configuration,null)
        super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val validationResult = initializeArgs()
        super.onCreate(savedInstanceState)

        val validatedArgs = validationResult.getOrNull()
        if (validatedArgs == null) {
            finishWithError(error = validationResult.exceptionOrNull())
            return
        }

        viewModel.registerFromActivity(this)

        viewModel.setupGooglePay(
            lifecycleScope,
            registerForActivityResult(
                GooglePayPaymentMethodLauncherContract(),
                viewModel::onGooglePayResult
            )
        )

        starterArgs?.statusBarColor?.let {
            window.statusBarColor = it
        }
        setContentView(viewBinding.root)

        rootView.doOnNextLayout {
            // Show bottom sheet only after the Activity has been laid out so that it animates in
            bottomSheetController.expand()
        }

        setupTopContainer()

        linkButton.apply {
            onClick = { config ->
                linkHandler.launchLink(config, launchedDirectly = false)
            }
            linkPaymentLauncher = linkLauncher
        }

        viewBinding.contentContainer.setContent {
            StripeTheme {
                val currentScreen by viewModel.currentScreen.collectAsState()

                LaunchedEffect(currentScreen) {
                    buttonContainer.isVisible = currentScreen.showsBuyButton
                }

                currentScreen.PaymentSheetContent(validatedArgs)
            }
        }

        if (savedInstanceState == null) {
            viewModel.transitionToFirstScreenWhenReady()
        }

        viewModel.processing.filter { it }.launchAndCollectIn(this) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        }

        viewModel.paymentSheetResult.filterNotNull().launchAndCollectIn(this) {
            closeSheet(it)
        }

        viewModel.buttonsEnabled.launchAndCollectIn(this) { enabled ->
            linkButton.isEnabled = enabled
            googlePayButton.isEnabled = enabled
        }

        viewModel.selection.filterNotNull().launchAndCollectIn(this) {
            viewModel.clearErrorMessages()
            resetPrimaryButtonState()
        }

        viewModel.buyButtonState.launchAndCollectIn(this) { viewState ->
            updateErrorMessage(messageView, viewState?.errorMessage)
            viewBinding.buyButton.updateState(viewState?.convert())
        }

        viewModel.stripeErrorFlow.launchAndCollectIn(this) { stripeErrorhandler ->
            stripeErrorhandler?.startHandler?.invoke(this, this)
        }
    }

    private fun initializeArgs(): Result<PaymentSheetContract.Args?> {
        val starterArgs = this.starterArgs

        val result = if (starterArgs == null) {
            Result.failure(defaultInitializationError())
        } else {
            try {
                starterArgs.config?.validate()
                starterArgs.clientSecret.validate()
                starterArgs.config?.appearance?.parseAppearance()
                Result.success(starterArgs)
            } catch (e: InvalidParameterException) {
                Result.failure(e)
            }
        }

        earlyExitDueToIllegalState = result.isFailure
        return result
    }

    override fun resetPrimaryButtonState() {
        viewBinding.buyButton.updateState(PrimaryButton.State.Ready)

        val customLabel = starterArgs?.config?.primaryButtonLabel

        val label = if (customLabel != null) {
            starterArgs?.config?.primaryButtonLabel
        } else if (viewModel.isProcessingPaymentIntent) {
            viewModel.amount.value?.buildPayButtonLabel(resources)
        } else {
            getString(R.string.stripe_setup_button_label)
        }

        viewBinding.buyButton.setLabel(label)

        viewBinding.buyButton.setOnClickListener {
            viewModel.checkout()
        }
    }

    private fun setupTopContainer() {
        setupGooglePayButton()

        viewModel.walletsContainerState.launchAndCollectIn(this) { config ->
            linkButton.isVisible = config.showLink
            googlePayButton.isVisible = config.showGooglePay
            topContainer.isVisible = config.shouldShow
        }

        googlePayDivider.setContent {
            val containerState by viewModel.walletsContainerState.collectAsState(
                initial = WalletsContainerState(),
            )

            StripeTheme {
                if (containerState.shouldShow) {
                    val text = stringResource(containerState.dividerTextResource)
                    GooglePayDividerUi(text)
                }
            }
        }
    }

    private fun setupGooglePayButton() {
        googlePayButton.setOnClickListener {
            viewModel.checkoutWithGooglePay()
        }

        viewModel.googlePayButtonState.launchAndCollectIn(this) { viewState ->
            updateErrorMessage(topMessage, viewState?.errorMessage)
            googlePayButton.updateState(viewState?.convert())
        }
    }

    override fun setActivityResult(result: PaymentSheetResult) {
        setResult(
            Activity.RESULT_OK,
            Intent()
                .putExtras(PaymentSheetContract.Result(result).toBundle())
        )
    }

    override fun onDestroy() {
        if (!earlyExitDueToIllegalState) {
            viewModel.unregisterFromActivity()
        }
        super.onDestroy()
    }

    /**
     * Convert a [PaymentSheetViewState] to a [PrimaryButton.State]
     */
    private fun PaymentSheetViewState.convert(): PrimaryButton.State {
        return when (this) {
            is PaymentSheetViewState.Reset ->
                PrimaryButton.State.Ready
            is PaymentSheetViewState.StartProcessing ->
                PrimaryButton.State.StartProcessing
            is PaymentSheetViewState.FinishProcessing ->
                PrimaryButton.State.FinishProcessing(this.onComplete)
        }
    }

    private fun finishWithError(error: Throwable?) {
        val e = error ?: defaultInitializationError()
        setActivityResult(PaymentSheetResult.Failed(e))
        finish()
    }

    private fun defaultInitializationError(): IllegalArgumentException {
        return IllegalArgumentException("PaymentSheet started without arguments.")
    }

    internal companion object {
        internal const val EXTRA_STARTER_ARGS = BaseSheetActivity.EXTRA_STARTER_ARGS
    }
}
