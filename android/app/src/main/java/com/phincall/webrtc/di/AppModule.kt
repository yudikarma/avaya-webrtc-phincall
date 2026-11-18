package com.phincall.webrtc.di

import android.app.Application
import android.content.Context
import com.phincall.webrtc.BuildConfig
import com.phincall.webrtc.auth.TokenRepository
import com.phincall.webrtc.config.CallHistoryStore
import com.phincall.webrtc.config.RuntimeConfigManager
import com.phincall.webrtc.config.SettingsStore
import com.phincall.webrtc.logging.DiagnosticLogManager
import com.phincall.webrtc.platform.NetworkMonitor
import com.phincall.webrtc.webrtc.InteractionCoordinator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideHardenedOkHttpClient(): OkHttpClient {
        val specs = if (BuildConfig.DEBUG) {
            listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT)
        } else {
            listOf(ConnectionSpec.MODERN_TLS)
        }
        return OkHttpClient.Builder()
            .connectionSpecs(specs)
            .followRedirects(false)
            .followSslRedirects(false)
            .cache(null)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideSettingsStore(@ApplicationContext context: Context): SettingsStore =
        SettingsStore(context)

    @Provides
    @Singleton
    fun provideCallHistoryStore(@ApplicationContext context: Context): CallHistoryStore =
        CallHistoryStore(context)

    @Provides
    @Singleton
    fun provideDiagnosticLogManager(@ApplicationContext context: Context): DiagnosticLogManager =
        DiagnosticLogManager(context)

    @Provides
    @Singleton
    fun provideRuntimeConfigManager(
        settingsStore: SettingsStore,
        diagnosticLogManager: DiagnosticLogManager
    ): RuntimeConfigManager = RuntimeConfigManager(settingsStore, diagnosticLogManager)

    @Provides
    @Singleton
    fun provideTokenRepository(httpClient: OkHttpClient): TokenRepository =
        TokenRepository(httpClient)

    @Provides
    @Singleton
    fun provideNetworkMonitor(@ApplicationContext context: Context): NetworkMonitor =
        NetworkMonitor(context)

    @Provides
    @Singleton
    fun provideInteractionCoordinator(
        application: Application,
        diagnosticLogManager: DiagnosticLogManager,
        callHistoryStore: CallHistoryStore
    ): InteractionCoordinator = InteractionCoordinator(
        application = application,
        diagnosticLogManager = diagnosticLogManager,
        callHistoryStore = callHistoryStore
    )
}
