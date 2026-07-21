package com.instafact.app.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.instafact.app.InstafactApplication
import com.instafact.app.R
import com.instafact.app.data.model.UserProfileResponse
import com.instafact.app.data.api.SessionExpiryHandler
import com.instafact.app.databinding.ActivityLoginBinding
import com.instafact.app.databinding.DialogCompleteProfileBinding
import com.instafact.app.ui.home.HomeActivity
import com.instafact.app.utils.IntentExtras
import com.instafact.app.utils.ViewModelFactory
import com.instafact.app.utils.applySystemBarInsets
import com.instafact.app.utils.configureSystemBars
import com.instafact.app.viewmodel.LoginUiModel
import com.instafact.app.viewmodel.LoginViewModel

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var completionDialogBinding: DialogCompleteProfileBinding? = null

    private val viewModel: LoginViewModel by viewModels {
        ViewModelFactory((application as InstafactApplication).appContainer)
    }

    private val sharedUrl: String?
        get() = intent.getStringExtra(IntentExtras.EXTRA_SHARED_URL)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionExpiryHandler.resetRedirectState()

        val preferenceManager = (application as InstafactApplication).appContainer.preferenceManager
        if (preferenceManager.isLoggedIn()) {
            navigateToHome()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureSystemBars(
            statusBarColorRes = R.color.brand_surface,
            navigationBarColorRes = R.color.brand_surface,
            lightStatusBar = true,
        )
        binding.contentRoot.applySystemBarInsets(applyTop = true)

        binding.requestOtpButton.setOnClickListener {
            viewModel.requestOtp(binding.phoneEditText.text.toString())
        }
        binding.verifyOtpButton.setOnClickListener {
            viewModel.verifyOtp(binding.otpEditText.text.toString())
        }
        binding.resendOtpButton.setOnClickListener { viewModel.resendOtp() }
        binding.changeNumberButton.setOnClickListener { viewModel.editPhoneNumber() }
        binding.instagramLoginButton.setOnClickListener {
            viewModel.requestOtp(binding.phoneEditText.text.toString())
        }

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

        viewModel.profileCompletionRequired.observe(this) { profile ->
            if (profile != null) {
                showProfileCompletionDialog(profile)
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

        if (intent.getBooleanExtra(IntentExtras.EXTRA_SESSION_EXPIRED, false)
            && state.errorMessage.isNullOrBlank()
            && state.infoMessage.isNullOrBlank()
        ) {
            binding.helperTextView.visibility = View.VISIBLE
            binding.helperTextView.text = getString(R.string.session_expired_message)
        }

        binding.otpContainer.visibility = if (state.isOtpStep) View.VISIBLE else View.GONE
        binding.requestOtpButton.visibility = if (state.isOtpStep) View.GONE else View.VISIBLE
        binding.verifyOtpButton.visibility = if (state.isOtpStep) View.VISIBLE else View.GONE
        binding.phoneInputLayout.isEnabled = !state.isLoading && !state.isOtpStep
        binding.phoneEditText.isEnabled = !state.isLoading && !state.isOtpStep
        binding.otpInputLayout.isEnabled = !state.isLoading
        binding.requestOtpButton.isEnabled = !state.isLoading
        binding.verifyOtpButton.isEnabled = !state.isLoading
        binding.changeNumberButton.isEnabled = !state.isLoading
        binding.resendOtpButton.isEnabled = state.canResend

        binding.stepTextView.text = getString(
            if (state.isOtpStep) R.string.otp_title else R.string.login_form_title,
        )

        binding.resendCountdownTextView.visibility =
            if (state.isOtpStep && state.resendSecondsRemaining > 0) View.VISIBLE else View.GONE
        binding.resendCountdownTextView.text = formatCountdown(state.resendSecondsRemaining)
    }

    private fun formatCountdown(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return getString(
            R.string.resend_countdown,
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

    private fun showProfileCompletionDialog(profile: UserProfileResponse) {
        if (completionDialogBinding != null) return

        val dialogBinding = DialogCompleteProfileBinding.inflate(layoutInflater)
        completionDialogBinding = dialogBinding
        val genderOptions = genderOptions()
        val ageGroupOptions = ageGroupOptions()

        setupDropdown(dialogBinding.genderAutoCompleteTextView, genderOptions.map { it.first })
        setupDropdown(dialogBinding.ageGroupAutoCompleteTextView, ageGroupOptions.map { it.first })

        dialogBinding.nameEditText.setText(profile.name.orEmpty())
        dialogBinding.genderAutoCompleteTextView.setText(
            genderOptions.firstOrNull { it.second == profile.gender }?.first.orEmpty(),
            false,
        )
        dialogBinding.ageGroupAutoCompleteTextView.setText(
            ageGroupOptions.firstOrNull { it.second == profile.ageGroup }?.first.orEmpty(),
            false,
        )

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.profile_completion_title)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .setNegativeButton(R.string.profile_complete_skip) { _, _ ->
                completionDialogBinding = null
                viewModel.skipProfileCompletion()
            }
            .setPositiveButton(R.string.profile_complete_now, null)
            .create()

        dialog.setOnDismissListener {
            completionDialogBinding = null
        }

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val fullName = dialogBinding.nameEditText.text?.toString()?.trim().orEmpty()
                val gender = genderOptions.firstOrNull {
                    it.first == dialogBinding.genderAutoCompleteTextView.text?.toString().orEmpty()
                }?.second
                val ageGroup = ageGroupOptions.firstOrNull {
                    it.first == dialogBinding.ageGroupAutoCompleteTextView.text?.toString().orEmpty()
                }?.second

                when {
                    fullName.isBlank() -> {
                        dialogBinding.nameEditText.error = getString(R.string.profile_name_required)
                    }

                    gender.isNullOrBlank() -> {
                        dialogBinding.genderAutoCompleteTextView.error = getString(R.string.profile_gender_required)
                    }

                    ageGroup.isNullOrBlank() -> {
                        dialogBinding.ageGroupAutoCompleteTextView.error = getString(R.string.profile_age_group_required)
                    }

                    else -> {
                        dialogBinding.nameEditText.error = null
                        dialogBinding.genderAutoCompleteTextView.error = null
                        dialogBinding.ageGroupAutoCompleteTextView.error = null
                        viewModel.completeProfile(
                            fullName = fullName,
                            gender = gender,
                            ageGroup = ageGroup,
                        )
                    }
                }
            }
        }

        dialog.show()
    }

    private fun setupDropdown(view: AutoCompleteTextView, options: List<String>) {
        view.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, options))
    }

    private fun genderOptions(): List<Pair<String, String>> = listOf(
        "Male" to "male",
        "Female" to "female",
        "Other" to "other",
        "Prefer not to say" to "prefer_not_to_say",
    )

    private fun ageGroupOptions(): List<Pair<String, String>> = listOf(
        "Under 18" to "under_18",
        "18-24" to "18_24",
        "25-34" to "25_34",
        "35-44" to "35_44",
        "45-54" to "45_54",
        "55+" to "55_plus",
    )
}
