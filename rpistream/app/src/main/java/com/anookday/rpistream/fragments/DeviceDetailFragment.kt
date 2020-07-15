package com.anookday.rpistream.fragments

import android.content.Context
import android.hardware.usb.*
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.anookday.rpistream.databinding.DeviceDetailFragmentBinding
import com.anookday.rpistream.viewmodels.DeviceDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.nio.ByteBuffer

class DeviceDetailFragment: Fragment() {
    private lateinit var mContext: Context
    private lateinit var bytes: ByteArray
    private lateinit var usbManager: UsbManager
    private lateinit var usbDevice: UsbDevice
    private lateinit var usbInterface: UsbInterface
    private lateinit var usbEndpoint: UsbEndpoint
    private lateinit var usbDeviceConnection: UsbDeviceConnection

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = DeviceDetailFragmentBinding.inflate(inflater)
        usbManager = mContext.getSystemService(Context.USB_SERVICE) as UsbManager

        val viewModel = ViewModelProvider(this).get(DeviceDetailViewModel::class.java)
        binding.viewModel = viewModel

        val arguments = DeviceDetailFragmentArgs.fromBundle(requireArguments())
        val device = arguments.device

        viewModel.setDevice(device)
        viewModel.streamData.observe(viewLifecycleOwner, Observer { value ->
            value?.let { binding.streamOutput.text = value }
        })

        // connect to USB device and receive stream data
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                usbDevice = device.device
                usbInterface = usbDevice.getInterface(0)
                usbDeviceConnection = usbManager.openDevice(usbDevice)
                usbDeviceConnection.claimInterface(usbInterface, true)
                for (i in 0 until usbInterface.endpointCount) {
                    val endpoint = usbInterface.getEndpoint(i)
                    if (endpoint.direction == UsbConstants.USB_DIR_IN) {
                        usbEndpoint = endpoint
                        break
                    }
                }
                val usbRequest = UsbRequest()
                val initialized = usbRequest.initialize(usbDeviceConnection, usbEndpoint)

                if (!initialized) {
                    Timber.i("Failed to connect to USB device.")
                } else {
                    while (true) {
                        val buffer = ByteBuffer.allocate(usbEndpoint.maxPacketSize)
                        if (usbRequest.queue(buffer)) {
                            if (usbDeviceConnection.requestWait() == usbRequest) {
                                val result = buffer.array().toString()
                                Timber.i("Stream output result: $result")
                            }
                        }
                    }
                }
            }
        }

        return binding.root
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mContext = context
    }
}