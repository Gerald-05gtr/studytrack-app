
package com.studytrack.app

import com.google.firebase.auth.FirebaseAuth

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    val isLoggedIn: Boolean
        get() = auth.currentUser != null

    val currentEmail: String
        get() = auth.currentUser?.email.orEmpty()

    fun observeAuth(
        onChanged: (Boolean) -> Unit
    ): FirebaseAuth.AuthStateListener {
        val listener = FirebaseAuth.AuthStateListener {
            onChanged(it.currentUser != null)
        }
        auth.addAuthStateListener(listener)
        return listener
    }

    fun stopObserving(
        listener: FirebaseAuth.AuthStateListener
    ) {
        auth.removeAuthStateListener(listener)
    }

    fun register(
        email: String,
        password: String,
        onResult: (Boolean) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                onResult(task.isSuccessful)
            }
    }

    fun login(
        email: String,
        password: String,
        onResult: (Boolean) -> Unit
    ) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                onResult(task.isSuccessful)
            }
    }

    fun logout() {
        auth.signOut()
    }
}
