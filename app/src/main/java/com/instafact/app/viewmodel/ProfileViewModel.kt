package com.instafact.app.viewmodel

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.instafact.app.data.model.UserProfileUpdateRequest
import com.instafact.app.data.model.UserProfileResponse
import com.instafact.app.data.repository.ProfileRepository
import com.instafact.app.utils.UiState
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _profileState = MutableLiveData<UiState<UserProfileResponse>>(UiState.Idle)
    val profileState: LiveData<UiState<UserProfileResponse>> = _profileState
    private val _updateProfileState = MutableLiveData<UiState<UserProfileResponse>>(UiState.Idle)
    val updateProfileState: LiveData<UiState<UserProfileResponse>> = _updateProfileState
    private val _uploadProfileImageState = MutableLiveData<UiState<String>>(UiState.Idle)
    val uploadProfileImageState: LiveData<UiState<String>> = _uploadProfileImageState

    fun loadProfile() {
        _profileState.value = UiState.Loading
        viewModelScope.launch {
            profileRepository.getProfile()
                .onSuccess { _profileState.value = UiState.Success(it) }
                .onFailure { _profileState.value = UiState.Error(it.message.orEmpty()) }
        }
    }

    fun updateProfile(request: UserProfileUpdateRequest) {
        _updateProfileState.value = UiState.Loading
        viewModelScope.launch {
            profileRepository.updateProfile(request)
                .onSuccess {
                    _profileState.value = UiState.Success(it)
                    _updateProfileState.value = UiState.Success(it)
                }
                .onFailure {
                    _updateProfileState.value = UiState.Error(it.message.orEmpty())
                }
        }
    }

    fun resetUpdateProfileState() {
        _updateProfileState.value = UiState.Idle
    }

    fun uploadProfileImage(imageUri: Uri) {
        _uploadProfileImageState.value = UiState.Loading
        viewModelScope.launch {
            profileRepository.uploadProfileImage(imageUri)
                .onSuccess { _uploadProfileImageState.value = UiState.Success(it) }
                .onFailure { _uploadProfileImageState.value = UiState.Error(it.message.orEmpty()) }
        }
    }

    fun resetUploadProfileImageState() {
        _uploadProfileImageState.value = UiState.Idle
    }

    fun getPhoneNumber(): String? = profileRepository.getPhoneNumber()

    fun getProfileName(): String? = profileRepository.getProfileName()

    fun logout() {
        profileRepository.logout()
    }
}
