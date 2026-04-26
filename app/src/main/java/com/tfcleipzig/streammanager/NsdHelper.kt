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

class NsdHelper(
    private val context: Context,
    private val onStatusUpdate: (String) -> Unit,
) {
    private val nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var discoveryJob: Job? = null
    private var monitoringJob: Job? = null
    private var isServiceFound = false
    private var lastServiceFoundTime = 0L

    private var _serverHost: String? = null
    private var _serverPort: Int = -1

    val serverHost: String?
        get() = _serverHost

    val serverPort: Int
        get() = _serverPort

    fun getConnectionDetails(): Pair<String?, Int?> = Pair(serverHost, serverPort)

    fun startDiscovery() {
        discoveryJob?.cancel()
        monitoringJob?.cancel()
        isServiceFound = false

        startMonitoring()
        startDiscoveryLoop()
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
        isServiceFound = false
        _serverHost = null
        _serverPort = -1
        onStatusUpdate("Service lost - searching...")
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
                    onStatusUpdate("Discovery failed to start")
                }

                override fun onStopDiscoveryFailed(
                    serviceType: String,
                    errorCode: Int,
                ) {
                    Log.e(TAG, "Discovery failed to stop with error code: $errorCode")
                    onStatusUpdate("Discovery failed to stop")
                }

                override fun onDiscoveryStarted(serviceType: String) {
                    if (!isServiceFound) {
                        onStatusUpdate(
                            "Discovery active: Searching for $TARGET_SERVICE_NAME...",
                        )
                    }
                }

                override fun onDiscoveryStopped(serviceType: String) {
                    if (!isServiceFound) {
                        onStatusUpdate("Discovery stopped - retrying...")
                    }
                }

                override fun onServiceFound(service: NsdServiceInfo) {
                    if (service.serviceName.contains(TARGET_SERVICE_NAME, ignoreCase = true)) {
                        lastServiceFoundTime = System.currentTimeMillis()

                        if (!isServiceFound) {
                            onStatusUpdate("Found target service: ${service.serviceName}")
                            resolveService(service)
                        }
                    }
                }

                override fun onServiceLost(service: NsdServiceInfo) {
                    if (service.serviceName.contains(TARGET_SERVICE_NAME, ignoreCase = true)) {
                        Log.d(TAG, "Target service lost: ${service.serviceName}")
                        handleServiceLost()
                    }
                }
            }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start discovery", e)
            onStatusUpdate("Failed to start discovery: ${e.message}")
        }
    }

    private fun resolveService(service: NsdServiceInfo) {
        nsdManager.registerServiceInfoCallback(
            service,
            { it.run() },
            object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                    Log.e(TAG, "Service info callback registration failed: $errorCode")
                    onStatusUpdate("Service resolution failed")
                    isServiceFound = false
                    _serverHost = null
                    _serverPort = -1
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
                    Log.d(TAG, "Resolve succeeded: ${service.serviceName}")
                    Log.d(TAG, "Host: $host, Port: ${service.port}")

                    _serverHost = host
                    _serverPort = service.port

                    isServiceFound = true
                    lastServiceFoundTime = System.currentTimeMillis()
                    onStatusUpdate("Connected to ${service.serviceName}")
                    nsdManager.unregisterServiceInfoCallback(this)
                }

                override fun onServiceLost() {
                    Log.d(TAG, "Service lost during resolution")
                    handleServiceLost()
                    nsdManager.unregisterServiceInfoCallback(this)
                }
            },
        )
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
        _serverHost = null
        _serverPort = -1
    }

    companion object {
        private const val TAG = "NsdHelper"
        private const val SERVICE_TYPE = "_http._tcp."
        const val TARGET_SERVICE_NAME = "TFCStreamServer"
        private const val DISCOVERY_INTERVAL = 2000L
        private const val CONNECTED_REFRESH_INTERVAL = 10000L
        private const val MONITORING_INTERVAL = 2000L
    }
}
