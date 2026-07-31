package com.arcticalphawolf.arcticrf.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val deviceName: String) : ConnectionState()
    object PermissionDenied : ConnectionState()
}

private const val ACTION_USB_PERMISSION = "com.arcticalphawolf.arcticrf.USB_PERMISSION"
private const val BAUD_RATE = 115200

/**
 * Owns the single USB-serial link to the ESP32 board: device discovery via
 * usb-serial-for-android's built-in CP2102/CH340 drivers, the permission
 * dialog round-trip, line-buffered reads off a background IO thread, and
 * automatic reconnect when the board is unplugged/replugged - all without
 * the user ever touching a "connect" button.
 */
class UsbSerialManager(private val context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var driver: UsbSerialDriver? = null
    private var port: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null
    private val readBuffer = StringBuilder()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _lines = MutableSharedFlow<String>(extraBufferCapacity = 512)
    val lines: SharedFlow<String> = _lines.asSharedFlow()

    // Connection-state diagnostics (IO errors, reconnect attempts) - purely
    // informational, feeds the Console tab so flaky-USB symptoms are visible
    // instead of just silently dropping back to "disconnected".
    private val _diagnostics = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val diagnostics: SharedFlow<String> = _diagnostics.asSharedFlow()

    // Debug-tab visibility: raw traffic counters/tail and current control-line
    // state, so a silent link (board not replying) can actually be diagnosed
    // from the phone instead of guessed at.
    private val _bytesSent = MutableStateFlow(0L)
    val bytesSent: StateFlow<Long> = _bytesSent.asStateFlow()
    private val _bytesReceived = MutableStateFlow(0L)
    val bytesReceived: StateFlow<Long> = _bytesReceived.asStateFlow()
    private val _rawHexTail = MutableStateFlow("")
    val rawHexTail: StateFlow<String> = _rawHexTail.asStateFlow()
    private val _controlLines = MutableStateFlow("")
    val controlLines: StateFlow<String> = _controlLines.asStateFlow()
    private val _deviceInfo = MutableStateFlow("")
    val deviceInfo: StateFlow<String> = _deviceInfo.asStateFlow()

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 3

    // Guards against overlapping connectToDevice() calls: MainActivity.onResume()
    // calls tryAutoConnect() unconditionally on every resume, which can race
    // with an in-flight reconnect-after-error coroutine and try to open the
    // same port twice ("Already open"), corrupting the retry-count bookkeeping.
    @Volatile private var isConnecting = false

    private var receiversRegistered = false

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    val device: UsbDevice? = intent.getParcelableExtraCompat(UsbManager.EXTRA_DEVICE)
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted && device != null) {
                        connectToDevice(device)
                    } else {
                        _connectionState.value = ConnectionState.PermissionDenied
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> tryAutoConnect()
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device: UsbDevice? = intent.getParcelableExtraCompat(UsbManager.EXTRA_DEVICE)
                    if (device == null || device == driver?.device) {
                        disconnect()
                    }
                }
            }
        }
    }

    fun registerReceivers() {
        if (receiversRegistered) return
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(usbReceiver, filter)
        }
        receiversRegistered = true
    }

    fun unregisterReceivers() {
        if (!receiversRegistered) return
        context.unregisterReceiver(usbReceiver)
        receiversRegistered = false
    }

    /** Called on app launch and whenever we should re-scan for an already-plugged-in board. */
    fun tryAutoConnect() {
        if (port != null && _connectionState.value is ConnectionState.Connected) {
            return // already connected - MainActivity.onResume() calls this unconditionally
        }
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        val found = availableDrivers.firstOrNull() ?: run {
            _connectionState.value = ConnectionState.Disconnected
            return
        }
        driver = found
        if (usbManager.hasPermission(found.device)) {
            connectToDevice(found.device)
        } else {
            requestPermission(found.device)
        }
    }

    /** Handles the intent Android delivers when it auto-launches us via device_filter.xml. */
    fun handleAttachIntent(intent: Intent) {
        if (intent.action != UsbManager.ACTION_USB_DEVICE_ATTACHED) return
        val device: UsbDevice? = intent.getParcelableExtraCompat(UsbManager.EXTRA_DEVICE)
        if (device != null) {
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            driver = availableDrivers.firstOrNull { it.device == device } ?: return
            if (usbManager.hasPermission(device)) connectToDevice(device) else requestPermission(device)
        } else {
            tryAutoConnect()
        }
    }

    private fun requestPermission(device: UsbDevice) {
        _connectionState.value = ConnectionState.Connecting
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else 0
        val permissionIntent = PendingIntent.getBroadcast(
            context, 0, Intent(ACTION_USB_PERMISSION).setPackage(context.packageName), flags
        )
        usbManager.requestPermission(device, permissionIntent)
    }

    private fun connectToDevice(device: UsbDevice) {
        if (isConnecting) {
            _diagnostics.tryEmit("Connect already in progress, skipping duplicate request")
            return
        }
        // Always re-probe a fresh driver/port rather than reusing the cached
        // `driver` field: usb-serial-for-android's port objects track open/
        // closed state internally, and reusing one whose close() hasn't fully
        // settled yet throws "Already open" on the next open() attempt.
        val currentDriver = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            .firstOrNull { it.device == device } ?: return
        driver = currentDriver
        val connection = usbManager.openDevice(device)
        if (connection == null) {
            _connectionState.value = ConnectionState.Disconnected
            return
        }
        isConnecting = true
        try {
            val p = currentDriver.ports[0]
            p.open(connection)
            p.setParameters(BAUD_RATE, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

            // Some ESP32 boards' auto-reset circuit wires DTR/RTS through to
            // EN/GPIO0. If either line is left asserted after open() (driver-
            // or OS-dependent default), the chip can be held in reset for as
            // long as the port stays open - CP2102 itself still enumerates
            // fine (so the app shows "Connected"), but the ESP32 never boots
            // and never says a word. Explicitly release both right away.
            try {
                p.dtr = false
                p.rts = false
                _diagnostics.tryEmit("DTR/RTS cleared on connect")
            } catch (e: Exception) {
                _diagnostics.tryEmit("DTR/RTS not supported on this port: ${e.message ?: e.javaClass.simpleName}")
            }
            refreshControlLines(p)

            port = p
            readBuffer.setLength(0)
            _bytesSent.value = 0
            _bytesReceived.value = 0
            _rawHexTail.value = ""
            _deviceInfo.value = "VID=%04X PID=%04X %s".format(
                device.vendorId, device.productId, device.deviceName
            )
            ioManager = SerialInputOutputManager(p, object : SerialInputOutputManager.Listener {
                override fun onNewData(data: ByteArray) = onBytesReceived(data)
                override fun onRunError(e: Exception) = handleIoError(device, e)
            })
            Executors.newSingleThreadExecutor().submit(ioManager)
            reconnectAttempts = 0
            _connectionState.value = ConnectionState.Connected(device.deviceName)
        } catch (e: Exception) {
            _diagnostics.tryEmit("Connect failed: ${e.message ?: e.javaClass.simpleName}")
            _connectionState.value = ConnectionState.Disconnected
        } finally {
            isConnecting = false
        }
    }

    private fun refreshControlLines(p: UsbSerialPort) {
        try {
            _controlLines.value = "DTR=${p.dtr} RTS=${p.rts}"
        } catch (_: Exception) {
            _controlLines.value = "unsupported"
        }
    }

    /** Manual DTR/RTS control for the Debug tab - lets a stuck connection be poked live. */
    fun setDtr(value: Boolean) {
        val p = port ?: return
        try {
            p.dtr = value
            _diagnostics.tryEmit("DTR set to $value")
        } catch (e: Exception) {
            _diagnostics.tryEmit("setDTR failed: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            refreshControlLines(p)
        }
    }

    fun setRts(value: Boolean) {
        val p = port ?: return
        try {
            p.rts = value
            _diagnostics.tryEmit("RTS set to $value")
        } catch (e: Exception) {
            _diagnostics.tryEmit("setRTS failed: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            refreshControlLines(p)
        }
    }

    /**
     * A read/write error on the IO thread doesn't necessarily mean the board
     * was unplugged - flaky OTG cables/adapters and USB host power hiccups
     * are common with ESP32 boards, especially right after a power-hungry
     * radio operation. Instead of dropping straight to "disconnected" (which
     * would force a manual replug), retry a few times first; a real unplug
     * is still caught separately via ACTION_USB_DEVICE_DETACHED.
     */
    private fun handleIoError(device: UsbDevice, e: Exception) {
        _diagnostics.tryEmit("USB IO error: ${e.message ?: e.javaClass.simpleName}")
        closePortQuietly()
        ioScope.launch {
            if (reconnectAttempts >= maxReconnectAttempts) {
                _diagnostics.tryEmit("Giving up after $maxReconnectAttempts reconnect attempts")
                _connectionState.value = ConnectionState.Disconnected
                return@launch
            }
            reconnectAttempts++
            _connectionState.value = ConnectionState.Connecting
            delay(800L * reconnectAttempts)
            if (usbManager.deviceList.values.none { it.deviceName == device.deviceName }) {
                _diagnostics.tryEmit("Device no longer present")
                _connectionState.value = ConnectionState.Disconnected
                return@launch
            }
            _diagnostics.tryEmit("Reconnect attempt $reconnectAttempts/$maxReconnectAttempts...")
            connectToDevice(device)
        }
    }

    private fun closePortQuietly() {
        try {
            ioManager?.stop()
            port?.close()
        } catch (_: Exception) {
        } finally {
            ioManager = null
            port = null
        }
    }

    private fun onBytesReceived(data: ByteArray) {
        _bytesReceived.value += data.size
        appendHexTail(data)
        synchronized(readBuffer) {
            readBuffer.append(String(data, Charsets.US_ASCII))
            var idx: Int
            while (readBuffer.indexOf("\n").also { idx = it } >= 0) {
                val line = readBuffer.substring(0, idx).trimEnd('\r')
                readBuffer.delete(0, idx + 1)
                if (line.isNotEmpty()) _lines.tryEmit(line)
            }
        }
    }

    /** Sends one newline-terminated command line to the board. Safe to call from any thread. */
    fun send(command: String) {
        val p = port ?: return
        try {
            val bytes = (command + "\n").toByteArray(Charsets.US_ASCII)
            p.write(bytes, 500)
            _bytesSent.value += bytes.size
        } catch (e: Exception) {
            _diagnostics.tryEmit("write() failed: ${e.message ?: e.javaClass.simpleName}")
            disconnect()
        }
    }

    private val hexTailBytes = ArrayDeque<Byte>()
    private fun appendHexTail(data: ByteArray) {
        synchronized(hexTailBytes) {
            data.forEach { hexTailBytes.addLast(it) }
            while (hexTailBytes.size > 128) hexTailBytes.removeFirst()
            _rawHexTail.value = hexTailBytes.joinToString(" ") { "%02X".format(it) }
        }
    }

    fun disconnect() {
        closePortQuietly()
        reconnectAttempts = 0
        _connectionState.value = ConnectionState.Disconnected
    }
}

private inline fun <reified T : android.os.Parcelable> Intent.getParcelableExtraCompat(name: String): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(name, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(name)
    }
}
