package com.example.filemanager.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.filemanager.utils.AuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authManager: AuthManager
) : ViewModel() {
    private val _authSuccess = MutableLiveData<Boolean>()
    val authSuccess: LiveData<Boolean> = _authSuccess

    fun signUp(name: String, email: String, password: String) {
        authManager.signUp(name.trim(), email.trim(), password)
        _authSuccess.value = true
    }

    fun login(email: String, password: String) {
        _authSuccess.value = authManager.login(email.trim(), password)
    }

    fun isLoggedIn(): Boolean = authManager.isLoggedIn()
}
