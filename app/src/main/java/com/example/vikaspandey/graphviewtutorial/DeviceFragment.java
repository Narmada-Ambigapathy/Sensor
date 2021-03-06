package com.example.vikaspandey.graphviewtutorial;

import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import info.plux.pluxapi.BTHDeviceScan;
import info.plux.pluxapi.Communication;
import info.plux.pluxapi.Constants;
import info.plux.pluxapi.bitalino.BITalinoCommunication;
import info.plux.pluxapi.bitalino.BITalinoCommunicationFactory;
import info.plux.pluxapi.bitalino.BITalinoException;


public class DeviceFragment extends DialogFragment implements Toolbar.OnMenuItemClickListener {
    private static final String TAG = "DeviceFragment";
    private BTHDeviceScan bthDeviceScan;
    private Handler mHandler;
    private boolean mScanning;
    private BluetoothAdapter mBluetoothAdapter;
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 2;
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;
    private BITalinoCommunication bitalino;
    private Handler handler;
    private boolean isScanDevicesUpdateReceiverRegistered = false;
    private ArrayList<BluetoothDevice> mDeviceList = new ArrayList<>();
    ArrayList<String> deviceArray;
    RecyclerView recyclerView;
    DeviceListAdapter deviceListAdapter;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager = (BluetoothManager) getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();


        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(getActivity(), "Error - Bluetooth not supported", Toast.LENGTH_SHORT).show();
            getActivity().finish();
            return;
        }
        bthDeviceScan = new BTHDeviceScan(getContext());


    }



    @Override
    public void onResume() {
        super.onResume();


        getActivity().getApplicationContext().registerReceiver(scanDevicesUpdateReceiver, new IntentFilter(Constants.ACTION_MESSAGE_SCAN));
        isScanDevicesUpdateReceiverRegistered = true;
        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            // Log.info("Enabling bluetooth");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        deviceListAdapter = new DeviceListAdapter(getContext(), mDeviceList);

        recyclerView.setAdapter(deviceListAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        scanDevice(true);
    }

    public final BroadcastReceiver scanDevicesUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "onReceive: " + action);
            if (action.equals(Constants.ACTION_MESSAGE_SCAN)) {
                BluetoothDevice bluetoothDevice = intent.getParcelableExtra(Constants.EXTRA_DEVICE_SCAN);

                if (bluetoothDevice != null) {
                    Log.d(TAG, "bluetoothDevice found: " + bluetoothDevice.getName() + " - " + bluetoothDevice.getAddress());
                    mDeviceList.add(bluetoothDevice);
                    deviceListAdapter.notifyDataSetChanged();

                }
            }
        }
    };

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == getActivity().RESULT_CANCELED) {
            getActivity().finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View dialogView = inflater.inflate(R.layout.dialogfragment, container);

        Toolbar toolbar = (Toolbar) dialogView.findViewById(R.id.my_toolbar);
        toolbar.inflateMenu(R.menu.main);

        toolbar.setTitle("Scan Devices");

        setUpRecyclerView(dialogView);


        return dialogView;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_scan:
                deviceListAdapter.clear();
                scanDevice(true);
                break;
            case R.id.menu_stop:
                scanDevice(false);
                break;
        }
        return true;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.main, menu);
        super.onCreateOptionsMenu(menu,inflater);
        if (!mScanning) {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(null);
        } else {
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(R.layout.actionbar_indeterminate_progress);
        }
    }

    private void setUpRecyclerView(View  view) {
        recyclerView = (RecyclerView) view.findViewById(R.id.dialog_fragment_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

    }


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        // request a window without the title
        dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    @Override
    public void onPause() {
        super.onPause();
        scanDevice(false);
        //deviceListAdapter.
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (bthDeviceScan != null) {
            bthDeviceScan.closeScanReceiver();
        }

        if (isScanDevicesUpdateReceiverRegistered) {
            getActivity().getApplicationContext().unregisterReceiver(scanDevicesUpdateReceiver);
        }
    }

    private void scanDevice(final boolean enable) {
        if (enable) {


            if (mBluetoothAdapter.getBondedDevices().size() == 0)
                Toast.makeText(getActivity(), "No Paired Device", Toast.LENGTH_SHORT).show();
            if (getActivity() != null) {
                if (isVisible())
                    dismiss();
            }


            mScanning = true;

            bthDeviceScan.doDiscovery();
        } else {
            mScanning = false;

            bthDeviceScan.stopScan();
        }
    }

    // Adapter for holding devices found through scanning.
    private class DeviceListAdapter extends RecyclerView.Adapter<ViewHolder> {
        private ArrayList<BluetoothDevice> devices;
        Context c;
        private LayoutInflater mInflator;

        public DeviceListAdapter(Context c, ArrayList<BluetoothDevice> devices) {
            this.c = c;
            this.devices = devices;
            mInflator = (LayoutInflater) c.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }


        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item, parent, false);
            ViewHolder viewHolder = new ViewHolder(v);
            return viewHolder;
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

            final BluetoothDevice device = devices.get(position);
            holder.deviceAddress.setText(device.getAddress());
            holder.deviceName.setText(device.getName());
            holder.layout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    if (mScanning) {
                        bthDeviceScan.stopScan();
                        mScanning = false;
                    }


                    //if device is already paired directly connect
                    // android.support.v4.app.FragmentTransaction ft = getActivity().getSupportFragmentManager().beginTransaction();

                    // TabFragment1 tb1 = new TabFragment1();
                    // Bundle args = new Bundle();
                    // args.putParcelable("EXTRA_DEVICE",device);
                    // tb1.setArguments(args);

                    // ft.replace(R.id.dialog_fragment_view,tb1);
                    //  ft.commit();
                    if (!mBluetoothAdapter.getBondedDevices().contains(device)) {
                        FragmentManager fm = getActivity().getSupportFragmentManager();
                        DevicePinFragment sc = new DevicePinFragment();
                        Set<BluetoothDevice> deviceSet = new HashSet<BluetoothDevice>();
                        deviceSet.add(device);
                        Bundle args = new Bundle();
                        args.putParcelable("EXTRA_DEVICE", device);
                        args.putParcelableArrayList("PAIRED_DEVICE", new ArrayList(deviceSet));
                        sc.setArguments(args);
                        sc.show(fm, "device list");


                    }

                    //else device is not paired show pin dialog

                    Intent i = new Intent(getActivity().getApplicationContext(), TabFragment1.class);
                    i.putExtra("EXTRA_DEVICE", device);
                    i.putParcelableArrayListExtra("PAIRED_DEVICE", new ArrayList(mBluetoothAdapter.getBondedDevices()));
                    getTargetFragment().onActivityResult(getTargetRequestCode(), Activity.RESULT_OK, i);
                    getDialog().dismiss();

                }
            });
        }

        @Override
        public int getItemCount() {
            return devices.size();
        }

        public void clear() {
            devices.clear();
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
        LinearLayout layout;

        public ViewHolder(View itemView) {
            super(itemView);
            deviceName = itemView.findViewById(R.id.text);
            deviceAddress = itemView.findViewById(R.id.address);
            layout = itemView.findViewById(R.id.llayout);
        }


    }


}
