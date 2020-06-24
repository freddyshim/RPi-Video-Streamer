package com.anookday.rpistream.fragments

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.anookday.rpistream.adapters.DeviceClickListener
import com.anookday.rpistream.adapters.DeviceListAdapter
import com.anookday.rpistream.databinding.DeviceListFragmentBinding
import com.anookday.rpistream.viewmodels.DeviceListViewModel
import timber.log.Timber

private const val ACTION_USB_CONNECTION = "com.anookday.rpistream.USB_CONNECTION"

class DeviceListFragment: Fragment() {
    private lateinit var mPermissionIntent: PendingIntent
    private lateinit var mContext: Context

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // initialize binding
        val binding = DeviceListFragmentBinding.inflate(inflater)

        // initialize view model
        val viewModel = ViewModelProvider(this).get(DeviceListViewModel::class.java)
        binding.viewModel = viewModel

        // get USB connections
        mPermissionIntent = PendingIntent.getBroadcast(mContext, 0, Intent(ACTION_USB_CONNECTION), 0)
        val filter = IntentFilter(ACTION_USB_CONNECTION)
        mContext.registerReceiver(usbReceiver, filter)
        val usbManager = mContext.getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList: HashMap<String, UsbDevice> = usbManager.deviceList

        // get user permission to connect to USB devices
        deviceList.forEach { usbManager.requestPermission(it.value, mPermissionIntent) }

        // initialize recyclerview
        binding.deviceListItem.adapter =
            DeviceListAdapter(DeviceClickListener { device ->
                viewModel.onDeviceClicked(device)
            })

        // populate recyclerview
        viewModel.setDevices(usbManager.deviceList)

        // enable navigation to device detail fragment
        viewModel.navigateToDeviceDetails.observe(viewLifecycleOwner, Observer { device ->
            device?.let  {
                this.findNavController().navigate(DeviceListFragmentDirections.actionDeviceListFragmentToDeviceDetailFragment(device))
                viewModel.onDeviceDetailNavigated()
            }
        })

        return binding.root
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mContext = context
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_CONNECTION == intent.action) {
                kotlin.synchronized(this) {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        Timber.i("Permission granted for ${device?.deviceId}")
                        device?.apply {
                            // call method to set up device communication
                        }
                    } else {
                        Timber.d("Permission denied for $device")
                    }
                }
            }
        }
    }

}
