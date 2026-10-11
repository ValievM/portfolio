package com.example.phoneagent.di

import android.content.Context
import com.example.phoneagent.auth.ApiKeyAuthFactory
import com.example.phoneagent.auth.AuthCoordinator
import com.example.phoneagent.auth.MutableApiKeyAuthProvider
import com.example.phoneagent.auth.SessionAuthCoordinator
import com.example.phoneagent.data.auth.ApiKeyAuth
import com.example.phoneagent.data.auth.AuthConfig
import com.example.phoneagent.data.auth.AuthMethod
import com.example.phoneagent.data.auth.AuthProvider
import com.example.phoneagent.data.auth.BrowserLauncher
import com.example.phoneagent.data.auth.CallbackReceiver
import com.example.phoneagent.data.auth.ChatGptBrowserAuth
import com.example.phoneagent.data.auth.ChatGptDeviceCodeAuth
import com.example.phoneagent.data.auth.CustomTabsBrowserLauncher
import com.example.phoneagent.data.auth.DeviceCodeGateway
import com.example.phoneagent.data.auth.EncryptedTokenStore
import com.example.phoneagent.data.auth.LoopbackCallbackReceiver
import com.example.phoneagent.data.auth.OkHttpDeviceCodeGateway
import com.example.phoneagent.data.auth.OkHttpTokenRefreshGateway
import com.example.phoneagent.data.auth.SessionManager
import com.example.phoneagent.data.auth.TokenRefreshGateway
import com.example.phoneagent.data.auth.TokenRefresher
import com.example.phoneagent.data.auth.TokenStore
import com.example.phoneagent.data.llm.BackendProfile
import com.example.phoneagent.data.llm.OkHttpApiKeyValidationGateway
import com.example.phoneagent.domain.auth.ApiKeyValidationGateway
import com.example.phoneagent.domain.auth.CredentialsProvider
import com.example.phoneagent.domain.platform.AppLogger
import com.example.phoneagent.domain.platform.Clock
import com.example.phoneagent.domain.platform.DispatcherProvider
import com.example.phoneagent.domain.platform.RedactingAppLogger
import com.example.phoneagent.logging.AndroidLogcatLogger
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AuthBindingsModule {
    @Binds
    @Singleton
    abstract fun bindAuthCoordinator(implementation: SessionAuthCoordinator): AuthCoordinator

    @Binds
    abstract fun bindCredentialsProvider(implementation: SessionManager): CredentialsProvider
}

@Module
@InstallIn(SingletonComponent::class)
internal object AuthNetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient()

    @Provides
    fun provideAuthConfig(): AuthConfig = AuthConfig.ChatGptSubscription

    @Provides
    fun provideBackendProfile(): BackendProfile = BackendProfile.ChatGptSubscription

    @Provides
    @Singleton
    fun provideTokenGateway(
        config: AuthConfig,
        logger: AppLogger,
        client: OkHttpClient,
        dispatchers: DispatcherProvider,
    ): TokenRefreshGateway = OkHttpTokenRefreshGateway(config, logger, client, dispatchers = dispatchers)

    @Provides
    @Singleton
    fun provideApiKeyValidationGateway(
        profile: BackendProfile,
        client: OkHttpClient,
        dispatchers: DispatcherProvider,
    ): ApiKeyValidationGateway = OkHttpApiKeyValidationGateway(profile, client, dispatchers)

    @Provides
    @Singleton
    fun provideDeviceCodeGateway(
        config: AuthConfig,
        client: OkHttpClient,
        dispatchers: DispatcherProvider,
    ): DeviceCodeGateway = OkHttpDeviceCodeGateway(config, client, dispatchers = dispatchers)
}

@Module
@InstallIn(SingletonComponent::class)
internal object AuthRuntimeModule {
    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Provides
    fun provideClock(): Clock = Clock(System::currentTimeMillis)

    @Provides
    @Singleton
    fun provideTokenStore(
        @ApplicationContext context: Context,
        logger: AppLogger,
    ): TokenStore = EncryptedTokenStore(context, logger)

    @Provides
    @Singleton
    fun provideCallbackReceiver(
        logger: AppLogger,
        dispatchers: DispatcherProvider,
    ): CallbackReceiver = LoopbackCallbackReceiver(logger, dispatchers = dispatchers)

    @Provides
    @Singleton
    fun provideBrowserLauncher(
        @ApplicationContext context: Context,
    ): BrowserLauncher = CustomTabsBrowserLauncher(context)

    @Provides
    @Singleton
    fun provideBrowserAuth(
        config: AuthConfig,
        receiver: CallbackReceiver,
        launcher: BrowserLauncher,
        tokenGateway: TokenRefreshGateway,
        logger: AppLogger,
    ): ChatGptBrowserAuth = ChatGptBrowserAuth(config, receiver, launcher, tokenGateway, logger)

    @Provides
    @Singleton
    fun provideDeviceCodeAuth(
        config: AuthConfig,
        deviceGateway: DeviceCodeGateway,
        tokenGateway: TokenRefreshGateway,
    ): ChatGptDeviceCodeAuth = ChatGptDeviceCodeAuth(config, deviceGateway, tokenGateway)

    @Provides
    @Singleton
    fun provideApiKeyAuthFactory(validationGateway: ApiKeyValidationGateway): ApiKeyAuthFactory =
        ApiKeyAuthFactory { key -> ApiKeyAuth(key, validationGateway) }

    @Provides
    @Singleton
    fun provideTokenRefresher(
        tokenStore: TokenStore,
        gateway: TokenRefreshGateway,
        clock: Clock,
    ): TokenRefresher = TokenRefresher(tokenStore, gateway, clock)
}

// Kept separate from AuthRuntimeModule purely to stay under detekt's function-count threshold for
// that object; there is no architectural reason these couldn't live together.
@Module
@InstallIn(SingletonComponent::class)
internal object SessionAssemblyModule {
    // AndroidLogcatLogger is never bound as AppLogger directly: RedactingAppLogger must always sit
    // in front of it so nothing can log a raw token, key or auth code by accident.
    @Provides
    @Singleton
    fun provideAppLogger(
        @ApplicationContext context: Context,
    ): AppLogger = RedactingAppLogger(AndroidLogcatLogger(context))

    @Provides
    @Singleton
    fun provideAuthProviders(
        browserAuth: ChatGptBrowserAuth,
        deviceCodeAuth: ChatGptDeviceCodeAuth,
        apiKeyAuth: MutableApiKeyAuthProvider,
    ): Map<AuthMethod, AuthProvider> =
        mapOf(
            AuthMethod.CHATGPT_BROWSER to browserAuth,
            AuthMethod.CHATGPT_DEVICE_CODE to deviceCodeAuth,
            AuthMethod.API_KEY to apiKeyAuth,
        )

    @Provides
    @Singleton
    fun provideSessionManager(
        providers: Map<AuthMethod, @JvmSuppressWildcards AuthProvider>,
        tokenRefresher: TokenRefresher,
        logger: AppLogger,
    ): SessionManager = SessionManager(providers, tokenRefresher, logger)
}
