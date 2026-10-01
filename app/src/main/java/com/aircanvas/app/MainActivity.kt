package com.aircanvas.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aircanvas.app.drawing.*
import com.aircanvas.app.network.*
import com.aircanvas.app.ui.theme.AirCanvasTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AirCanvasTheme {
                AirCanvasApp()
            }
        }
    }
}

@Composable
fun AirCanvasApp() {
    val scope = rememberCoroutineScope()
    val protocol = remember { AirCanvasProtocol() }
    val client = remember { ESP32WebSocketClient(protocol, scope) }
    val mapper = remember { CoordinateMapper() }
    
    var isConnected by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Disconnected") }
    var currentTool by remember { mutableStateOf(Tool.PEN) }
    var brushSize by remember { mutableIntStateOf(2) }
    var eraserSize by remember { mutableIntStateOf(7) }
    
    // Path and Drawing State
    val paths = remember { mutableStateListOf<Pair<Tool, List<Offset>>>() }
    var currentPathPoints = remember { mutableStateOf(listOf<Offset>()) }
    
    // Buffer State
    var pendingPoints by remember { mutableStateOf(listOf<Offset>()) }
    var lastBufferedPoint by remember { mutableStateOf<Offset?>(null) }

    // Actual size of the drawing canvas in Android pixels.
    var drawingCanvasSize by remember { mutableStateOf(IntSize.Zero) }

    val connectionStatus = client.connectionStatus.collectAsState()
    
    LaunchedEffect(connectionStatus.value) {
        statusText = when(connectionStatus.value) {
            ConnectionStatus.CONNECTED -> "Connected"
            ConnectionStatus.CONNECTING -> "Connecting..."
            ConnectionStatus.ERROR -> "Error"
            ConnectionStatus.DISCONNECTED -> "Disconnected"
        }
        isConnected = connectionStatus.value == ConnectionStatus.CONNECTED
    }

    // 20ms Buffer Logic for Pen/Eraser
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(20)
            if (pendingPoints.isNotEmpty()) {
                val pointsToProcess = pendingPoints
                pendingPoints = emptyList()
                
                // Use lastPoint from previous batch to prevent gaps
                var prev = lastBufferedPoint ?: pointsToProcess.first()
                
                for (point in pointsToProcess) {
                    val oledP1 = mapper.mapToOled(prev.x, prev.y, drawingCanvasSize.width.toFloat(), drawingCanvasSize.height.toFloat())
                    val oledP2 = mapper.mapToOled(point.x, point.y, drawingCanvasSize.width.toFloat(), drawingCanvasSize.height.toFloat())
                    
                    if (currentTool == Tool.PEN || currentTool == Tool.ERASER) {
                        val cmd = if (currentTool == Tool.ERASER) AirCanvasProtocol.CMD_ERASER else AirCanvasProtocol.CMD_PEN
                        val sizeVal = if (currentTool == Tool.ERASER) eraserSize else brushSize
                        client.sendCommand(cmd, oledP1.x.toInt(), oledP1.y.toInt(), oledP2.x.toInt(), oledP2.y.toInt(), sizeVal)
                    }
                    prev = point
                }
                lastBufferedPoint = pointsToProcess.last()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
            Text("ESP32 OLED AirCanvas", color = Color.White, fontSize = 24.sp)
            
            Row(modifier = Modifier.padding(vertical = 8.dp)) {
                ToolButtonFixed("Pen", Tool.PEN, currentTool) { currentTool = it }
                ToolButtonFixed("Eraser", Tool.ERASER, currentTool) { currentTool = it }
                ToolButtonFixed("Line", Tool.LINE, currentTool) { currentTool = it }
                ToolButtonFixed("Rect", Tool.RECTANGLE, currentTool) { currentTool = it }
                ToolButtonFixed("Circle", Tool.CIRCLE, currentTool) { currentTool = it }
            }

            Row(modifier = Modifier.padding(vertical = 8.dp)) {
                SystemButton("Clear") {
                    client.sendCommand(AirCanvasProtocol.CMD_CLEAR)
                    paths.clear()
                }
                SystemButton("Force") {
                    client.sendCommand(AirCanvasProtocol.CMD_FORCE_CLEAR)
                    paths.clear()
                }
            }

            // Drawing Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
                    .border(1.dp, Color.Gray)
                    .onSizeChanged { size ->
                        drawingCanvasSize = size
                    }
                    .pointerInput(currentTool, drawingCanvasSize) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentPathPoints.value = listOf(offset)
                                if (currentTool == Tool.PEN || currentTool == Tool.ERASER) {
                                    pendingPoints = listOf(offset)
                                    lastBufferedPoint = null
                                }
                            },
                            onDrag = { change, _ ->
                                val point = change.position
                                currentPathPoints.value = currentPathPoints.value + point
                                if (currentTool == Tool.PEN || currentTool == Tool.ERASER) {
                                    pendingPoints = pendingPoints + point
                                }
                            },
                            onDragEnd = {
                                val points = currentPathPoints.value
                                if (points.isNotEmpty()) {
                                    val start = points.first()
                                    val end = points.last()
                                    
                                    // Handle L, R, C_S commands on Drag End
                                    when (currentTool) {
                                        Tool.LINE -> {
                                            val p1 = mapper.mapToOled(start.x, start.y, drawingCanvasSize.width.toFloat(), drawingCanvasSize.height.toFloat())
                                            val p2 = mapper.mapToOled(end.x, end.y, drawingCanvasSize.width.toFloat(), drawingCanvasSize.height.toFloat())
                                            client.sendCommand(AirCanvasProtocol.CMD_LINE, p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt())
                                        }
                                        Tool.RECTANGLE -> {
                                            val p1 = mapper.mapToOled(start.x, start.y, drawingCanvasSize.width.toFloat(), drawingCanvasSize.height.toFloat())
                                            val p2 = mapper.mapToOled(end.x, end.y, drawingCanvasSize.width.toFloat(), drawingCanvasSize.height.toFloat())
                                            client.sendCommand(AirCanvasProtocol.CMD_RECT, p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt())
                                        }
                                        Tool.CIRCLE -> {
                                            val pCenter = mapper.mapToOled(start.x, start.y, drawingCanvasSize.width.toFloat(), drawingCanvasSize.height.toFloat())
                                            val pEnd = mapper.mapToOled(end.x, end.y, drawingCanvasSize.width.toFloat(), drawingCanvasSize.height.toFloat())
                                            val radius = (Math.sqrt(Math.pow((pEnd.x - pCenter.x).toDouble(), 2.0) + Math.pow((pEnd.y - pCenter.y).toDouble(), 2.0))).toInt().coerceIn(1, 63)
                                            client.sendCommand(AirCanvasProtocol.CMD_CIRCLE, pCenter.x.toInt(), pCenter.y.toInt(), size = radius)
                                        }
                                        else -> { /* Pen/Eraser handled by buffer */ }
                                    }
                                    paths.add(currentTool to points)
                                }
                                currentPathPoints.value = emptyList()
                                lastBufferedPoint = null
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Draw historical paths
                    paths.forEach { (tool, points) ->
                        if (points.size > 1) {
                            val strokeWidth = if (tool == Tool.ERASER) eraserSize.toFloat() else brushSize.toFloat()
                            val color = if (tool == Tool.ERASER) Color.Black else Color.Cyan
                            
                            drawLine(
                                color = color,
                                start = points[0],
                                end = points[1],
                                strokeWidth = strokeWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                            for (i in 1 until points.size) {
                                drawLine(
                                    color = color,
                                    start = points[i-1],
                                    end = points[i],
                                    strokeWidth = strokeWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            }
                        }
                    }
                    // Draw current path
                    if (currentPathPoints.value.size > 1) {
                        val strokeWidth = if (currentTool == Tool.ERASER) eraserSize.toFloat() else brushSize.toFloat()
                        val color = if (currentTool == Tool.ERASER) Color.Black else Color.Cyan
                        for (i in 1 until currentPathPoints.value.size) {
                            drawLine(
                                color = color,
                                start = currentPathPoints.value[i-1],
                                end = currentPathPoints.value[i],
                                strokeWidth = strokeWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        }
                    }
                }
            }

            Row(modifier = Modifier.padding(vertical = 16.dp)) {
                Button(onClick = { client.sendCommand(AirCanvasProtocol.CMD_DEMO_START) }) { Text("Demo Start") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { client.sendCommand(AirCanvasProtocol.CMD_DEMO_END) }) { Text("Demo End") }
            }

            Button(
                onClick = { 
                    if (isConnected) client.disconnect() else client.connect("ws://192.168.4.1:81") 
                },
                colors = ButtonDefaults.buttonColors(containerColor = if (isConnected) Color.Red else Color.Green)
            ) {
                Text(if (isConnected) "Disconnect" else "Connect")
            }

            Text("Status: $statusText", color = Color.Gray)
    }
}

@Composable
fun ToolButtonFixed(label: String, tool: Tool, currentTool: Tool, action: (Tool) -> Unit) {
    val isSelected = tool == currentTool
    Button(
        onClick = { action(tool) },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color(0xFFBB86FC) else Color.DarkGray
        ),
        modifier = Modifier.padding(4.dp)
    ) {
        Text(label, color = Color.White)
    }
}

@Composable
fun SystemButton(label: String, action: () -> Unit) {
    Button(
        onClick = action,
        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
        modifier = Modifier.padding(4.dp)
    ) {
        Text(label, color = Color.White)
    }
}
