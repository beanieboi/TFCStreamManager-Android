package com.tfcleipzig.streammanager

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.*

class NsdHelper(private val context: Context, private val onStatusUpdate: (String) -> Unit) {
    private val TAG = "NsdHelper"
    private val SERVICE_TYPE = "_http._tcp."
    private val TARGET_SERVICE_NAME = "TFCStream"
    private val DISCOVERY_INTERVAL = 2000L // 2 seconds in milliseconds
    private val MONITORING_INTERVAL = 2000L // 2 seconds in milliseconds

    private var nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var discoveryJob: Job? = null
    private var monitoringJob: Job? = null
    private var isServiceFound = false
    private var lastServiceFoundTime = 0L

    // Add properties to store connection details
    private var _serverHost: String? = null
    private var _serverPort: Int = -1

    // Expose connection details as read-only properties
    val serverHost: String?
        get() = _serverHost

    val serverPort: Int
        get() = _serverPort

    fun getConnectionDetails(): Pair<String?, Int?> = Pair(serverHost, serverPort)

    fun startDiscovery() {
        isServiceFound = false
        discoveryJob?.cancel()
        monitoringJob?.cancel()

        startMonitoring()
        startDiscoveryLoop()
    }

    private fun startDiscoveryLoop() {
        discoveryJob =
                CoroutineScope(Dispatchers.Main).launch {
                    while (isActive && !isServiceFound) {
                        startSingleDiscovery()
                        delay(DISCOVERY_INTERVAL)
                    }
                }
    }

    private fun startMonitoring() {
        monitoringJob =
                CoroutineScope(Dispatchers.Main).launch {
                    while (isActive) {
                        if (isServiceFound) {
                            // If service hasn't been seen for more than 2 intervals, consider it
                            // lost
                            if (System.currentTimeMillis() - lastServiceFoundTime >
                                            MONITORING_INTERVAL * 2
                            ) {
                                Log.d(TAG, "Service timeout detected")
                                handleServiceLost()
                                startDiscoveryLoop()
                            }
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
        startDiscovery()
    }

    private fun startSingleDiscovery() {
        stopCurrentDiscovery() // Stop current discovery listener only

        discoveryListener =
                object : NsdManager.DiscoveryListener {
                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                        Log.e(TAG, "Discovery failed to start with error code: $errorCode")
                        onStatusUpdate("Discovery failed to start")
                    }

                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                        Log.e(TAG, "Discovery failed to stop with error code: $errorCode")
                        onStatusUpdate("Discovery failed to stop")
                    }

                    override fun onDiscoveryStarted(serviceType: String) {
                        // Log.d(TAG, "Discovery started")
                        if (!isServiceFound) {
                            onStatusUpdate(
                                    "Discovery active: Searching for $TARGET_SERVICE_NAME..."
                            )
                        }
                    }

                    override fun onDiscoveryStopped(serviceType: String) {
                        // Log.d(TAG, "Discovery stopped")
                        if (!isServiceFound) {
                            onStatusUpdate("Discovery stopped - retrying...")
                        }
                    }

                    override fun onServiceFound(service: NsdServiceInfo) {
                        // Only resolve if it's our target service
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
                            _serverHost = null
                            _serverPort = -1
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
        nsdManager.resolveService(
                service,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(service: NsdServiceInfo, errorCode: Int) {
                        Log.e(TAG, "Resolve failed: $errorCode")
                        onStatusUpdate("Service resolution failed")
                        isServiceFound = false
                        _serverHost = null
                        _serverPort = -1
                    }

                    override fun onServiceResolved(service: NsdServiceInfo) {
                        Log.d(TAG, "Resolve succeeded: ${service.serviceName}")
                        Log.d(TAG, "Host: ${service.host.hostAddress}, Port: ${service.port}")

                        _serverHost = service.host.hostAddress
                        _serverPort = service.port

                        isServiceFound = true
                        onStatusUpdate("Connected to ${service.serviceName}")
                    }
                }
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
        discoveryJob?.cancel()
        discoveryJob = null
        monitoringJob?.cancel()
        monitoringJob = null
        stopCurrentDiscovery()
        _serverHost = null
        _serverPort = -1
    }

    companion object {
        const val TARGET_SERVICE_NAME = "TFCStreamServer"
    }
}
