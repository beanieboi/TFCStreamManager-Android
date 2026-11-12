package com.tfcleipzig.streammanager

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.Inet4Address

/**
 * What the tablet knows about the stream server.
 *
 * The server registers mDNS as soon as the app is open and announces its state in the TXT
 * record `state`: `started` (app open, web server stopped) or `running`.
 */
enum class ServerStatus {
    NOT_FOUND,
    STARTED,
    RUNNING,
    ;

    companion object {
        /** Servers before the state record only advertised while running. */
        fun fromTxt(state: String?): ServerStatus =
            when (state?.lowercase()) {
                "started" -> STARTED
                "running", null -> RUNNING
                else -> STARTED
            }
    }
}

class NsdHelper(
    context: Context,
    private val onStatusChange: (ServerStatus) -> Unit,
) {
    private val nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val mainExecutor = context.mainExecutor
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var serviceInfoCallback: NsdManager.ServiceInfoCallback? = null
    private var discoveryJob: Job? = null
    private var monitoringJob: Job? = null
    private var lastServiceFoundTime = 0L

    private var serverHost: String? = null
    private var serverPort: Int = -1

    var status: ServerStatus = ServerStatus.NOT_FOUND
        private set

    private val isServiceFound: Boolean
        get() = status != ServerStatus.NOT_FOUND

    /** Host and port, only while the server is running. */
    fun getConnectionDetails(): Pair<String?, Int?> = if (status == ServerStatus.RUNNING) Pair(serverHost, serverPort) else Pair(null, null)

    fun startDiscovery() {
        discoveryJob?.cancel()
        monitoringJob?.cancel()
        updateStatus(ServerStatus.NOT_FOUND)

        startMonitoring()
        startDiscoveryLoop()
    }

    private fun updateStatus(newStatus: ServerStatus) {
        if (newStatus != status) {
            Log.d(TAG, "Server status: $status -> $newStatus")
        }
        status = newStatus
        onStatusChange(newStatus)
    }

    private fun startDiscoveryLoop() {
        discoveryJob?.cancel()
        discoveryJob =
            scope.launch {
                while (isActive) {
                    startSingleDiscovery()
                    delay(if (isServiceFound) CONNECTED_REFRESH_INTERVAL else DISCOVERY_INTERVAL)
                }
            }
    }

    private fun startMonitoring() {
        monitoringJob =
            scope.launch {
                while (isActive) {
                    if (isServiceFound &&
                        System.currentTimeMillis() - lastServiceFoundTime > CONNECTED_REFRESH_INTERVAL * 2
                    ) {
                        Log.d(TAG, "Service timeout detected")
                        handleServiceLost()
                    }
                    delay(MONITORING_INTERVAL)
                }
            }
    }

    private fun handleServiceLost() {
        unregisterServiceInfoCallback()
        serverHost = null
        serverPort = -1
        updateStatus(ServerStatus.NOT_FOUND)
        startDiscoveryLoop()
    }

    private fun startSingleDiscovery() {
        stopCurrentDiscovery()

        discoveryListener =
            object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(
                    serviceType: String,
                    errorCode: Int,
                ) {
                    Log.e(TAG, "Discovery failed to start with error code: $errorCode")
                }

                override fun onStopDiscoveryFailed(
                    serviceType: String,
                    errorCode: Int,
                ) {
                    Log.e(TAG, "Discovery failed to stop with error code: $errorCode")
                }

                override fun onDiscoveryStarted(serviceType: String) {
                    Log.d(TAG, "Discovery started")
                }

                override fun onDiscoveryStopped(serviceType: String) {
                    Log.d(TAG, "Discovery stopped")
                }

                override fun onServiceFound(service: NsdServiceInfo) {
                    if (service.serviceName.contains(TARGET_SERVICE_NAME, ignoreCase = true)) {
                        mainExecutor.execute {
                            lastServiceFoundTime = System.currentTimeMillis()
                            if (serviceInfoCallback == null) {
                                Log.d(TAG, "Found target service: ${service.serviceName}")
                                watchService(service)
                            }
                        }
                    }
                }

                override fun onServiceLost(service: NsdServiceInfo) {
                    if (service.serviceName.contains(TARGET_SERVICE_NAME, ignoreCase = true)) {
                        Log.d(TAG, "Target service lost: ${service.serviceName}")
                        mainExecutor.execute { handleServiceLost() }
                    }
                }
            }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start discovery", e)
        }
    }

    /**
     * Keeps a ServiceInfoCallback registered for as long as the service exists, so changes of
     * the `state` TXT record (started <-> running) arrive without rediscovery.
     */
    private fun watchService(service: NsdServiceInfo) {
        val callback =
            object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                    Log.e(TAG, "Service info callback registration failed: $errorCode")
                    serviceInfoCallback = null
                    handleServiceLost()
                }

                override fun onServiceInfoCallbackUnregistered() {}

                override fun onServiceUpdated(service: NsdServiceInfo) {
                    val host =
                        service.hostAddresses
                            .filterNot { it.isLinkLocalAddress }
                            .sortedBy { if (it is Inet4Address) 0 else 1 }
                            .firstOrNull()
                            ?.hostAddress
                            ?: service.hostAddresses.firstOrNull()?.hostAddress
                    val state = service.attributes[TXT_STATE_KEY]?.decodeToString()
                    Log.d(TAG, "Service updated: ${service.serviceName} $host:${service.port} state=$state")

                    serverHost = host
                    serverPort = service.port
                    lastServiceFoundTime = System.currentTimeMillis()
                    updateStatus(if (host == null) ServerStatus.NOT_FOUND else ServerStatus.fromTxt(state))
                }

                override fun onServiceLost() {
                    Log.d(TAG, "Service lost")
                    handleServiceLost()
                }
            }

        serviceInfoCallback = callback
        try {
            nsdManager.registerServiceInfoCallback(service, mainExecutor, callback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to watch service", e)
            serviceInfoCallback = null
        }
    }

    private fun unregisterServiceInfoCallback() {
        serviceInfoCallback?.let {
            try {
                nsdManager.unregisterServiceInfoCallback(it)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister service info callback", e)
            }
        }
        serviceInfoCallback = null
    }

    private fun stopCurrentDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop discovery", e)
            }
        }
        discoveryListener = null
    }

    fun stopDiscovery() {
        scope.cancel()
        stopCurrentDiscovery()
        unregisterServiceInfoCallback()
        serverHost = null
        serverPort = -1
    }

    companion object {
        private const val TAG = "NsdHelper"
        private const val SERVICE_TYPE = "_http._tcp."
        const val TARGET_SERVICE_NAME = "TFCStreamServer"
        const val TXT_STATE_KEY = "state"
        private const val DISCOVERY_INTERVAL = 2000L
        private const val CONNECTED_REFRESH_INTERVAL = 10000L
        private const val MONITORING_INTERVAL = 2000L
    }
}
