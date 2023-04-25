package com.kmitl.viai.ui.home
import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.kmitl.viai.databinding.FragmentHomeBinding
import java.util.UUID


class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null


    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var deviceListAdapter: ArrayAdapter<String>
    private lateinit var bluetoothGatt: BluetoothGatt
    private lateinit var characteristic: BluetoothGattCharacteristic

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textHome
        homeViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }

        val deviceEditText: EditText = binding.editTextDevice
        val connectButton: Button = binding.buttonConnect
        val disconnectButton: Button = binding.buttonDisconnect

        val listView: ListView = binding.deviceListView
        deviceListAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            mutableListOf()
        )
        listView.adapter = deviceListAdapter
        listView.setOnItemClickListener { _, _, position, _ ->
            val deviceName = deviceListAdapter.getItem(position)
            textView.text = "Connecting to $deviceName..."
            val device = if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                    REQUEST_PERMISSION_CODE
                )
                return@setOnItemClickListener
            } else {
                val device = bluetoothAdapter.bondedDevices.find { it.name == deviceName }
                if (device != null) {
                    bluetoothGatt = device.connectGatt(requireContext(), false, gattCallback)
                } else {
                    textView.text = "Device $deviceName not found"
                }
            }
        }
        val bMan = context?.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bMan.adapter

        if (bluetoothAdapter.isEnabled) {
            // Show paired devices
            deviceListAdapter.addAll(bluetoothAdapter.bondedDevices.map { it.name })
        } else {
            // Bluetooth is not enabled, ask user to enable it
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            resultLauncher.launch(enableBtIntent)
        }

        connectButton.setOnClickListener {
            val deviceName = deviceEditText.text.toString()
            val device = bluetoothAdapter.bondedDevices.find { it.name == deviceName }
            if (device != null) {
                textView.text = "Connecting to $deviceName..."
                bluetoothGatt = device.connectGatt(requireContext(), false, gattCallback)
            } else {
                textView.text = "Device $deviceName not found"
            }
        }

        disconnectButton.setOnClickListener {
//            val isGattConnect = bluetoothGatt?.device
//            if (isGattConnect != null) {
//                bluetoothGatt.disconnect()
//            }
            textView.text = "Disconnected"
        }

        return root
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // Permission not granted, request it
                    ActivityCompat.requestPermissions(
                        requireActivity(),
                        arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                        REQUEST_PERMISSION_CODE
                    )
                    return
                }
                // Permission granted, discover services
                gatt.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(SERVICE_UUID)
            characteristic = service.getCharacteristic(CHARACTERISTIC_UUID)
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Permission not granted, request it
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                    REQUEST_PERMISSION_CODE
                )
            } else {
                // Permission already granted, set characteristic notification
                gatt.setCharacteristicNotification(characteristic, true)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val textView: TextView = binding.textHome
            val value = characteristic.getStringValue(0)
            requireActivity().runOnUiThread {
                textView.text = value
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                REQUEST_PERMISSION_CODE
            )
            return
        }
        bluetoothGatt.close()
    }

    companion object {
        private const val REQUEST_PERMISSION_CODE = 1
        private val SERVICE_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        private val CHARACTERISTIC_UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
    }

    var resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // There are no request codes
                val data: Intent? = result.data
            }
        }

    val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // PERMISSION GRANTED
        } else {
            // PERMISSION NOT GRANTED
        }
    }

//    override fun onRequestPermissionsResult(
//        requestCode: Int,
//        permissions: Array<String>,
//        grantResults: IntArray
//    ) {
//        when (requestCode) {
//            REQUEST_PERMISSION_CODE -> {
//                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                    // Permission was granted, proceed with Bluetooth operation
//                    Toast.makeText(requireContext(), "Bluetooth permission granted", Toast.LENGTH_SHORT).show()
//                } else {
//                    // Permission denied, inform the user and disable Bluetooth functionality
//                    Toast.makeText(requireContext(), "Bluetooth permission denied", Toast.LENGTH_SHORT).show()
//                    disableBluetoothFunctionality()
//                }
//            }
//            else -> {
//                Toast.makeText(requireContext(), "Unknown permission $requestCode", Toast.LENGTH_SHORT).show()
//                super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//            }
//        }
//    }

    private fun disableBluetoothFunctionality() {
        // Disable UI elements related to Bluetooth functionality
        binding.buttonConnect.isEnabled = false
        binding.buttonDisconnect.isEnabled = false
        binding.editTextDevice.isEnabled = false
    }

}