package com.example.filemanager

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. `HiltAndroidApp` enables Hilt dependency injection across the app
 * (ViewModels, repositories, AuthManager, etc.).
 */
@HiltAndroidApp
class FileManagerApp : Application()
