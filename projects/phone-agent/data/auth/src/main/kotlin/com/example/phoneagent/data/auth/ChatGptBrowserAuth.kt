package com.example.phoneagent.data.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.phoneagent.domain.platform.AppLogger
import com.example.phoneagent.domain.platform.DefaultDispatcherProvider
import com.example.phoneagent.domain.platform.DispatcherProvider
import com.example.phoneagent.domain.platform.error
import com.example.phoneagent.domain.platform.warn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.net.BindException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.net.URLEncoder

private const val UTF_8: String = "UTF-8"
private const val TAG: String = "ChatGptBrowserAuth"

public fun interface BrowserLauncher {
    public fun open(url: String)
}

public class CustomTabsBrowserLauncher(
    private val context: Context,
) : BrowserLauncher {
    // `context` here is the application context (see DI), not an Activity, so starting a new
    // screen from it requires FLAG_ACTIVITY_NEW_TASK or Android throws AndroidRuntimeException —
    // which the caller's broad catch then silently turned into a generic "network" failure.
    override fun open(url: String) {
        val customTabsIntent = CustomTabsIntent.Builder().build()
        customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        customTabsIntent.launchUrl(context, Uri.parse(url))
    }
}

public sealed interface CallbackReceiverStartResult {
    public data object Started : CallbackReceiverStartResult

    public data class Failure(
        public val failure: AuthFailure,
    ) : CallbackReceiverStartResult
}

public sealed interface CallbackResult {
    public data class Received(
        public val code: String?,
        public val state: String?,
    ) : CallbackResult

    public data object TimedOut : CallbackResult

    public data class Failure(
        public val failure: AuthFailure,
    ) : CallbackResult
}

public interface CallbackReceiver {
    public fun start(): CallbackReceiverStartResult

    public suspend fun awaitCallback(timeoutMillis: Long): CallbackResult

    public fun close()
}

public class LoopbackCallbackReceiver(
    private val logger: AppLogger,
    private val port: Int = LOOPBACK_PORT,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
) : CallbackReceiver {
    private var serverSocket: ServerSocket? = null

    override fun start(): CallbackReceiverStartResult =
        try {
            serverSocket =
                ServerSocket().also { socket ->
                    socket.reuseAddress = true
                    socket.bind(InetSocketAddress(InetAddress.getByName(LOOPBACK_HOST), port))
                }
            CallbackReceiverStartResult.Started
        } catch (_: BindException) {
            close()
            CallbackReceiverStartResult.Failure(AuthFailure.LoopbackPortUnavailable)
        } catch (exception: Exception) {
            // Unlike the port-busy case above, this is unexpected (e.g. a missing runtime
            // permission or an OS-level socket restriction) and worth a trace to diagnose.
            logger.warn(TAG, "failed to start the OAuth loopback receiver", throwable = exception)
            close()
            CallbackReceiverStartResult.Failure(AuthFailure.Network(null))
        }

    override suspend fun awaitCallback(timeoutMillis: Long): CallbackResult =
        withContext(dispatchers.io) {
            val socket = serverSocket ?: return@withContext CallbackResult.Failure(AuthFailure.Network(null))
            try {
                socket.soTimeout = timeoutMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                socket.accept().use { connection ->
                    val requestLine = connection.getInputStream().bufferedReader().readLine()
                    connection.getOutputStream().bufferedWriter().use(::writeResponse)
                    parseCallback(requestLine)
                }
            } catch (_: SocketTimeoutException) {
                CallbackResult.TimedOut
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                logger.warn(TAG, "OAuth loopback callback failed", throwable = exception)
                CallbackResult.Failure(AuthFailure.Network(null))
            }
        }

    override fun close() {
        serverSocket?.close()
        serverSocket = null
    }

    private fun parseCallback(requestLine: String?): CallbackResult {
        val requestTarget = requestLine?.split(' ')?.getOrNull(1) ?: return CallbackResult.Failure(AuthFailure.Network(null))
        val query = requestTarget.substringAfter('?', missingDelimiterValue = "")
        val parameters =
            query
                .split('&')
                .filter(String::isNotEmpty)
                .associate { part ->
                    val name = part.substringBefore('=')
                    val value = part.substringAfter('=', missingDelimiterValue = "")
                    URLDecoder.decode(name, UTF_8) to URLDecoder.decode(value, UTF_8)
                }
        return CallbackResult.Received(code = parameters["code"], state = parameters["state"])
    }

    private fun writeResponse(writer: BufferedWriter) {
        writer.write(CALLBACK_RESPONSE)
        writer.flush()
    }

    private companion object {
        private const val LOOPBACK_HOST: String = "127.0.0.1"
        private const val LOOPBACK_PORT: Int = 1455
        private const val CALLBACK_RESPONSE: String =
            "HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\n" +
                "Connection: close\r\n\r\nAuthorization completed. You can return to the app."
    }
}

public class ChatGptBrowserAuth(
    private val config: AuthConfig,
    private val callbackReceiver: CallbackReceiver,
    private val browserLauncher: BrowserLauncher,
    private val tokenGateway: TokenRefreshGateway,
    private val logger: AppLogger,
    private val entropy: AuthEntropy = SecureAuthEntropy,
    private val timeoutMillis: Long = CALLBACK_TIMEOUT_MILLIS,
    // Android can cut a backgrounded app's outgoing network access for as long as the user is
    // looking at the Custom Tab, which is exactly when the loopback callback arrives and this
    // exchange would otherwise fire — see the retry comment on OkHttpTokenRefreshGateway for the
    // observed evidence. Waiting here for the app to actually be back in the foreground avoids
    // racing a network window that isn't open yet.
    private val awaitForeground: suspend () -> Unit = { awaitProcessForeground() },
) : AuthProvider {
    override val method: AuthMethod = AuthMethod.CHATGPT_BROWSER

    override suspend fun signIn(): AuthResult {
        when (val start = callbackReceiver.start()) {
            is CallbackReceiverStartResult.Failure -> return AuthResult.Failure(start.failure)
            CallbackReceiverStartResult.Started -> Unit
        }

        val pkce = Pkce.create(entropy)
        return try {
            browserLauncher.open(authorizationUrl(pkce))
            when (val callback = callbackReceiver.awaitCallback(timeoutMillis)) {
                is CallbackResult.Failure -> {
                    AuthResult.Failure(callback.failure)
                }

                CallbackResult.TimedOut -> {
                    AuthResult.Failure(AuthFailure.TimedOut)
                }

                is CallbackResult.Received -> {
                    when {
                        callback.state != pkce.state -> {
                            AuthResult.Failure(AuthFailure.StateMismatch)
                        }

                        callback.code.isNullOrBlank() -> {
                            AuthResult.Failure(AuthFailure.MissingAuthorizationCode)
                        }

                        else -> {
                            awaitForeground()
                            tokenGateway
                                .exchangeAuthorizationCode(
                                    AuthorizationCodeExchange(
                                        code = callback.code,
                                        codeVerifier = pkce.verifier,
                                        redirectUri = config.redirectUri,
                                    ),
                                ).withMethod(AuthMethod.CHATGPT_BROWSER)
                        }
                    }
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            // This is exactly the catch that once hid a plain Android bug (missing
            // FLAG_ACTIVITY_NEW_TASK) behind a generic "network" error — always log the real
            // exception here so the next surprise doesn't cost an hour of guessing.
            logger.error(TAG, "browser sign-in threw an unexpected exception", throwable = exception)
            AuthResult.Failure(AuthFailure.Network(null))
        } finally {
            callbackReceiver.close()
        }
    }

    private fun authorizationUrl(pkce: PkceParameters): String {
        val parameters =
            config.authorizationParameters +
                mapOf(
                    "client_id" to config.clientId,
                    "redirect_uri" to config.redirectUri,
                    "scope" to config.scope,
                    "code_challenge" to pkce.challenge,
                    "state" to pkce.state,
                )
        return config.authorizationEndpoint +
            parameters.entries.joinToString(prefix = "?", separator = "&") { (name, value) ->
                "${encode(name)}=${encode(value)}"
            }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, UTF_8).replace("+", "%20")

    private companion object {
        private const val CALLBACK_TIMEOUT_MILLIS: Long = 3 * 60 * 1_000L
    }
}

private suspend fun awaitProcessForeground() {
    ProcessLifecycleOwner
        .get()
        .lifecycle.currentStateFlow
        .first { it.isAtLeast(Lifecycle.State.STARTED) }
}
