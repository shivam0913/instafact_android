package com.instafact.app.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.instafact.app.InstafactApplication
import com.instafact.app.R
import com.instafact.app.data.model.UserProfileResponse
import com.instafact.app.data.model.UserProfileUpdateRequest
import com.instafact.app.databinding.DialogEditProfileBinding
import com.instafact.app.databinding.FragmentProfileBinding
import com.instafact.app.ui.support.HelpSupportActivity
import com.instafact.app.ui.splash.SplashActivity
import com.instafact.app.utils.UiState
import com.instafact.app.utils.ViewModelFactory
import com.instafact.app.viewmodel.ProfileViewModel
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private var latestProfile: UserProfileResponse? = null
    private var editDialogBinding: DialogEditProfileBinding? = null
    private var pendingCameraImageUri: Uri? = null
    private var editingProfileImageUrl: String? = null

    private val viewModel: ProfileViewModel by viewModels {
        ViewModelFactory((requireActivity().application as InstafactApplication).appContainer)
    }

    private val galleryPickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { viewModel.uploadProfileImage(it) }
        }

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                pendingCameraImageUri?.let { viewModel.uploadProfileImage(it) }
            } else {
                pendingCameraImageUri = null
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        renderFallbackProfile()

        binding.shareFriendsButton.setOnClickListener {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            getString(R.string.share_friends_message, getString(R.string.app_download_link)),
                        )
                    },
                    getString(R.string.share_with_friends),
                ),
            )
        }
        binding.writeToUsButton.setOnClickListener {
            val emailIntent = Intent(
                Intent.ACTION_SENDTO,
                Uri.parse("mailto:connect@instafact.co"),
            ).apply {
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.write_to_us_subject))
            }
            runCatching {
                startActivity(
                    Intent.createChooser(
                        emailIntent,
                        getString(R.string.write_to_us),
                    ),
                )
            }.onFailure {
                Toast.makeText(requireContext(), getString(R.string.no_email_app), Toast.LENGTH_SHORT).show()
            }
        }
        binding.helpSupportButton.setOnClickListener {
            startActivity(Intent(requireContext(), HelpSupportActivity::class.java))
        }
        binding.privacyButton.setOnClickListener {
            showPrivacyPolicyDialog()
        }
        binding.logoutButton.setOnClickListener {
            viewModel.logout()
            Toast.makeText(requireContext(), getString(R.string.logged_out), Toast.LENGTH_SHORT).show()
            startActivity(
                Intent(requireContext(), SplashActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                },
            )
            requireActivity().finish()
        }
        binding.profileRetryButton.setOnClickListener { viewModel.loadProfile() }
        binding.editProfileButton.setOnClickListener { showEditProfileDialog() }

        observeProfile()
        viewModel.loadProfile()
    }

    private fun observeProfile() {
        viewModel.profileState.observe(viewLifecycleOwner) { state ->
            when (state) {
                UiState.Idle -> Unit
                UiState.Loading -> {
                    binding.profileStatusTextView.text = getString(R.string.profile_loading)
                    binding.profileErrorContainer.isVisible = false
                }

                is UiState.Success -> {
                    latestProfile = state.data
                    bindProfile(state.data)
                    binding.profileStatusTextView.text = getString(R.string.profile_subtitle)
                    binding.profileErrorContainer.isVisible = false
                }

                is UiState.Error -> {
                    binding.profileStatusTextView.text = getString(R.string.profile_subtitle)
                    binding.profileErrorContainer.isVisible = true
                    binding.profileErrorTextView.text = state.message
                }
            }
        }

        viewModel.updateProfileState.observe(viewLifecycleOwner) { state ->
            when (state) {
                UiState.Idle -> Unit
                UiState.Loading -> binding.editProfileButton.isEnabled = false
                is UiState.Success -> {
                    binding.editProfileButton.isEnabled = true
                    Toast.makeText(requireContext(), getString(R.string.profile_update_success), Toast.LENGTH_SHORT).show()
                    viewModel.resetUpdateProfileState()
                }

                is UiState.Error -> {
                    binding.editProfileButton.isEnabled = true
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    viewModel.resetUpdateProfileState()
                }
            }
        }

        viewModel.uploadProfileImageState.observe(viewLifecycleOwner) { state ->
            val dialogBinding = editDialogBinding ?: return@observe
            when (state) {
                UiState.Idle -> {
                    dialogBinding.choosePhotoButton.isEnabled = true
                    dialogBinding.choosePhotoButton.text = getString(R.string.profile_choose_photo)
                }

                UiState.Loading -> {
                    dialogBinding.choosePhotoButton.isEnabled = false
                    dialogBinding.choosePhotoButton.text = getString(R.string.profile_photo_uploading)
                }

                is UiState.Success -> {
                    dialogBinding.choosePhotoButton.isEnabled = true
                    dialogBinding.choosePhotoButton.text = getString(R.string.profile_choose_photo)
                    editingProfileImageUrl = state.data
                    renderEditProfilePhoto(dialogBinding, state.data)
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.profile_photo_upload_success),
                        Toast.LENGTH_SHORT,
                    ).show()
                    viewModel.resetUploadProfileImageState()
                }

                is UiState.Error -> {
                    dialogBinding.choosePhotoButton.isEnabled = true
                    dialogBinding.choosePhotoButton.text = getString(R.string.profile_choose_photo)
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    viewModel.resetUploadProfileImageState()
                }
            }
        }
    }

    private fun renderFallbackProfile() {
        val phoneNumber = viewModel.getPhoneNumber().orEmpty()
        binding.displayNameTextView.text = getString(R.string.profile_display_name)
        binding.handleTextView.text = getString(R.string.profile_handle)
        binding.avatarTextView.text = binding.displayNameTextView.text.firstOrNull()?.uppercase() ?: "I"
        binding.avatarImageView.load(null)
        binding.nameValueTextView.text = getString(R.string.profile_not_set)
        binding.genderValueTextView.text = getString(R.string.profile_not_set)
        binding.ageGroupValueTextView.text = getString(R.string.profile_not_set)
        binding.phoneValueTextView.text = phoneNumber.ifBlank { "-" }
        binding.factChecksValueTextView.text = "0"
        binding.memberSinceValueTextView.text = getString(R.string.profile_unknown_member_since)
        binding.referralCodeValueTextView.text = "-"
        binding.referralCodeDetailsValueTextView.text = "-"
    }

    private fun bindProfile(profile: UserProfileResponse) {
        val displayName = profile.name?.takeIf { it.isNotBlank() } ?: profile.phoneNumber
        binding.displayNameTextView.text = displayName
        binding.handleTextView.text = "@${profile.referralCode.lowercase(Locale.US)}"
        binding.avatarTextView.text = displayName.firstOrNull()?.uppercase() ?: "I"
        binding.avatarImageView.load(profile.profileImageUrl) {
            crossfade(true)
        }
        binding.avatarTextView.isVisible = profile.profileImageUrl.isNullOrBlank()
        binding.nameValueTextView.text = profile.name?.takeIf { it.isNotBlank() } ?: getString(R.string.profile_not_set)
        binding.genderValueTextView.text = profile.gender.toDisplayLabel()
        binding.ageGroupValueTextView.text = profile.ageGroup.toDisplayLabel()
        binding.phoneValueTextView.text = profile.phoneNumber
        binding.factChecksValueTextView.text = profile.factCheckedContentCount.toString()
        binding.memberSinceValueTextView.text = formatMemberSince(profile.memberSince)
        binding.referralCodeValueTextView.text = profile.referralCode
        binding.referralCodeDetailsValueTextView.text = profile.referralCode
    }

    private fun showEditProfileDialog() {
        val profile = latestProfile
        if (profile == null) {
            Toast.makeText(requireContext(), getString(R.string.profile_loading), Toast.LENGTH_SHORT).show()
            return
        }

        val dialogBinding = DialogEditProfileBinding.inflate(layoutInflater)
        editDialogBinding = dialogBinding
        val ageGroupOptions = ageGroupOptions()

        setupDropdown(dialogBinding.ageGroupAutoCompleteTextView, ageGroupOptions.map { it.first })

        editingProfileImageUrl = profile.profileImageUrl
        dialogBinding.nameEditText.setText(profile.name.orEmpty())
        renderEditProfilePhoto(dialogBinding, profile.profileImageUrl)
        applyGenderSelection(dialogBinding, profile.gender)
        dialogBinding.ageGroupAutoCompleteTextView.setText(
            ageGroupOptions.firstOrNull { it.second == profile.ageGroup }?.first ?: getString(R.string.profile_not_set),
            false,
        )
        dialogBinding.choosePhotoButton.setOnClickListener { showPhotoSourceChooser() }
        dialogBinding.photoActionButton.setOnClickListener { showPhotoSourceChooser() }
        dialogBinding.nameEditText.doAfterTextChanged {
            renderEditProfilePhoto(dialogBinding, editingProfileImageUrl)
        }

        dialogBinding.genderMaleButton.setOnClickListener {
            applyGenderSelection(dialogBinding, "male")
        }
        dialogBinding.genderFemaleButton.setOnClickListener {
            applyGenderSelection(dialogBinding, "female")
        }
        dialogBinding.genderPreferNotButton.setOnClickListener {
            applyGenderSelection(dialogBinding, "other")
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialog.setOnDismissListener {
            editDialogBinding = null
            pendingCameraImageUri = null
            editingProfileImageUrl = null
        }

        dialogBinding.discardChangesButton.setOnClickListener { dialog.dismiss() }
        dialogBinding.saveProfileButton.setOnClickListener {
            val updateRequest = UserProfileUpdateRequest(
                name = dialogBinding.nameEditText.text?.toString()?.trim().orEmpty().ifBlank { null },
                gender = selectedGender(dialogBinding),
                ageGroup = ageGroupOptions.firstOrNull {
                    it.first == dialogBinding.ageGroupAutoCompleteTextView.text?.toString().orEmpty()
                }?.second,
                profileImageUrl = editingProfileImageUrl,
            )
            viewModel.updateProfile(updateRequest)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showPhotoSourceChooser() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.profile_choose_photo_title)
            .setItems(
                arrayOf(
                    getString(R.string.profile_photo_camera),
                    getString(R.string.profile_photo_gallery),
                    getString(R.string.profile_photo_remove),
                ),
            ) { _, which ->
                when (which) {
                    0 -> launchCameraPicker()
                    1 -> galleryPickerLauncher.launch("image/*")
                    2 -> {
                        editingProfileImageUrl = null
                        editDialogBinding?.let { renderEditProfilePhoto(it, null) }
                    }
                }
            }
            .show()
    }

    private fun launchCameraPicker() {
        val cacheDirectory = File(requireContext().cacheDir, "camera").apply { mkdirs() }
        val imageFile = File.createTempFile("profile_", ".jpg", cacheDirectory)
        val imageUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            imageFile,
        )
        pendingCameraImageUri = imageUri
        runCatching {
            cameraLauncher.launch(imageUri)
        }.onFailure {
            pendingCameraImageUri = null
            Toast.makeText(requireContext(), getString(R.string.profile_camera_unavailable), Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatMemberSince(value: String): String {
        return runCatching {
            val formatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)
            OffsetDateTime.parse(value).format(formatter)
        }.getOrElse {
            getString(R.string.profile_unknown_member_since)
        }
    }

    private fun showPrivacyPolicyDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.privacy_policy_title)
            .setMessage(getString(R.string.privacy_policy_body))
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun setupDropdown(view: AutoCompleteTextView, options: List<String>) {
        view.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, options))
    }

    private fun renderEditProfilePhoto(dialogBinding: DialogEditProfileBinding, imageUrl: String?) {
        dialogBinding.profilePhotoPreviewImageView.load(imageUrl) {
            crossfade(true)
        }
        dialogBinding.profilePhotoInitialTextView.isVisible = imageUrl.isNullOrBlank()
        dialogBinding.profilePhotoInitialTextView.text =
            dialogBinding.nameEditText.text?.toString()?.trim()?.firstOrNull()?.uppercase() ?: "I"
    }

    private fun applyGenderSelection(dialogBinding: DialogEditProfileBinding, gender: String?) {
        updateGenderButton(dialogBinding.genderMaleButton, gender == "male", filled = false)
        updateGenderButton(dialogBinding.genderFemaleButton, gender == "female", filled = false)
        updateGenderButton(dialogBinding.genderPreferNotButton, gender == "other", filled = true)
    }

    private fun selectedGender(dialogBinding: DialogEditProfileBinding): String? {
        return when {
            dialogBinding.genderMaleButton.tag == true -> "male"
            dialogBinding.genderFemaleButton.tag == true -> "female"
            dialogBinding.genderPreferNotButton.tag == true -> "other"
            else -> null
        }
    }

    private fun updateGenderButton(
        button: com.google.android.material.button.MaterialButton,
        selected: Boolean,
        filled: Boolean,
    ) {
        button.tag = selected
        if (filled) {
            button.setBackgroundColor(
                if (selected) requireContext().getColor(R.color.brand_primary)
                else requireContext().getColor(R.color.brand_primary_soft),
            )
            button.setTextColor(
                if (selected) requireContext().getColor(android.R.color.white)
                else requireContext().getColor(R.color.brand_primary),
            )
            button.iconTint = android.content.res.ColorStateList.valueOf(
                if (selected) requireContext().getColor(android.R.color.white)
                else requireContext().getColor(R.color.brand_primary),
            )
        } else {
            button.setBackgroundColor(
                if (selected) requireContext().getColor(R.color.brand_primary_soft)
                else requireContext().getColor(android.R.color.white),
            )
            button.strokeColor = android.content.res.ColorStateList.valueOf(
                if (selected) requireContext().getColor(R.color.brand_primary)
                else requireContext().getColor(R.color.brand_border),
            )
            button.setTextColor(
                if (selected) requireContext().getColor(R.color.brand_primary)
                else requireContext().getColor(R.color.brand_text),
            )
            button.iconTint = android.content.res.ColorStateList.valueOf(
                if (selected) requireContext().getColor(R.color.brand_primary)
                else requireContext().getColor(R.color.brand_text),
            )
        }
    }

    private fun genderOptions(): List<Pair<String, String?>> = listOf(
        getString(R.string.profile_not_set) to null,
        "Male" to "male",
        "Female" to "female",
        "Other" to "other",
        "Prefer not to say" to "prefer_not_to_say",
    )

    private fun ageGroupOptions(): List<Pair<String, String?>> = listOf(
        getString(R.string.profile_not_set) to null,
        "Under 18" to "under_18",
        "18-24" to "18_24",
        "25-34" to "25_34",
        "35-44" to "35_44",
        "45-54" to "45_54",
        "55+" to "55_plus",
    )

    private fun String?.toDisplayLabel(): String {
        return when (this) {
            "male" -> "Male"
            "female" -> "Female"
            "other" -> "Other"
            "prefer_not_to_say" -> "Prefer not to say"
            "under_18" -> "Under 18"
            "18_24" -> "18-24"
            "25_34" -> "25-34"
            "35_44" -> "35-44"
            "45_54" -> "45-54"
            "55_plus" -> "55+"
            else -> getString(R.string.profile_not_set)
        }
    }

    override fun onDestroyView() {
        editDialogBinding = null
        _binding = null
        super.onDestroyView()
    }
}
