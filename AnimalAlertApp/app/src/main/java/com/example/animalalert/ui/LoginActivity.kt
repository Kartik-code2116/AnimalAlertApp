package com.example.animalalert.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.animalalert.databinding.ActivityLoginBinding
import com.example.animalalert.model.LoginRequest
import com.example.animalalert.network.RetrofitClient
import com.example.animalalert.utils.PreferenceManager
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.example.animalalert.model.AuthResponse

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var preferenceManager: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferenceManager = PreferenceManager(this)

        // Check if already logged in
        if (preferenceManager.isLoggedIn()) {
            navigateToMain()
            return
        }

        // Sync RetrofitClient with saved server URL before any call
        RetrofitClient.setBaseUrl(preferenceManager.getServerUrl())

        binding.btnLogin.setOnClickListener {
            performLogin()
        }

        binding.btnBiometric.setOnClickListener {
            Toast.makeText(this, "Biometric Authentication (mock)", Toast.LENGTH_SHORT).show()
        }

        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun performLogin() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.length < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)

        RetrofitClient.api.login(LoginRequest(email, password))
            .enqueue(object : Callback<AuthResponse> {
                override fun onResponse(call: Call<AuthResponse>, response: Response<AuthResponse>) {
                    setLoading(false)
                    val body = response.body()
                    if (response.isSuccessful && body?.status == "success") {
                        preferenceManager.setLoggedIn(true)
                        preferenceManager.setAuthToken(body.token)
                        preferenceManager.saveUserData(
                            body.user?.name ?: email,
                            email,
                            ""
                        )
                        Toast.makeText(this@LoginActivity, "Login successful!", Toast.LENGTH_SHORT).show()
                        navigateToMain()
                    } else {
                        val msg = body?.message ?: "Invalid credentials"
                        Toast.makeText(this@LoginActivity, msg, Toast.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<AuthResponse>, t: Throwable) {
                    setLoading(false)
                    // Fallback: allow offline login so the app still works without server
                    android.util.Log.w("LoginActivity", "Server unreachable: ${t.message}")
                    Toast.makeText(
                        this@LoginActivity,
                        "Server unreachable — using offline mode",
                        Toast.LENGTH_LONG
                    ).show()
                    preferenceManager.setLoggedIn(true)
                    preferenceManager.saveUserData(email, email, "")
                    navigateToMain()
                }
            })
    }

    private fun setLoading(loading: Boolean) {
        binding.btnLogin.isEnabled = !loading
        binding.btnLogin.text = if (loading) "Logging in…" else "Login"
        // Show/hide progress if the layout has a progress bar (graceful fallback)
        try {
            val progressBar = binding.root.findViewWithTag<View>("progressBar")
            progressBar?.visibility = if (loading) View.VISIBLE else View.GONE
        } catch (_: Exception) {}
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
