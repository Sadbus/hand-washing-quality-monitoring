package com.example.ti.ble.sensortag;



import android.bluetooth.BluetoothDevice;

public class SensorTagUtil {

    public static boolean isSensorTag2(BluetoothDevice dev) {
        if (dev != null) {
            String name = dev.getName();
            if (name.compareTo("SensorTag2") == 0) return true;
            if (name.compareTo("SensorTag2.0") == 0) return true;
            if (name.compareTo("CC2650 SensorTag") == 0) return true;
            if (name.compareTo("CC2650 SensorTag LED") == 0) return true;
        }
        return false;
    }

}
