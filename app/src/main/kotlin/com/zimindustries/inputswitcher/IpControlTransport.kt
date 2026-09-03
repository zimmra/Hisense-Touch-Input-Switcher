package com.zimindustries.inputswitcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Fallback B from the spec: the display runs an IP-control service (`com.dss.ipcontrol`) that
 * speaks the RS-232 protocol over TCP. We send the serial "select input" frame to
 * 127.0.0.1:<port> and expect an `AB AB … CD CD` acknowledgement, retrying once after 500 ms.
 *
 * The port is not fixed in firmware documentation; discover it with
 * `adb shell "netstat -tlnp || ss -tlnp"` and enter it in the setup screen.
 */
object IpControlTransport : SourceTransport {

    private const val HOST = "127.0.0.1"
    private const val CONNECT_TIMEOUT_MS = 1000
    private const val READ_TIMEOUT_MS = 1000
    private const val RETRY_DELAY_MS = 500L
    private const val ACK_BYTE = 0xAB.toByte()

    private val frames: Map<Source, ByteArray> = mapOf(
        Source.CONFERENCE_HUB to "DD FF 00 07 C1 08 00 00 01 24 EB BB CC",  // HDMI 1
        Source.FRONT_HDMI to "DD FF 00 07 C1 08 00 00 01 1A D5 BB CC",      // HDMI 3
        Source.FRONT_USBC to "DD FF 00 07 C1 08 00 00 01 1D D2 BB CC",      // Type-C 2
    ).mapValues { (_, hex) -> hex.split(' ').map { it.toInt(16).toByte() }.toByteArray() }

    override val displayName: String get() = "IP control"

    override fun switch(context: Context, source: Source, onResult: (SwitchResult) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        val port = Settings.ipPort(context)
        if (port !in 1..65535) {
            onResult(SwitchResult(false, displayName, source, "IP control port not set"))
            return
        }
        val frame = frames[source]
            ?: run {
                onResult(SwitchResult(false, displayName, source, "no frame for ${source.name}"))
                return
            }

        Thread({
            val result = try {
                var acked = sendOnce(port, frame)
                if (!acked) {
                    Thread.sleep(RETRY_DELAY_MS)
                    acked = sendOnce(port, frame)
                }
                if (acked) {
                    SwitchResult(true, displayName, source, "ack from :$port")
                } else {
                    SwitchResult(false, displayName, source, "no ack from :$port")
                }
            } catch (e: IOException) {
                Log.e(SourceSwitcher.TAG, "IP control failed", e)
                SwitchResult(false, displayName, source, "socket error: ${e.message}")
            } catch (e: InterruptedException) {
                SwitchResult(false, displayName, source, "interrupted")
            }
            main.post { onResult(result) }
        }, "ip-control").start()
    }

    private fun sendOnce(port: Int, frame: ByteArray): Boolean {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(HOST, port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = READ_TIMEOUT_MS
            socket.getOutputStream().apply {
                write(frame)
                flush()
            }
            val buf = ByteArray(64)
            val n = try {
                socket.getInputStream().read(buf)
            } catch (e: java.net.SocketTimeoutException) {
                Log.w(SourceSwitcher.TAG, "IP control: no reply within ${READ_TIMEOUT_MS}ms")
                return false
            }
            if (n <= 0) return false
            Log.i(SourceSwitcher.TAG, "IP control reply: " + buf.copyOf(n).joinToString(" ") { "%02X".format(it) })
            return n >= 2 && buf[0] == ACK_BYTE && buf[1] == ACK_BYTE
        }
    }
}
