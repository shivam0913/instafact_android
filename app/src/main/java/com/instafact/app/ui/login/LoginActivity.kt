package com.instafact.app.ui.login

import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.InputFilter
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.instafact.app.InstafactApplication
import com.instafact.app.R
import com.instafact.app.data.model.UserProfileResponse
import com.instafact.app.data.api.SessionExpiryHandler
import com.instafact.app.databinding.ActivityLoginBinding
import com.instafact.app.databinding.DialogCompleteProfileBinding
import com.instafact.app.ui.home.HomeActivity
import com.instafact.app.utils.Countries
import com.instafact.app.utils.Country
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

    private val otpBoxes: List<EditText> by lazy {
        listOf(
            binding.otpDigit1,
            binding.otpDigit2,
            binding.otpDigit3,
            binding.otpDigit4,
        )
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

        renderBrand()
        setupOtpBoxes()
        binding.otpSentToTextView.text = getString(R.string.otp_sent_to, LoginViewModel.OTP_LENGTH)

        binding.requestOtpButton.setOnClickListener {
            viewModel.requestOtp(binding.phoneEditText.text.toString())
        }
        binding.verifyOtpButton.setOnClickListener { viewModel.verifyOtp(otpValue()) }
        binding.resendOtpButton.setOnClickListener {
            if (binding.resendOtpButton.isEnabled) viewModel.resendOtp()
        }
        binding.changeNumberButton.setOnClickListener { viewModel.editPhoneNumber() }
        binding.otpBackButton.setOnClickListener { viewModel.editPhoneNumber() }
        binding.countryCodeTextView.setOnClickListener { showCountryPicker() }

        // Surface the length rule as the user types rather than only on submit.
        binding.phoneEditText.doAfterTextChanged { editable ->
            val country = viewModel.uiState.value?.country ?: Countries.default()
            val digits = editable?.toString().orEmpty().filter { it.isDigit() }
            val liveError = if (digits.isEmpty()) null else viewModel.validatePhoneNumber(digits, country)
            binding.phoneErrorTextView.isVisible = liveError != null
            binding.phoneErrorTextView.text = liveError.orEmpty()
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
        // Route the error to whichever step is on screen.
        val error = state.errorMessage.orEmpty()
        binding.phoneErrorTextView.isVisible = error.isNotBlank() && !state.isOtpStep
        binding.phoneErrorTextView.text = error
        binding.otpErrorTextView.isVisible = error.isNotBlank() && state.isOtpStep
        binding.otpErrorTextView.text = error

        binding.helperTextView.visibility = if (state.infoMessage.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.helperTextView.text = state.infoMessage

        if (intent.getBooleanExtra(IntentExtras.EXTRA_SESSION_EXPIRED, false)
            && error.isBlank()
            && state.infoMessage.isNullOrBlank()
        ) {
            binding.helperTextView.visibility = View.VISIBLE
            binding.helperTextView.text = getString(R.string.session_expired_message)
        }

        val wasOtpStep = binding.otpStepContainer.isVisible
        binding.phoneStepContainer.isVisible = !state.isOtpStep
        binding.otpStepContainer.isVisible = state.isOtpStep

        binding.phoneEditText.isEnabled = !state.isLoading
        setCtaEnabled(binding.requestOtpButton, !state.isLoading)
        setCtaEnabled(binding.verifyOtpButton, !state.isLoading)
        binding.changeNumberButton.isEnabled = !state.isLoading
        otpBoxes.forEach { it.isEnabled = !state.isLoading }

        renderCountry(state.country)
        binding.otpPhoneTextView.text = getString(
            R.string.login_phone_display,
            state.country.dialCode,
            state.phoneNumber,
        )

        binding.resendOtpButton.isEnabled = state.canResend
        binding.resendOtpButton.text = if (state.resendSecondsRemaining > 0) {
            formatCountdown(state.resendSecondsRemaining)
        } else {
            getString(R.string.otp_resend_now)
        }
        binding.resendOtpButton.setTextColor(
            ContextCompat.getColor(
                this,
                if (state.canResend) R.color.brand_primary else R.color.brand_muted,
            ),
        )

        if (state.isOtpStep && !wasOtpStep) {
            clearOtpBoxes()
            focusFirstEmptyOtpBox()
        }
        if (!state.isOtpStep && wasOtpStep) {
            clearOtpBoxes()
        }
    }

    private fun setCtaEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        view.alpha = if (enabled) 1f else 0.6f
    }

    private fun showCountryPicker() {
        val current = viewModel.uiState.value?.country ?: Countries.default()
        CountryPickerDialog.show(this, current) { country ->
            viewModel.selectCountry(country)
            binding.phoneEditText.text?.clear()
        }
    }

    private fun renderCountry(country: Country) {
        binding.countryCodeTextView.text = getString(
            R.string.login_country_code_format,
            country.flag,
            country.dialCode,
        )
        country.nationalLength?.let { length ->
            binding.phoneEditText.filters = arrayOf(InputFilter.LengthFilter(length))
        } ?: run {
            binding.phoneEditText.filters = arrayOf(InputFilter.LengthFilter(15))
        }
    }

    private fun renderBrand() {
        val brand = getString(R.string.app_name)
        val splitIndex = brand.indexOf("Fact")
        val span = SpannableString(brand)
        if (splitIndex in 1 until brand.length) {
            span.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(this, R.color.brand_primary)),
                splitIndex,
                brand.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        binding.loginBrandTextView.text = span
        binding.otpBrandTextView.text = span
    }

    /** Six single-character boxes that behave like one field: auto-advance, backspace, paste. */
    private fun setupOtpBoxes() {
        otpBoxes.forEachIndexed { index, box ->
            box.doAfterTextChanged { editable ->
                val text = editable?.toString().orEmpty()
                if (text.length > 1) {
                    distributeOtp(text, index)
                    return@doAfterTextChanged
                }
                if (text.isNotEmpty() && index < otpBoxes.lastIndex) {
                    otpBoxes[index + 1].requestFocus()
                }
                if (otpValue().length == otpBoxes.size) {
                    hideKeyboard()
                }
            }

            box.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DEL &&
                    event.action == KeyEvent.ACTION_DOWN &&
                    box.text.isEmpty() &&
                    index > 0
                ) {
                    otpBoxes[index - 1].apply {
                        setText("")
                        requestFocus()
                    }
                    true
                } else {
                    false
                }
            }
        }
    }

    /** Handles an SMS autofill or paste landing in a single box. */
    private fun distributeOtp(value: String, startIndex: Int) {
        val digits = value.filter { it.isDigit() }
        otpBoxes.forEachIndexed { index, box ->
            if (index >= startIndex) {
                val digitIndex = index - startIndex
                box.setText(digits.getOrNull(digitIndex)?.toString().orEmpty())
            }
        }
        focusFirstEmptyOtpBox()
    }

    private fun otpValue(): String = otpBoxes.joinToString("") { it.text?.toString().orEmpty() }

    private fun clearOtpBoxes() {
        otpBoxes.forEach { it.setText("") }
    }

    private fun focusFirstEmptyOtpBox() {
        val target = otpBoxes.firstOrNull { it.text.isNullOrEmpty() } ?: otpBoxes.last()
        target.requestFocus()
        target.setSelection(target.text?.length ?: 0)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    private fun formatCountdown(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return getString(
            R.string.otp_resend_in,
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
