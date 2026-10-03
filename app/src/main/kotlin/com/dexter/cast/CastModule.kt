package com.dexter.cast

import com.dexter.di.BASE_CLIENT
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Cast wiring. Add to the modules list in DexterApp.startKoin:
 *     modules(appModule, castModule)
 */
val castModule = module {
    single { CastManager.create(androidContext(), get(named(BASE_CLIENT))) }
}
