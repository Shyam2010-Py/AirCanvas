package com.aircanvas.app.network

import okhttp3.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ConnectionStatus {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}

class AirCanvasProtocol {
    companion object {
        const val CMD_PEN = "W"
        const val CMD_ERASER = "B"
        const val CMD_LINE = "L"
        const val CMD_RECT = "R"
        const val CMD_CIRCLE = "C_S"
        const val CMD_CLEAR = "C"
        const val CMD_FORCE_CLEAR = "FC"
        const val CMD_DEMO_START = "D_S"
        const val CMD_DEMO_END = "D_E"
    }

    fun formatCommand(cmd: String, x1: Int = 0, y1: Int = 0, x2: Int = 0, y2: Int = 0, size: Int = 1): String {
        return when (cmd) {
            CMD_CLEAR, CMD_FORCE_CLEAR, CMD_DEMO_START, CMD_DEMO_END -> cmd
            CMD_LINE, CMD_RECT -> "$cmd,$x1,$y1,$x2,$y2"
            CMD_CIRCLE -> "$cmd,$x1,$y1,$size" // x, y, radius (size used as r)
            else -> "$cmd,$x1,$y1,$x2,$y2,$size" // Pen/Eraser: CMD,x1,y1,x2,y2,size
        }
    }
}

class ESP32WebSocketClient(private val protocol: AirCanvasProtocol, private val scope: CoroutineScope) {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private var isManualDisconnect = false
    
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus
    
    private var reconnectionJob: Job? = null

    fun connect(url: String) {
        isManualDisconnect = false
        _connectionStatus.value = ConnectionStatus.CONNECTING
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionStatus.value = ConnectionStatus.CONNECTED
                reconnectionJob?.cancel()
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                if (!isManualDisconnect) attemptReconnect(url)
            }

            override fun onFailure(webSocket: WebSocket, t: java.io.IOException) {
                _connectionStatus.value = ConnectionStatus.ERROR
                if (!isManualDisconnect) attemptReconnect(url)
            }
        })
    }

    private fun attemptReconnect(url: String) {
        reconnectionJob?.cancel()
        reconnectionJob = scope.launch {
            delay(3000)
            connect(url)
        }
    }

    fun disconnect() {
        isManualDisconnect = true
        reconnectionJob?.cancel()
        webSocket?.close(1000, "User disconnected")
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }

    fun sendCommand(cmd: String, x1: Int = 0, y1: Int = 0, x2: Int = 0, y2: Int = 0, size: Int = 1) {
        val payload = protocol.formatCommand(cmd, x1, y1, x2, y2, size)
        webSocket?.send(payload)
    }
}
