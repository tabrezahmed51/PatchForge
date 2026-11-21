package com.example.data.remote

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlin.math.min

private const val WS_TAG = "WorkerWsClient"

sealed interface WsConnectionState {
    data object Disconnected : WsConnectionState
    data object Connecting : WsConnectionState
    data class Connected(val endpoint: String) : WsConnectionState
    data class Reconnecting(val attempt: Int, val lastError: String) : WsConnectionState
    data class Error(val message: String) : WsConnectionState
}

class WorkerWebSocketClient(
    private val scope: CoroutineScope
) {
    private val _connectionState = MutableStateFlow<WsConnectionState>(WsConnectionState.Disconnected)
    val connectionState: StateFlow<WsConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableStateFlow<List<WorkerEvent>>(emptyList())
    val events: StateFlow<List<WorkerEvent>> = _events.asStateFlow()

    private var activeWebSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var manualDisconnect = false
    private var currentBaseUrl: String = ""
    private var currentToken: String = ""
    private var reconnectAttempt = 0

    private val eventAdapter = FastApiClientFactory.moshi.adapter(WorkerEvent::class.java)

    fun connect(baseUrl: String, bearerToken: String, path: String = "/ws/events") {
        manualDisconnect = false
        reconnectJob?.cancel()
        activeWebSocket?.close(1000, "Reconnecting")
        currentBaseUrl = baseUrl.trim().trimEnd('/')
        currentToken = bearerToken.trim()
        reconnectAttempt = 0
        openSocket(path)
    }

    private fun openSocket(path: String) {
        val wsUrl = when {
            currentBaseUrl.startsWith("https://") -> "wss://" + currentBaseUrl.removePrefix("https://") + path
            currentBaseUrl.startsWith("http://") -> "ws://" + currentBaseUrl.removePrefix("http://") + path
            else -> {
                _connectionState.value = WsConnectionState.Error("Invalid FastAPI URL scheme. Use http:// or https://")
                return
            }
        }

        _connectionState.value = WsConnectionState.Connecting
        appendEvent(
            WorkerEvent(
                eventType = "CLIENT",
                worker = "ws-bridge",
                message = "Dialing worker stream $wsUrl"
            )
        )

        val client = FastApiClientFactory.buildOkHttpClient(currentToken)
        val request = Request.Builder().url(wsUrl).build()

        activeWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempt = 0
                _connectionState.value = WsConnectionState.Connected(wsUrl)
                appendEvent(
                    WorkerEvent(
                        eventType = "CONNECTED",
                        worker = "fastapi-ws",
                        message = "Subscribed to background worker telemetry stream."
                    )
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val parsed = runCatching { eventAdapter.fromJson(text) }.getOrNull()
                    ?: WorkerEvent(
                        eventType = "WORKER_LOG",
                        worker = "fastapi-ws",
                        message = text
                    )
                appendEvent(parsed)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                if (!manualDisconnect) {
                    scheduleReconnect(path, "Closed ($code): $reason")
                } else {
                    _connectionState.value = WsConnectionState.Disconnected
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val reason = t.localizedMessage ?: "Connection refused"
                Log.w(WS_TAG, "WebSocket failure: $reason")
                appendEvent(
                    WorkerEvent(
                        eventType = "TRANSPORT_ERR",
                        worker = "ws-bridge",
                        message = "Stream unreachable: $reason"
                    )
                )
                if (!manualDisconnect) {
                    scheduleReconnect(path, reason)
                } else {
                    _connectionState.value = WsConnectionState.Disconnected
                }
            }
        })
    }

    private fun scheduleReconnect(path: String, lastError: String) {
        if (reconnectAttempt >= 4) {
            _connectionState.value = WsConnectionState.Error("Backend stream offline: $lastError")
            return
        }
        reconnectAttempt += 1
        val backoffMs = min(15_000L, 1500L * (1L shl (reconnectAttempt - 1)))
        _connectionState.value = WsConnectionState.Reconnecting(reconnectAttempt, lastError)
        reconnectJob?.cancel()
        reconnectJob = scope.launch(Dispatchers.IO) {
            delay(backoffMs)
            if (!manualDisconnect) {
                openSocket(path)
            }
        }
    }

    fun disconnect() {
        manualDisconnect = true
        reconnectJob?.cancel()
        activeWebSocket?.close(1000, "Operator disconnected")
        activeWebSocket = null
        _connectionState.value = WsConnectionState.Disconnected
        appendEvent(
            WorkerEvent(
                eventType = "CLIENT",
                worker = "ws-bridge",
                message = "Worker telemetry stream disconnected by operator."
            )
        )
    }

    fun recordLocalTelemetry(worker: String, eventType: String, message: String, jobId: String = "") {
        appendEvent(
            WorkerEvent(
                eventType = eventType,
                worker = worker,
                jobId = jobId,
                message = message
            )
        )
    }

    private fun appendEvent(event: WorkerEvent) {
        val updated = (_events.value + event).takeLast(80)
        _events.value = updated
    }
}
