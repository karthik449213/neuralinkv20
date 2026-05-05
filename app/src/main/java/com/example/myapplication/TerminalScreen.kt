package com.example.myapplication

import android.app.Activity
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

//new imports
import android.util.Log
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

// ── Terminal color palette ──────────────────────────────────────────────
val BgDark       = Color(0xFF020A04)
val BgPanel      = Color(0xFF051208)
val NeonGreen    = Color(0xFF00FF88)
val NeonMid      = Color(0xFF00CC66)
val NeonDim      = Color(0xFF007733)
val NeonBorder   = Color(0xFF00441A)
val InfoBlue     = Color(0xFF55CCFF)
val ErrorRed     = Color(0xFFFF4455)
val OutputGreen  = Color(0xFF44DD88)

// ── Data model ──────────────────────────────────────────────────────────
enum class LineType { PROMPT, OUTPUT, INFO, ERROR, MUTED, SEPARATOR }

data class TerminalLine(val text: String, val type: LineType)

// ── Composable ──────────────────────────────────────────────────────────
@Composable
fun TerminalScreen(activity: Activity) {
    val commandHandler = rememberCommandHandler(activity = activity)
    val lines          = commandHandler.lines
    var input          by remember { mutableStateOf("") }
    val listState      = rememberLazyListState()
    val scope          = rememberCoroutineScope()
    var message by remember { mutableStateOf("Loading....") }
    LaunchedEffect(Unit) {
        RetrofitClient.api.getHello().enqueue(object : Callback<HelloResponse> {
            override fun onResponse(call: Call<HelloResponse>, response: Response<HelloResponse>) {
                if (response.isSuccessful) {
                    Log.d("API_TEST", "Response: ${response.body()?.message}")
                    // Update your UI state here (e.g., message = response.body()?.message ?: "")
                }
            }

            override fun onFailure(call: Call<HelloResponse>, t: Throwable) {
                Log.e("API_TEST", "Error: ${t.message}")
            }

        })
    }



    fun submit() {
        val cmd = input.trim()
        if (cmd.isBlank()) return
        input = ""
        commandHandler.execute(cmd)
        scope.launch { listState.animateScrollToItem(lines.size) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = BgDark
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Title bar ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgPanel)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrafficDots()
                Spacer(Modifier.width(10.dp))
                Text(
                    "KOTLIN-TERMINAL-X",
                    fontFamily = FontFamily.Monospace,
                    fontSize   = 10.sp,
                    letterSpacing = 2.sp,
                    color      = NeonDim,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "OS: KOTLIN-TERMINAL-X  |  KERNEL: API-34-STABLE",
                    fontFamily = FontFamily.Monospace,
                    fontSize   = 9.sp,
                    color      = NeonDim
                )
            }

            HorizontalDivider(color = NeonBorder, thickness = 1.dp)

            // ── Scrollable output ──────────────────────────────────────
            LazyColumn(
                state    = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(lines) { line -> TerminalLineItem(line) }
            }

            HorizontalDivider(color = NeonBorder, thickness = 1.dp)

            // ── Input row ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgPanel)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "root@pixel7:~$ ",
                    fontFamily = FontFamily.Monospace,
                    fontSize   = 12.sp,
                    color      = NeonDim
                )
                TextField(
                    value         = input,
                    onValueChange = { input = it },
                    modifier      = Modifier.weight(1f),
                    textStyle     = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 12.sp,
                        color      = NeonGreen
                    ),
                    singleLine    = true,
                    colors        = TextFieldDefaults.colors(
                        focusedContainerColor   = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor   = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor             = NeonGreen
                    ),
                    placeholder   = {
                        Text(
                            "enter command...",
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 12.sp,
                            color      = NeonBorder
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() })
                )
                TextButton(onClick = { submit() }) {
                    Text("▶", color = NeonGreen, fontSize = 16.sp)
                }
            }
        }
    }
}



@Composable
private fun TrafficDots() {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(Color(0xFFFF5F57), Color(0xFFFFBD2E), Color(0xFF28CA41)).forEach { c ->
            Surface(shape = RoundedCornerShape(50), color = c, modifier = Modifier.size(10.dp)) {}
        }
    }
}

@Composable
private fun TerminalLineItem(line: TerminalLine) {
    val (color, size) = when (line.type) {
        LineType.PROMPT    -> NeonDim     to 12.sp
        LineType.OUTPUT    -> OutputGreen to 12.sp
        LineType.INFO      -> InfoBlue    to 12.sp
        LineType.ERROR     -> ErrorRed    to 12.sp
        LineType.MUTED     -> NeonDim     to 11.sp
        LineType.SEPARATOR -> NeonBorder  to 11.sp
    }
    Text(
        text       = line.text,
        color      = color,
        fontSize   = size,
        fontFamily = FontFamily.Monospace,
        lineHeight = 20.sp,
        modifier   = Modifier.fillMaxWidth()
    )
}
