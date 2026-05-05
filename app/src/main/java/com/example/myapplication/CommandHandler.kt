package com.example.myapplication

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult


// ── CommandHandler ──────────────────────────────────────────────────────
//
//  Wires the three commands from the JSON registry to real Android APIs:
//
//  sys --cam   [-v | --video | -s | --still]
//      Permission : android.permission.CAMERA
//      Intent     : android.media.action.IMAGE_CAPTURE / VIDEO_CAPTURE
//      Handler    : launchCameraBridge()
//
//  fs --mount  [root | sdcard]
//      Permission : android.permission.READ_EXTERNAL_STORAGE
//      Handler    : mountFileSystem()
//      Contract   : ActivityResultContracts.OpenDocumentTree()
//
//  logs --clear
//      Handler    : terminalState.clear()
//
// ───────────────────────────────────────────────────────────────────────

class CommandHandler(private val activity: Activity) {

    val lines = mutableStateListOf<TerminalLine>()

    // Launchers — must be registered before the Activity is started.
    // Call registerLaunchers() from onCreate() / rememberCommandHandler().
    var cameraStillLauncher: ActivityResultLauncher<Intent>? = null
    var cameraVideoLauncher: ActivityResultLauncher<Intent>? = null
    var documentTreeLauncher: ActivityResultLauncher<Uri?>?  = null
    var cameraPermLauncher:   ActivityResultLauncher<String>? = null

    private var pendingCameraMode: CameraMode = CameraMode.STILL

    init { bootMessages() }

    // ── Public entry point ─────────────────────────────────────────────
    fun execute(raw: String) {
        addLine(LineType.PROMPT, "root@pixel7:~\$ $raw")
        val parts = raw.trim().lowercase().split("\\s+".toRegex())
        when {
            parts[0] == "help"                                  -> cmdHelp()
            parts[0] == "clear"                                 -> cmdClear()
            parts[0] == "logs" && parts.getOrNull(1) == "--clear" -> cmdLogsClear()
            parts[0] == "sys"  && parts.getOrNull(1) == "--cam"   -> cmdSysCam(parts.drop(2))
            parts[0] == "fs"   && parts.getOrNull(1) == "--mount" -> cmdFsMount(parts.drop(2))
            else -> {
                addLine(LineType.ERROR, " ERR: command not found: $raw")
                addLine(LineType.MUTED, " Type 'help' for available commands.")
            }
        }
        addBlank()
    }

    // ── sys --cam ──────────────────────────────────────────────────────
    private fun cmdSysCam(flags: List<String>) {
        val mode = when {
            flags.contains("-v") || flags.contains("--video") -> CameraMode.VIDEO
            flags.contains("-s") || flags.contains("--still") -> CameraMode.STILL
            else                                               -> CameraMode.STILL
        }
        addLine(LineType.INFO,   " [android.permission.CAMERA] — requesting…")
        addLine(LineType.OUTPUT, " Intent: ${mode.action}")
        addLine(LineType.OUTPUT, " Handler: launchCameraBridge(mode=${mode.label})")
        addLine(LineType.MUTED,  " Accessing optical sensors…")
        launchCameraBridge(mode)
    }

    // ── fs --mount ─────────────────────────────────────────────────────
    private fun cmdFsMount(args: List<String>) {
        val target = args.firstOrNull()
        if (target == null) {
            addLine(LineType.ERROR, " ERR: specify target — [root | sdcard]")
            return
        }
        addLine(LineType.INFO,   " [android.permission.READ_EXTERNAL_STORAGE]")
        addLine(LineType.OUTPUT, " Intent: android.intent.action.OPEN_DOCUMENT_TREE")
        addLine(LineType.OUTPUT, " Handler: mountFileSystem(target=${target.uppercase()})")
        addLine(LineType.OUTPUT, " ActivityResultContracts.OpenDocument()")
        addLine(LineType.MUTED,  " Mapping directory structure…")
        mountFileSystem(target)
    }

    // ── logs --clear ───────────────────────────────────────────────────
    private fun cmdLogsClear() {
        addLine(LineType.MUTED, " Initiating scanline wipe…")
        terminalClear(keepBoot = false)
        addLine(LineType.OUTPUT, " ✔ System cache purged.")
    }

    // ── help ───────────────────────────────────────────────────────────
    private fun cmdHelp() {
        addLine(LineType.INFO,   " Available commands:")
        addLine(LineType.OUTPUT, "  sys --cam [-v|--video] [-s|--still]")
        addLine(LineType.MUTED,  "    Access optical sensors / camera bridge")
        addLine(LineType.OUTPUT, "  fs --mount [root|sdcard]")
        addLine(LineType.MUTED,  "    Mount file system, map directory structure")
        addLine(LineType.OUTPUT, "  logs --clear")
        addLine(LineType.MUTED,  "    Purge system cache / terminal state")
        addLine(LineType.OUTPUT, "  clear")
        addLine(LineType.MUTED,  "    Clear terminal output")
        addLine(LineType.OUTPUT, "  help")
        addLine(LineType.MUTED,  "    Show this message")
    }

    // ── clear ──────────────────────────────────────────────────────────
    private fun cmdClear() = terminalClear(keepBoot = false)

    // ── Android: Camera ────────────────────────────────────────────────
    private fun launchCameraBridge(mode: CameraMode) {
        pendingCameraMode = mode

        val hasPerm = ContextCompat.checkSelfPermission(
            activity, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPerm) {
            cameraPermLauncher?.launch(Manifest.permission.CAMERA)
                ?: addLine(LineType.ERROR, " ERR_HARDWARE_UNAVAILABLE — launcher not registered")
            return
        }

        fireCamera(mode)
    }

    fun fireCamera(mode: CameraMode) {
        val intent = Intent(mode.action).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        when (mode) {
            CameraMode.STILL -> cameraStillLauncher?.launch(intent)
                ?: addLine(LineType.ERROR, " ERR_HARDWARE_UNAVAILABLE")
            CameraMode.VIDEO -> cameraVideoLauncher?.launch(intent)
                ?: addLine(LineType.ERROR, " ERR_HARDWARE_UNAVAILABLE")
        }
        addLine(LineType.OUTPUT, " ✔ Camera bridge ready (${mode.label}).")
    }

    fun onCameraPermResult(granted: Boolean) {
        if (granted) {
            addLine(LineType.OUTPUT, " ✔ Permission granted.")
            fireCamera(pendingCameraMode)
        } else {
            addLine(LineType.ERROR, " ERR: Camera permission denied.")
        }
        addBlank()
    }

    // ── Android: File system ───────────────────────────────────────────
    private fun mountFileSystem(target: String) {
        val startUri: Uri = when (target) {
            "sdcard" -> Uri.parse("content://com.android.externalstorage.documents/tree/primary%3A")
            else     -> Uri.parse("content://com.android.externalstorage.documents/tree/root%3A")
        }
        documentTreeLauncher?.launch(startUri)
            ?: addLine(LineType.ERROR, " ERR: documentTreeLauncher not registered")
    }

    fun onDocumentTreeResult(uri: Uri?) {
        if (uri != null) {
            addLine(LineType.OUTPUT, " ✔ Drive mounted: $uri")
        } else {
            addLine(LineType.MUTED, " Mount cancelled by user.")
        }
        addBlank()
    }

    // ── Helpers ────────────────────────────────────────────────────────
    private fun bootMessages() {
        addLine(LineType.SEPARATOR, "────────────────────────────────────────────")
        addLine(LineType.INFO,      " KOTLIN-TERMINAL-X [v2.4.0] — API 34 STABLE")
        addLine(LineType.MUTED,     " Pixel 7 Emulator  |  com.example.myapplication")
        addLine(LineType.SEPARATOR, "────────────────────────────────────────────")
        addLine(LineType.OUTPUT,    " System boot... OK")
        addLine(LineType.OUTPUT,    " Command registry loaded. 3 commands available.")
        addBlank()
        addLine(LineType.MUTED,     " Type 'help' to list commands.")
        addBlank()
    }

    private fun terminalClear(keepBoot: Boolean) {
        lines.clear()
        if (keepBoot) bootMessages()
    }

    private fun addLine(type: LineType, text: String) = lines.add(TerminalLine(text, type))
    private fun addBlank() = lines.add(TerminalLine("", LineType.MUTED))
}

// ── Camera modes ────────────────────────────────────────────────────────
enum class CameraMode(val label: String, val action: String) {
    STILL("STILL", MediaStore.ACTION_IMAGE_CAPTURE),
    VIDEO("VIDEO", MediaStore.ACTION_VIDEO_CAPTURE)
}

// ── Composable factory ──────────────────────────────────────────────────
@Composable
fun rememberCommandHandler(activity: Activity): CommandHandler {
    val handler = remember { CommandHandler(activity) }


    handler.cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        handler.onCameraPermResult(granted)
    }

    handler.cameraStillLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // result ignored for demo; real apps would handle it.media URI here
        }

    handler.cameraVideoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Handle your video result here
    }

    // 4. Document Tree (Folder) Picker Launcher
    handler.documentTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        handler.onDocumentTreeResult(uri)
    }

    return handler
}
