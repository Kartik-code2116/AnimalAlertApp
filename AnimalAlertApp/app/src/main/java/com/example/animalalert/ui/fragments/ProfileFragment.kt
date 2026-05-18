package com.example.animalalert.ui.fragments

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.animalalert.utils.PreferenceManager
import com.example.animalalert.R
import com.example.animalalert.databinding.FragmentProfileBinding
import com.example.animalalert.ui.LoginActivity
import com.example.animalalert.ui.SettingsActivity

import java.text.SimpleDateFormat
import java.util.*

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var preferenceManager: PreferenceManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferenceManager = PreferenceManager(requireContext())
        
        loadUserData()
        setupViews()
    }

    private fun loadUserData() {
        binding.tvUserName.text = preferenceManager.getUserName().ifEmpty { "User" }
        binding.tvUserEmail.text = preferenceManager.getUserEmail()
        binding.tvUserPhone.text = preferenceManager.getUserPhone().ifEmpty { "Not provided" }
        
        val totalDetections = preferenceManager.getTotalDetections()
        val todayDetections = preferenceManager.getTodayDetections()
        
        binding.tvTotalDetections.text = totalDetections.toString()
        binding.tvTodayDetections.text = todayDetections.toString()
        
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        binding.tvLastLogin.text = dateFormat.format(Date())
        
        // Load notification settings
        loadNotificationSettings()
    }
    
    private fun loadNotificationSettings() {
        binding.switchNotifications.isChecked = preferenceManager.isNotificationEnabled()
        binding.switchInAppNotifications.isChecked = preferenceManager.isInAppNotificationEnabled()
        binding.switchEmailNotifications.isChecked = preferenceManager.isEmailNotificationEnabled()
        binding.switchSmsNotifications.isChecked = preferenceManager.isSmsNotificationEnabled()
        
        // Enable/disable sub-switches based on main switch
        updateSubSwitchesState(binding.switchNotifications.isChecked)
    }
    
    private fun updateSubSwitchesState(enabled: Boolean) {
        binding.switchInAppNotifications.isEnabled = enabled
        binding.switchEmailNotifications.isEnabled = enabled
        binding.switchSmsNotifications.isEnabled = enabled
    }

    private fun setupViews() {
        binding.btnLogout.setOnClickListener {
            logout()
        }

        binding.btnEditProfile.setOnClickListener {
            showEditProfileDialog()
        }

        binding.btnSettings.setOnClickListener {
            val intent = Intent(requireContext(), SettingsActivity::class.java)
            startActivity(intent)
        }

        binding.btnRefreshStats.setOnClickListener {
            loadUserData()
            Toast.makeText(context, "Statistics refreshed", Toast.LENGTH_SHORT).show()
        }
        
        // Notification settings listeners
        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            preferenceManager.setNotificationEnabled(isChecked)
            updateSubSwitchesState(isChecked)
            if (!isChecked) {
                // Disable all sub-switches when main switch is off
                binding.switchInAppNotifications.isChecked = false
                binding.switchEmailNotifications.isChecked = false
                binding.switchSmsNotifications.isChecked = false
                preferenceManager.setInAppNotificationEnabled(false)
                preferenceManager.setEmailNotificationEnabled(false)
                preferenceManager.setSmsNotificationEnabled(false)
            }
            Toast.makeText(context, if (isChecked) "Notifications enabled" else "Notifications disabled", Toast.LENGTH_SHORT).show()
        }
        
        binding.switchInAppNotifications.setOnCheckedChangeListener { _, isChecked ->
            preferenceManager.setInAppNotificationEnabled(isChecked)
            Toast.makeText(context, if (isChecked) "In-app notifications enabled" else "In-app notifications disabled", Toast.LENGTH_SHORT).show()
        }
        
        binding.switchEmailNotifications.setOnCheckedChangeListener { _, isChecked ->
            preferenceManager.setEmailNotificationEnabled(isChecked)
            if (isChecked && preferenceManager.getUserEmail().isEmpty()) {
                Toast.makeText(context, "Please add your email address", Toast.LENGTH_LONG).show()
                binding.switchEmailNotifications.isChecked = false
                preferenceManager.setEmailNotificationEnabled(false)
            } else {
                Toast.makeText(context, if (isChecked) "Email notifications enabled" else "Email notifications disabled", Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.switchSmsNotifications.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (preferenceManager.getUserPhone().isEmpty()) {
                    Toast.makeText(context, "Please add your phone number", Toast.LENGTH_LONG).show()
                    binding.switchSmsNotifications.isChecked = false
                    return@setOnCheckedChangeListener
                }
                
                // Request SMS permission
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.SEND_SMS) 
                    != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.SEND_SMS), SMS_PERMISSION_REQUEST_CODE)
                    binding.switchSmsNotifications.isChecked = false
                    return@setOnCheckedChangeListener
                }
            }
            
            preferenceManager.setSmsNotificationEnabled(isChecked)
            Toast.makeText(context, if (isChecked) "SMS notifications enabled" else "SMS notifications disabled", Toast.LENGTH_SHORT).show()
        }
        
        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST_CODE)
            }
        }
    }

    private fun logout() {
        preferenceManager.clearAll()
        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    private fun showEditProfileDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_profile, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        val etName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etName)
        val etEmail = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEmail)
        val etPhone = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPhone)
        val btnSave = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSave)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)

        // Pre-fill with current data
        etName.setText(preferenceManager.getUserName())
        etEmail.setText(preferenceManager.getUserEmail())
        etPhone.setText(preferenceManager.getUserPhone())

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val phone = etPhone.text.toString().trim()

            if (name.isEmpty()) {
                etName.error = "Name is required"
                return@setOnClickListener
            }

            if (email.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.error = "Invalid email format"
                return@setOnClickListener
            }

            // Save to preferences
            preferenceManager.saveUserData(name, email, phone)

            // Refresh UI
            loadUserData()

            Toast.makeText(context, "Profile updated successfully", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            SMS_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    binding.switchSmsNotifications.isChecked = true
                    preferenceManager.setSmsNotificationEnabled(true)
                    Toast.makeText(context, "SMS permission granted. Notifications enabled.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "SMS permission denied. Cannot send SMS notifications.", Toast.LENGTH_LONG).show()
                }
            }
            NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(context, "Notification permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Notification permission denied. Some features may not work.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        private const val SMS_PERMISSION_REQUEST_CODE = 2001
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 2002
    }
}

