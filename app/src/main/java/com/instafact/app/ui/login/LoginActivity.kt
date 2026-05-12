package com.instafact.app.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.instafact.app.InstafactApplication
import com.instafact.app.databinding.ActivityLoginBinding
import com.instafact.app.ui.home.HomeActivity
import com.instafact.app.utils.IntentExtras
import com.instafact.app.utils.ViewModelFactory
import com.instafact.app.viewmodel.LoginViewModel
import com.instafact.app.viewmodel.LoginUiModel

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    private val viewModel: LoginViewModel by viewModels {
        ViewModelFactory((application as InstafactApplication).appContainer)
    }

    private val sharedUrl: String?
        get() = intent.getStringExtra(IntentExtras.EXTRA_SHARED_URL)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val preferenceManager = (application as InstafactApplication).appContainer.preferenceManager
        if (preferenceManager.isLoggedIn()) {
            navigateToHome()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.requestOtpButton.setOnClickListener {
            viewModel.requestOtp(binding.phoneEditText.text.toString())
        }
        binding.verifyOtpButton.setOnClickListener {
            viewModel.verifyOtp(binding.otpEditText.text.toString())
        }
        binding.resendOtpButton.setOnClickListener { viewModel.resendOtp() }
        binding.changeNumberButton.setOnClickListener { viewModel.editPhoneNumber() }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            renderState(state)
        }

        viewModel.loginComplete.observe(this) { isComplete ->
            if (isComplete == true) {
                viewModel.markLoginCompleteHandled()
                navigateToHome()
            }
        }
    }

    private fun renderState(state: LoginUiModel) {
        if (binding.phoneEditText.text?.toString() != state.phoneNumber) {
            binding.phoneEditText.setText(state.phoneNumber)
            binding.phoneEditText.setSelection(binding.phoneEditText.text?.length ?: 0)
        }

        binding.loginProgressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        binding.errorTextView.visibility = if (state.errorMessage.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.errorTextView.text = state.errorMessage

        binding.helperTextView.visibility = if (state.infoMessage.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.helperTextView.text = state.infoMessage

        binding.otpContainer.visibility = if (state.isOtpStep) View.VISIBLE else View.GONE
        binding.requestOtpButton.visibility = if (state.isOtpStep) View.GONE else View.VISIBLE
        binding.phoneInputLayout.isEnabled = !state.isLoading && !state.isOtpStep
        binding.otpInputLayout.isEnabled = !state.isLoading
        binding.requestOtpButton.isEnabled = !state.isLoading
        binding.verifyOtpButton.isEnabled = !state.isLoading
        binding.changeNumberButton.isEnabled = !state.isLoading
        binding.resendOtpButton.isEnabled = state.canResend

        binding.stepTextView.text = getString(
            if (state.isOtpStep) com.instafact.app.R.string.login_step_otp
            else com.instafact.app.R.string.login_step_phone,
        )
        binding.formTitleTextView.text = getString(
            if (state.isOtpStep) com.instafact.app.R.string.otp_title
            else com.instafact.app.R.string.login_form_title,
        )
        binding.formSubtitleTextView.text = getString(
            if (state.isOtpStep) com.instafact.app.R.string.otp_subtitle
            else com.instafact.app.R.string.login_form_subtitle,
        )

        binding.resendCountdownTextView.visibility =
            if (state.isOtpStep && state.resendSecondsRemaining > 0) View.VISIBLE else View.GONE
        binding.resendCountdownTextView.text = formatCountdown(state.resendSecondsRemaining)
    }

    private fun formatCountdown(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return getString(
            com.instafact.app.R.string.resend_countdown,
            String.format("%02d:%02d", minutes, seconds),
        )
    }

    private fun navigateToHome() {
        startActivity(
            Intent(this, HomeActivity::class.java).apply {
                sharedUrl?.let { putExtra(IntentExtras.EXTRA_SHARED_URL, it) }
            },
        )
        finish()
    }
}
