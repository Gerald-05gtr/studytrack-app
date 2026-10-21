
package com.studytrack.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class AuthViewModel : ViewModel() {

    private val repository = AuthRepository()

    var loggedIn by mutableStateOf(repository.isLoggedIn)
        private set

    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var confirmPassword by mutableStateOf("")
    var registerMode by mutableStateOf(false)

    var loading by mutableStateOf(false)
        private set

    var error by mutableStateOf("")
        private set

    val currentEmail: String
        get() = repository.currentEmail

    private val authListener =
        repository.observeAuth { authenticated ->
            loggedIn = authenticated
        }

    fun switchMode() {
        registerMode = !registerMode
        error = ""
        password = ""
        confirmPassword = ""
    }

    fun submit() {
        if (loading) return

        val cleanEmail = email.trim()

        error = when {
            !android.util.Patterns.EMAIL_ADDRESS
                .matcher(cleanEmail).matches() ->
                "Enter a valid email address"

            password.length < 6 ->
                "Password must have at least 6 characters"

            registerMode && password != confirmPassword ->
                "Passwords do not match"

            else -> ""
        }

        if (error.isNotEmpty()) return

        loading = true

        val callback: (Boolean) -> Unit = { success ->
            loading = false

            if (success) {
                loggedIn = repository.isLoggedIn
                error = ""
                password = ""
                confirmPassword = ""
            } else {
                error = if (registerMode) {
                    "Registration failed. Check your details."
                } else {
                    "Login failed. Check your email and password."
                }
            }
        }

        if (registerMode) {
            repository.register(cleanEmail, password, callback)
        } else {
            repository.login(cleanEmail, password, callback)
        }
    }

    fun logout() {
        repository.logout()
        loggedIn = false
        password = ""
        confirmPassword = ""
        error = ""
    }

    override fun onCleared() {
        repository.stopObserving(authListener)
        super.onCleared()
    }
}
