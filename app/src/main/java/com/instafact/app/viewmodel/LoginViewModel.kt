package com.instafact.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.instafact.app.data.repository.AuthRepository
import com.instafact.app.data.repository.ProfileRepository
import com.instafact.app.data.model.UserProfileResponse
import com.instafact.app.data.model.UserProfileUpdateRequest
import com.instafact.app.utils.Analytics
import com.instafact.app.utils.Countries
import com.instafact.app.utils.Country
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class LoginStep {
    PHONE,
    OTP,
}

data class LoginUiModel(
    val step: LoginStep = LoginStep.PHONE,
    val country: Country = Countries.default(),
    val phoneNumber: String = "",
    val infoMessage: String? = null,
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
    val resendSecondsRemaining: Int = 0,
) {
    val isOtpStep: Boolean
        get() = step == LoginStep.OTP

    val canResend: Boolean
        get() = isOtpStep && !isLoading && resendSecondsRemaining <= 0
}

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableLiveData(LoginUiModel())
    val uiState: LiveData<LoginUiModel> = _uiState

    private val _loginComplete = MutableLiveData(false)
    val loginComplete: LiveData<Boolean> = _loginComplete
    private val _profileCompletionRequired = MutableLiveData<UserProfileResponse?>(null)
    val profileCompletionRequired: LiveData<UserProfileResponse?> = _profileCompletionRequired

    private var countdownJob: Job? = null

    fun selectCountry(country: Country) {
        _uiState.value = _uiState.value?.copy(country = country, errorMessage = null)
    }

    /** Null when the number is acceptable for the selected country, else the reason. */
    fun validatePhoneNumber(rawPhoneNumber: String, country: Country): String? {
        val digits = rawPhoneNumber.filter { it.isDigit() }
        val expected = country.nationalLength
        return when {
            digits.isEmpty() -> "Enter your mobile number."
            expected != null && digits.length != expected ->
                "Enter a valid $expected-digit number for ${country.name}."
            expected == null && digits.length !in Countries.FALLBACK_LENGTH_RANGE ->
                "Enter a valid mobile number for ${country.name}."
            else -> null
        }
    }

    fun requestOtp(rawPhoneNumber: String) {
        val country = _uiState.value?.country ?: Countries.default()
        val phoneNumber = rawPhoneNumber.filter { it.isDigit() }
        val validationError = validatePhoneNumber(phoneNumber, country)
        if (validationError != null) {
            _uiState.value = _uiState.value?.copy(
                step = LoginStep.PHONE,
                phoneNumber = phoneNumber,
                errorMessage = validationError,
                infoMessage = null,
                isLoading = false,
            )
            return
        }

        _uiState.value = _uiState.value?.copy(
            step = LoginStep.PHONE,
            phoneNumber = phoneNumber,
            errorMessage = null,
            infoMessage = null,
            isLoading = true,
        )
        viewModelScope.launch {
            authRepository.requestOtp(phoneNumber, country.dialCode)
                .onSuccess {
                    Analytics.logOtpRequested(country.dialCode, isResend = false)
                    startOtpStep(phoneNumber, it.message)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value?.copy(
                        step = LoginStep.PHONE,
                        phoneNumber = phoneNumber,
                        isLoading = false,
                        errorMessage = error.message.orEmpty(),
                    )
                }
        }
    }

    fun resendOtp() {
        val country = _uiState.value?.country ?: Countries.default()
        val phoneNumber = _uiState.value?.phoneNumber?.trim().orEmpty()
        if (validatePhoneNumber(phoneNumber, country) != null) {
            _uiState.value = _uiState.value?.copy(
                step = LoginStep.PHONE,
                errorMessage = "Enter a valid phone number.",
            )
            return
        }

        _uiState.value = _uiState.value?.copy(
            isLoading = true,
            errorMessage = null,
        )
        viewModelScope.launch {
            authRepository.resendOtp(phoneNumber, country.dialCode)
                .onSuccess {
                    Analytics.logOtpRequested(country.dialCode, isResend = true)
                    startOtpStep(phoneNumber, it.message)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value?.copy(
                        isLoading = false,
                        errorMessage = error.message.orEmpty(),
                    )
                }
        }
    }

    fun verifyOtp(rawOtp: String) {
        val country = _uiState.value?.country ?: Countries.default()
        val otp = rawOtp.filter { it.isDigit() }
        val phoneNumber = _uiState.value?.phoneNumber?.trim().orEmpty()

        if (validatePhoneNumber(phoneNumber, country) != null) {
            _uiState.value = _uiState.value?.copy(
                step = LoginStep.PHONE,
                errorMessage = "Enter a valid phone number.",
                infoMessage = null,
            )
            return
        }

        if (otp.length != OTP_LENGTH) {
            _uiState.value = _uiState.value?.copy(
                step = LoginStep.OTP,
                errorMessage = "Enter the $OTP_LENGTH-digit code sent to your phone.",
                infoMessage = null,
            )
            return
        }

        _uiState.value = _uiState.value?.copy(
            isLoading = true,
            errorMessage = null,
        )

        viewModelScope.launch {
            authRepository.verifyOtp(phoneNumber, country.dialCode, otp)
                .onSuccess {
                    Analytics.logOtpVerified(success = true)
                    // The id has to be set before any later event, otherwise this session's
                    // activity is attributed to an anonymous user in GA.
                    Analytics.setUserId(it.userId)
                    Analytics.setUserProperties(
                        countryCode = country.dialCode,
                        gender = null,
                        ageGroup = null,
                    )
                    Analytics.logLoginCompleted()
                    // Covers the case where Firebase had not issued a token yet at sign-in,
                    // so verify-otp sent none and the server could not push to this device.
                    authRepository.ensureFcmTokenRegistered()
                    fetchProfileAfterLogin()
                }
                .onFailure { error ->
                    Analytics.logOtpVerified(success = false, failureReason = error.message)
                    _uiState.value = _uiState.value?.copy(
                        step = LoginStep.OTP,
                        isLoading = false,
                        errorMessage = error.message.orEmpty(),
                    )
                }
        }
    }

    fun editPhoneNumber() {
        stopCountdown()
        _uiState.value = _uiState.value?.copy(
            step = LoginStep.PHONE,
            infoMessage = null,
            errorMessage = null,
            isLoading = false,
            resendSecondsRemaining = 0,
        )
    }

    fun markLoginCompleteHandled() {
        _loginComplete.value = false
    }

    fun completeProfile(
        fullName: String,
        gender: String,
        ageGroup: String,
    ) {
        _uiState.value = _uiState.value?.copy(
            isLoading = true,
            errorMessage = null,
        )
        viewModelScope.launch {
            profileRepository.updateProfile(
                UserProfileUpdateRequest(
                    name = fullName.trim(),
                    gender = gender,
                    ageGroup = ageGroup,
                ),
            ).onSuccess {
                // Demographics arrive here, not at sign-in, so this is where they can
                // start segmenting reports.
                Analytics.setUserProperties(
                    countryCode = null,
                    gender = gender,
                    ageGroup = ageGroup,
                )
                Analytics.logProfileCompleted(skipped = false)
                _uiState.value = _uiState.value?.copy(
                    isLoading = false,
                    errorMessage = null,
                )
                _profileCompletionRequired.value = null
                _loginComplete.value = true
            }.onFailure { error ->
                _uiState.value = _uiState.value?.copy(
                    isLoading = false,
                    errorMessage = error.message.orEmpty(),
                )
            }
        }
    }

    fun skipProfileCompletion() {
        Analytics.logProfileCompleted(skipped = true)
        _profileCompletionRequired.value = null
        _loginComplete.value = true
    }

    fun markProfileCompletionHandled() {
        _profileCompletionRequired.value = null
    }

    private fun startOtpStep(phoneNumber: String, message: String) {
        _uiState.value = _uiState.value?.copy(
            step = LoginStep.OTP,
            phoneNumber = phoneNumber,
            infoMessage = message,
            errorMessage = null,
            isLoading = false,
            resendSecondsRemaining = RESEND_COOLDOWN_SECONDS,
        )
        startCountdown(RESEND_COOLDOWN_SECONDS)
    }

    private fun fetchProfileAfterLogin() {
        _uiState.value = _uiState.value?.copy(
            isLoading = true,
            errorMessage = null,
        )
        viewModelScope.launch {
            profileRepository.getProfile()
                .onSuccess { profile ->
                    _uiState.value = _uiState.value?.copy(
                        isLoading = false,
                        errorMessage = null,
                    )
                    if (profile.name.isNullOrBlank() || profile.gender.isNullOrBlank()) {
                        _profileCompletionRequired.value = profile
                    } else {
                        _loginComplete.value = true
                    }
                }
                .onFailure {
                    _uiState.value = _uiState.value?.copy(
                        isLoading = false,
                        errorMessage = null,
                    )
                    _loginComplete.value = true
                }
        }
    }

    private fun startCountdown(totalSeconds: Int) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (remaining in totalSeconds downTo 0) {
                val current = _uiState.value ?: LoginUiModel()
                _uiState.postValue(
                    current.copy(
                        resendSecondsRemaining = remaining,
                    ),
                )
                if (remaining > 0) {
                    delay(1_000)
                }
            }
        }
    }

    private fun stopCountdown() {
        countdownJob?.cancel()
        countdownJob = null
    }

    override fun onCleared() {
        stopCountdown()
        super.onCleared()
    }

    companion object {
        /** MessageCentral is configured to send 4-digit codes (otpLength=4). */
        const val OTP_LENGTH = 4
        private const val RESEND_COOLDOWN_SECONDS = 120
    }
}
