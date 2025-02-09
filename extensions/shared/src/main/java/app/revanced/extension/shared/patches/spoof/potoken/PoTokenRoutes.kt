package app.revanced.extension.shared.patches.spoof.potoken

import app.revanced.extension.shared.requests.Requester
import app.revanced.extension.shared.requests.Route
import app.revanced.extension.shared.requests.Route.CompiledRoute
import java.io.IOException
import java.net.HttpURLConnection

@Suppress("deprecation")
object PoTokenRoutes {
    @JvmField
    val CREATE_CHALLENGE: CompiledRoute = Route(Route.Method.POST, "Create").compile()

    @JvmField
    val GENERATE_INTEGRITY_TOKEN: CompiledRoute = Route(Route.Method.POST, "GenerateIT").compile()

    private const val JNN_API_URL = "https://www.youtube.com/api/jnn/v1/"

    private const val GOOGLE_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"

    /**
     * TCP connection and HTTP read timeout
     */
    private const val CONNECTION_TIMEOUT_MILLISECONDS = 10 * 1000 // 10 Seconds.

    @Throws(IOException::class)
    fun getBotGuardConnectionFromRoute(
        route: CompiledRoute
    ): HttpURLConnection {
        val connection = Requester.getConnectionFromCompiledRoute(JNN_API_URL, route)
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Content-Type", "application/json+protobuf")
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("x-goog-api-key", GOOGLE_API_KEY)
        connection.setRequestProperty("x-user-agent", "grpc-web-javascript/0.1")

        connection.useCaches = false
        connection.doOutput = true

        connection.connectTimeout = CONNECTION_TIMEOUT_MILLISECONDS
        connection.readTimeout = CONNECTION_TIMEOUT_MILLISECONDS
        return connection
    }

}