package com.dexter.di

import android.app.Application
import android.content.Context
import com.dexter.DexterApp
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class)
class AppModuleTest {
    /** Every dependency a definition asks for must be defined, so a missing one fails here and not at launch. */
    @Test
    fun everyDefinitionHasItsDependencies() {
        appModule.verify(extraTypes = listOf(Context::class, Application::class, String::class, DexterApp::class))
    }
}
