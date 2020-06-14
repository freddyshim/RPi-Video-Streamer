package com.anookday.rpistream.device

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.*
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.anookday.rpistream.databinding.DeviceListFragmentBinding
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.internal.synchronized
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
        binding.lifecycleOwner = this

        // initialize view model
        val viewModelFactory = DeviceListViewModelFactory()
        val viewModel = ViewModelProvider(this, viewModelFactory)
            .get(DeviceListViewModel::class.java)
        binding.viewModel = viewModel

        // initialize recyclerview
        binding.deviceListItem.adapter = DeviceListAdapter()

        // find connected USB devices
        // get USB connections
        Timber.i("Finding USB connections...")
        mPermissionIntent = PendingIntent.getBroadcast(mContext, 0, Intent(ACTION_USB_CONNECTION), 0)
        val filter = IntentFilter(ACTION_USB_CONNECTION)
        mContext.registerReceiver(usbReceiver, filter)

        // iterate through each USB connection
        val usbManager = mContext.getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList: HashMap<String, UsbDevice> = usbManager.deviceList
        Timber.i("${deviceList.size} devices found")
//        deviceList.values.forEach { device ->
//            Timber.i("Device found: $device")
//            usbManager.requestPermission(device, mPermissionIntent)
//            Timber.i("Device name: ${device.deviceName}")
//            Timber.i("Permission granted: ${usbManager.hasPermission(device)}")
//            val intf = device.getInterface(0)
//            val epc = intf.endpointCount
//            Timber.i("Endpoint count: $epc")
//            val connection = usbManager.openDevice(device)
//            if (connection != null) {
//                Timber.i("Sucessfully connected to $device")
//            }
//        }
        viewModel.setDevices(usbManager.deviceList)

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
                    Timber.i("$device")
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
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
