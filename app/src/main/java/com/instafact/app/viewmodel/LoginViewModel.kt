package com.instafact.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.instafact.app.data.repository.AuthRepository
import com.instafact.app.data.repository.ProfileRepository
import com.instafact.app.data.model.UserProfileResponse
import com.instafact.app.data.model.UserProfileUpdateRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class LoginStep {
    PHONE,
    OTP,
}

data class LoginUiModel(
    val step: LoginStep = LoginStep.PHONE,
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

    fun requestOtp(rawPhoneNumber: String) {
        val phoneNumber = rawPhoneNumber.trim()
        if (phoneNumber.length < MIN_PHONE_LENGTH) {
            _uiState.value = _uiState.value?.copy(
                step = LoginStep.PHONE,
                phoneNumber = phoneNumber,
                errorMessage = "Enter a valid phone number.",
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
            authRepository.requestOtp(phoneNumber)
                .onSuccess {
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
        val phoneNumber = _uiState.value?.phoneNumber?.trim().orEmpty()
        if (phoneNumber.length < MIN_PHONE_LENGTH) {
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
            authRepository.resendOtp(phoneNumber)
                .onSuccess {
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
        val otp = rawOtp.trim()
        val phoneNumber = _uiState.value?.phoneNumber?.trim().orEmpty()

        if (phoneNumber.length < MIN_PHONE_LENGTH) {
            _uiState.value = _uiState.value?.copy(
                step = LoginStep.PHONE,
                errorMessage = "Enter a valid phone number.",
                infoMessage = null,
            )
            return
        }

        if (otp.length < MIN_OTP_LENGTH) {
            _uiState.value = _uiState.value?.copy(
                step = LoginStep.OTP,
                errorMessage = "Enter the OTP sent to your phone.",
                infoMessage = null,
            )
            return
        }

        _uiState.value = _uiState.value?.copy(
            isLoading = true,
            errorMessage = null,
        )

        viewModelScope.launch {
            authRepository.verifyOtp(phoneNumber, otp)
                .onSuccess {
                    fetchProfileAfterLogin()
                }
                .onFailure { error ->
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
        private const val MIN_PHONE_LENGTH = 7
        private const val MIN_OTP_LENGTH = 4
        private const val RESEND_COOLDOWN_SECONDS = 120
    }
}
