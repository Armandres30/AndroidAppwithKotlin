package com.example.myapplication // Or your actual package name

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.ui.input.key.type
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val PREFS_NAME = "my_prefs"
private const val URLS_KEY = "urls"
private const val NOTIFICATION_CHANNEL_ID = "MyVpnServiceChannel"
private const val NOTIFICATION_ID = 1 // Must be > 0

class MyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val blockedDomains = mutableSetOf<String>()
    private lateinit var executorService: ExecutorService
    private var isVpnRunning = false

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()
        Log.d("MyVpnService", "onCreate called")

        // 1. Create Notification Channel (for Android 8.0 Oreo and above)
        createNotificationChannel()

        // 2. Create the Notification
        val notificationIntent = Intent(this, MainActivity::class.java) // Intent to open when notification is tapped
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("URL Blocker Active")
            .setContentText("VPN service is running to block specified URLs.")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with your app's icon
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Makes the notification non-dismissable by swipe
            .build()

        // 3. Call startForeground()
        // The ID (NOTIFICATION_ID) must be a non-zero integer.
        // This ID must not be 0.
        try {
            startForeground(NOTIFICATION_ID, notification)
            Log.d("MyVpnService", "startForeground called successfully.")
        } catch (e: Exception) {
            Log.e("MyVpnService", "Error calling startForeground", e)
            // Handle cases where startForeground might fail, though rare if permissions are correct.
            // For example, if on Android 12+ and FOREGROUND_SERVICE_SPECIAL_USE permission is needed but not granted.
            // For VPN, FOREGROUND_SERVICE is usually sufficient.
        }


        executorService = Executors.newSingleThreadExecutor()
        val loadedUrls = loadUrlsFromPreferences(this)
        synchronized(blockedDomains) {
            blockedDomains.clear()
            blockedDomains.addAll(loadedUrls.map { cleanDomain(it) })
        }
        Log.d("MyVpnService", "Blocked domains loaded: $blockedDomains")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "My VPN Service Channel", // User-visible name of the channel
                NotificationManager.IMPORTANCE_DEFAULT // Or IMPORTANCE_LOW to minimize sound/vibration
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
            Log.d("MyVpnService", "Notification channel created.")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("MyVpnService", "onStartCommand received action: ${intent?.action}")
        if (intent?.action == "stop_vpn") {
            stopVpn()
            return START_NOT_STICKY // Don't restart if stopped explicitly
        }

        if (intent?.action == "update_blocked_urls") {
            val loadedUrls = loadUrlsFromPreferences(this)
            synchronized(blockedDomains) {
                blockedDomains.clear()
                blockedDomains.addAll(loadedUrls.map { cleanDomain(it) })
            }
            Log.d("MyVpnService", "Blocked domains updated: $blockedDomains")
        }

        if (!isVpnRunning) {
            startVpn()
        }
        // Use START_STICKY to ensure the service restarts if killed by the system
        // (unless explicitly stopped).
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) {
            Log.d("MyVpnService", "VPN is already running.")
            return
        }

        val builder = Builder()
        builder.setSession(getString(R.string.app_name)) // Use app name or a descriptive session name
            .addAddress("10.0.0.2", 24)
            .addDnsServer("8.8.8.8") // Good practice to add a DNS server
            .addRoute("0.0.0.0", 0)

        // Add application exclusion if you don't want your own app's traffic to go through the VPN
        // try {
        //     builder.addDisallowedApplication(packageName)
        //     Log.d("MyVpnService", "Excluded own application from VPN: $packageName")
        // } catch (e: PackageManager.NameNotFoundException) {
        //     Log.e("MyVpnService", "Failed to exclude own application", e)
        // }


        try {
            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Log.e("MyVpnService", "VPN establish returned null. VPN permission likely not granted or revoked.")
                stopVpn() // Attempt to clean up
                // Consider sending a broadcast or updating UI to inform user about permission issue
                return
            }
            Log.d("MyVpnService", "VPN interface established successfully.")
            isVpnRunning = true

            executorService.execute {
                runVpnLoop()
            }
        } catch (e: SecurityException) {
            Log.e("MyVpnService", "Security Exception: Failed to establish VPN interface. VPN permission likely missing or denied.", e)
            stopVpn()
        }
        catch (e: Exception) {
            Log.e("MyVpnService", "Failed to establish VPN interface", e)
            stopVpn()
        }
    }


    private fun stopVpn() {
        Log.d("MyVpnService", "Stopping VPN service.")
        isVpnRunning = false
        if (::executorService.isInitialized && !executorService.isShutdown) {
            executorService.shutdownNow() // Attempt to stop threads immediately
        }
        try {
            vpnInterface?.close()
            Log.d("MyVpnService", "VPN interface closed.")
        } catch (e: IOException) {
            Log.e("MyVpnService", "Error closing VPN interface", e)
        }
        vpnInterface = null

        // Stop being a foreground service. Pass true to remove the notification.
        stopForeground(true)
        Log.d("MyVpnService", "stopForeground(true) called.")

        stopSelf() // Stop the service itself
        Log.d("MyVpnService", "stopSelf() called.")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MyVpnService", "onDestroy called.")
        stopVpn() // Ensure cleanup
    }

    private fun loadUrlsFromPreferences(context: Context): List<String> {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gson = Gson()
        val json = prefs.getString(URLS_KEY, null)
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    private fun cleanDomain(url: String): String {
        return try {
            var domain = url.replaceFirst(Regex("^https?://"), "")
            domain = domain.substringBefore("/")
            domain = domain.substringBefore(":")
            if (domain.startsWith("www.")) {
                domain = domain.substringAfter("www.")
            }
            domain.lowercase()
        } catch (e: Exception) {
            url.lowercase()
        }
    }


    private fun runVpnLoop() {
        Log.d("MyVpnService", "VPN loop started.")
        val vpnInputChannel = vpnInterface?.fileDescriptor?.let { FileInputStream(it).channel }
        val vpnOutputChannel = vpnInterface?.fileDescriptor?.let { FileOutputStream(it).channel }

        if (vpnInputChannel == null || vpnOutputChannel == null) {
            Log.e("MyVpnService", "VPN input or output channel is null. Stopping VPN loop.")
            stopVpn()
            return
        }

        val packet = ByteBuffer.allocate(32767)

        try { // Outer try for the whole loop, to catch unexpected errors and stop gracefully
            while (isVpnRunning && !Thread.currentThread().isInterrupted) {
                try {
                    packet.clear()
                    val length = vpnInputChannel.read(packet)

                    if (length > 0) {
                        packet.flip()
                        val packetBytes = ByteArray(length)
                        packet.get(packetBytes, 0, length) // Read only 'length' bytes
                        packet.position(0) // Reset position for potential re-read or write

                        val decision = shouldBlockPacket(packetBytes, length)

                        if (decision.block) {
                            // Log.d("MyVpnService", "Blocking packet for domain/IP: ${decision.reason}")
                        } else {
                            // Log.d("MyVpnService", "Allowing packet for domain/IP: ${decision.reason}")
                            vpnOutputChannel.write(packet)
                        }
                    } else if (length == -1) {
                        Log.d("MyVpnService", "VPN input channel closed (end of stream).")
                        isVpnRunning = false // Signal to stop
                        break
                    }
                    // Add a small sleep if no data is read immediately to prevent tight loop burning CPU
                    // else if (length == 0) {
                    //    Thread.sleep(10) // Milliseconds
                    // }

                } catch (e: IOException) {
                    if (isVpnRunning) { // Only log if we expect to be running
                        Log.e("MyVpnService", "VPN loop I/O error", e)
                    }
                    isVpnRunning = false // Signal to stop
                    break
                } catch (e: Exception) { // Catch other potential errors within the packet processing
                    Log.e("MyVpnService", "Error processing packet in VPN loop", e)
                    // Depending on the error, you might want to continue or break
                }
            }
        } catch (e: Exception) { // Catch errors that might occur outside the inner try-catch (e.g., thread interruption issues)
            Log.e("MyVpnService", "Critical error in VPN loop", e)
        } finally {
            Log.d("MyVpnService", "VPN loop terminated. isVpnRunning: $isVpnRunning")
            // Ensure VPN is stopped if the loop terminates for any reason while it was supposed to be running
            if (isVpnRunning || vpnInterface != null) { // Check vpnInterface as well, as isVpnRunning might have just been set to false
                stopVpn()
            }
        }
    }


    private data class PacketDecision(val block: Boolean, val reason: String = "")

    private fun shouldBlockPacket(packetData: ByteArray, length: Int): PacketDecision {
        try {
            val ipHeader = Ip4Header(packetData, 0, length)
            if (!ipHeader.isValid) {
                // Log.v("MyVpnService", "Invalid or too short IP packet.")
                return PacketDecision(false, "Invalid IP Packet")
            }

            if (ipHeader.transportProtocolEnum == Ip4Header.TransportProtocol.UDP) {
                if (ipHeader.headerLength > length - UdpHeader.MIN_HEADER_LENGTH) {
                    // Log.v("MyVpnService", "Packet too short for UDP header after IP header.")
                    return PacketDecision(false, "Packet too short for UDP Header")
                }
                val udpHeader = UdpHeader(packetData, ipHeader.headerLength, length - ipHeader.headerLength)
                if (!udpHeader.isValid) {
                    // Log.v("MyVpnService", "Invalid or too short UDP packet.")
                    return PacketDecision(false, "Invalid UDP Packet")
                }

                if (udpHeader.destinationPort == 53 || udpHeader.sourcePort == 53) { // DNS Query or Response
                    val dnsPayloadOffset = ipHeader.headerLength + udpHeader.headerLength
                    if (dnsPayloadOffset > length - DnsPacket.MIN_DNS_HEADER_LENGTH) {
                        // Log.v("MyVpnService", "Packet too short for DNS header after UDP header.")
                        return PacketDecision(false, "Packet too short for DNS Header")
                    }
                    try {
                        val dnsPacket = DnsPacket(packetData, dnsPayloadOffset, length - dnsPayloadOffset)
                        val queriedDomain = dnsPacket.getQueriedDomain()

                        if (queriedDomain != null && queriedDomain.isNotBlank()) {
                            // Log.d("MyVpnService", "DNS Query for: $queriedDomain (Dest Port: ${udpHeader.destinationPort})")
                            val isBlocked: Boolean
                            synchronized(blockedDomains) {
                                isBlocked = blockedDomains.any { blockedUrl ->
                                    queriedDomain.endsWith(blockedUrl, ignoreCase = true) || queriedDomain.equals(blockedUrl, ignoreCase = true)
                                }
                            }
                            if (isBlocked) {
                                Log.i("MyVpnService", "BLOCKING DNS query to: $queriedDomain")
                                return PacketDecision(true, "DNS query to blocked domain: $queriedDomain")
                            } else {
                                return PacketDecision(false, "DNS query to allowed domain: $queriedDomain")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MyVpnService", "Error parsing DNS packet for ${ipHeader.destinationAddress}", e)
                    }
                }
            }
            // Optional: Log non-DNS packets if needed for debugging
            // else {
            //    Log.v("MyVpnService", "Processing non-DNS packet: ${ipHeader.protocolEnum} to ${ipHeader.destinationAddress}")
            // }

            // Default: Allow if no specific blocking rule matched based on DNS
            // You might want to add IP-based blocking here as a fallback
            return PacketDecision(false, "Allowed (not a matched DNS query), Dest IP: ${ipHeader.destinationAddress}")
        } catch (e: Exception) {
            Log.e("MyVpnService", "Error in shouldBlockPacket: ${e.message}", e)
            return PacketDecision(false, "Error processing packet") // Default to allow on error to avoid breaking connectivity
        }
    }

    // --- Inner Helper Classes for Packet Parsing ---
    // (Ensure these are robust and handle edge cases/malformed packets)

    class Ip4Header(private val buffer: ByteArray, private val offset: Int, private val packetLength: Int) {
        companion object {
            const val MIN_HEADER_LENGTH = 20 // Minimum IPv4 header length
        }

        val isValid: Boolean
        val version: Int
        val headerLength: Int // In bytes
        val totalLength: Int
        val protocol: Int
        val sourceAddress: String
        val destinationAddress: String
        val transportProtocolEnum: TransportProtocol

        init {
            if (packetLength < MIN_HEADER_LENGTH) {
                isValid = false
                version = -1
                headerLength = -1
                totalLength = -1
                protocol = -1
                sourceAddress = ""
                destinationAddress = ""
                transportProtocolEnum = TransportProtocol.UNKNOWN
            } else {
                version = (buffer[offset + 0].toInt() and 0xF0) shr 4
                headerLength = (buffer[offset + 0].toInt() and 0x0F) * 4
                totalLength = ((buffer[offset + 2].toInt() and 0xFF) shl 8) or (buffer[offset + 3].toInt() and 0xFF)
                protocol = buffer[offset + 9].toInt() and 0xFF
                sourceAddress = getAddress(buffer, offset + 12)
                destinationAddress = getAddress(buffer, offset + 16)
                isValid = version == 4 && headerLength >= MIN_HEADER_LENGTH && headerLength <= packetLength && totalLength <= packetLength

                transportProtocolEnum = when (protocol) {
                    6 -> TransportProtocol.TCP
                    17 -> TransportProtocol.UDP
                    1 -> TransportProtocol.ICMP
                    else -> TransportProtocol.UNKNOWN
                }
            }
        }


        enum class TransportProtocol { TCP, UDP, ICMP, UNKNOWN }

        private fun getAddress(buffer: ByteArray, start: Int): String {
            if (start + 4 > buffer.size) return "invalid_ip_offset"
            return "${buffer[start + 0].toInt() and 0xFF}.${buffer[start + 1].toInt() and 0xFF}.${buffer[start + 2].toInt() and 0xFF}.${buffer[start + 3].toInt() and 0xFF}"
        }
    }

    class UdpHeader(private val buffer: ByteArray, private val offset: Int, private val availableLength: Int) {
        companion object {
            const val MIN_HEADER_LENGTH = 8 // Fixed for UDP
        }
        val isValid: Boolean
        val sourcePort: Int
        val destinationPort: Int
        val length: Int // Length of UDP header and data
        val headerLength: Int = MIN_HEADER_LENGTH // Fixed for UDP

        init {
            if (availableLength < MIN_HEADER_LENGTH) {
                isValid = false
                sourcePort = -1
                destinationPort = -1
                length = -1
            } else {
                sourcePort = ((buffer[offset + 0].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)
                destinationPort = ((buffer[offset + 2].toInt() and 0xFF) shl 8) or (buffer[offset + 3].toInt() and 0xFF)
                length = ((buffer[offset + 4].toInt() and 0xFF) shl 8) or (buffer[offset + 5].toInt() and 0xFF)
                isValid = length >= MIN_HEADER_LENGTH && length <= availableLength
            }
        }
    }

    class DnsPacket(private val buffer: ByteArray, private val offset: Int, private val availableLength: Int) {
        companion object {
            const val MIN_DNS_HEADER_LENGTH = 12 // Minimum DNS header length
        }
        // Basic DNS Header fields (can be expanded)
        // val id: Int
        // val qr: Int // Query/Response flag
        // val qdCount: Int // Number of questions

        init {
            if (availableLength < MIN_DNS_HEADER_LENGTH) {
                // Not enough data for even a basic DNS header
                throw IllegalArgumentException("DNS packet too short for header. Available: $availableLength")
            }
            // id = ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)
            // qr = (buffer[offset + 2].toInt() and 0x80) shr 7
            // qdCount = ((buffer[offset + 4].toInt() and 0xFF) shl 8) or (buffer[offset + 5].toInt() and 0xFF)
        }


        fun getQueriedDomain(): String? {
            var current = offset + MIN_DNS_HEADER_LENGTH // Skip DNS header to start of Question section
            val labels = mutableListOf<String>()

            // Check if we even have space for the first length byte of the QNAME
            if (current >= offset + availableLength) {
                Log.w("DnsPacket", "Attempting to read QNAME length byte outside available packet data (start). Offset: $current, Available: ${offset + availableLength}")
                return null
            }

            try {
                while (current < (offset + availableLength) && buffer[current].toInt() != 0) {
                    val lengthByte = buffer[current].toInt() and 0xFF

                    // Check for DNS name compression pointer
                    if ((lengthByte and 0xC0) == 0xC0) {
                        if (current + 1 >= offset + availableLength) {
                            Log.w("DnsPacket", "DNS compression pointer found, but not enough data for offset. Current: $current")
                            return labels.joinToString(".").ifEmpty { null } // Return what we have so far
                        }
                        val pointerOffset = (((lengthByte and 0x3F) shl 8) or (buffer[current + 1].toInt() and 0xFF))
                        // Recursively parse the name from the pointer offset, but be careful with depth.
                        // For simplicity here, we'll just append what we have if a pointer is found.
                        // A full implementation needs to handle pointers by jumping to that offset in the original packet.
                        // This basic version will NOT correctly parse compressed names fully.
                        // Log.d("DnsPacket", "DNS compression pointer found to offset $pointerOffset. Partial domain: ${labels.joinToString(".")}")
                        // For this simplified version, we stop parsing here.
                        // A more complete parser would call a function like: labels.addAll(parseNameFromOffset(buffer, originalPacketOffset + pointerOffset))
                        break // Stop processing this part of the name
                    }


                    if (lengthByte == 0) break // End of QNAME (should be caught by while condition too)
                    if (lengthByte > 63) { // Label length max 63
                        Log.w("DnsPacket", "Invalid DNS label length: $lengthByte")
                        return null // Malformed
                    }

                    current++ // Move past the length byte

                    if (current + lengthByte > offset + availableLength) {
                        Log.w("DnsPacket", "DNS label ($lengthByte bytes) exceeds available packet data. Current: $current, Needed: ${current + lengthByte}, Available end: ${offset + availableLength}")
                        return labels.joinToString(".").ifEmpty { null } // Return what has been parsed so far
                    }

                    labels.add(String(buffer, current, lengthByte, Charsets.UTF_8))
                    current += lengthByte
                }
                return if (labels.isNotEmpty()) labels.joinToString(".") else null
            } catch (e: Exception) {
                Log.e("DnsPacket", "Error parsing QNAME: ${e.message}", e)
                return labels.joinToString(".").ifEmpty { null } // Return what we have
            }
        }
    }
}
