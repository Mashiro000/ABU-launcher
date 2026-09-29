package com.limi.tvdesktop.plugins.runtime

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import org.json.JSONObject

/** App-lifetime device notifications; plugins receive them only while their surface is active. */
object PluginDeviceEvents {
    @Volatile private var started = false

    fun start(context: Context) {
        if (started) return
        synchronized(this) {
            if (started) return
            val app = context.applicationContext
            val filter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val topic = when (intent.action) {
                        UsbManager.ACTION_USB_DEVICE_ATTACHED -> "device.usb.attached"
                        UsbManager.ACTION_USB_DEVICE_DETACHED -> "device.usb.detached"
                        BluetoothDevice.ACTION_ACL_CONNECTED -> "device.bluetooth.connected"
                        BluetoothDevice.ACTION_ACL_DISCONNECTED -> "device.bluetooth.disconnected"
                        else -> return
                    }
                    val payload = JSONObject()
                    if (topic.startsWith("device.usb")) {
                        val device = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        else @Suppress("DEPRECATION") intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        device?.let { payload.put("id", it.deviceId).put("vendorId", it.vendorId).put("productId", it.productId) }
                    }
                    PluginEventBus.publish("host", topic, payload)
                }
            }
            if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else @Suppress("DEPRECATION") app.registerReceiver(receiver, filter)
            started = true
        }
    }
}
