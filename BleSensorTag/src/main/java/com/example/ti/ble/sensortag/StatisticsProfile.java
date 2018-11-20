
package com.example.ti.ble.sensortag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.util.Log;

import com.example.ti.ble.common.BluetoothLeService;
import com.example.ti.ble.common.GattInfo;
import com.example.ti.ble.common.GenericBluetoothProfile;
import com.example.ti.util.GenericCharacteristicTableRow;
import com.example.ti.util.Point3D;

public class StatisticsProfile extends GenericBluetoothProfile {
    public HandWashStatistics hwStats;

    public StatisticsProfile(Context con, BluetoothDevice device, BluetoothGattService service, BluetoothLeService controller, HandWashStatistics hwstat) {
        super(con,device,service,controller);
        this.tRow =  new GenericCharacteristicTableRow(con);
        this.hwStats = hwstat;

        // Period Bar

        List<BluetoothGattCharacteristic> characteristics = this.mBTService.getCharacteristics();

        for (BluetoothGattCharacteristic c : characteristics) {
            if (c.getUuid().toString().equals(SensorTagGatt.UUID_HUM_DATA.toString())) {
                this.dataC = c;
            }
            if (c.getUuid().toString().equals(SensorTagGatt.UUID_HUM_CONF.toString())) {
                this.configC = c;
            }
            if (c.getUuid().toString().equals(SensorTagGatt.UUID_HUM_PERI.toString())) {
                this.periodC = c;
            }
        }

        this.tRow.setIcon(this.getIconPrefix(), this.dataC.getUuid().toString());

        this.tRow.title.setText("Hand Wash Statistics");
//        this.tRow.uuidLabel.setText(this.dataC.getUuid().toString());
//        this.tRow.value.setText("0.0%rH");
        this.tRow.periodBar.setProgress(100);
    }

    public static boolean isCorrectService(BluetoothGattService service) {
        if ((service.getUuid().toString().compareTo(SensorTagGatt.UUID_HUM_SERV.toString())) == 0) {//service.getUuid().toString().compareTo(SensorTagGatt.UUID_HUM_DATA.toString())) {
            Log.d("Test", "Match !");
            return true;
        }
        else return false;
    }
    public void didWriteValueForCharacteristic(BluetoothGattCharacteristic c) {

    }
    public void didReadValueForCharacteristic(BluetoothGattCharacteristic c) {

    }
    @Override
    public void didUpdateValueForCharacteristic(BluetoothGattCharacteristic c) {
        byte[] value = c.getValue();
        if (c.equals(this.dataC)){
        }
        // Prints out the values from the handwash
        //this.tRow.value.setText(String.format("Bad Wash: %s, Ok Wash: %s, Good Wash: %s\nAmount of Washes: %s\nScore: %.2f", hwStats.getBadWash(), hwStats.getOkWash(), hwStats.getGoodWash(),hwStats.getTotalWash(), hwStats.getScore()));
        this.tRow.value.setText(hwStats.mHandWash);
//
// Log.e("Wash", "Statis Init" + hwStats.getBadWash()+ hwStats.getOkWash()+ hwStats.getGoodWash()+hwStats.getTotalWash()+hwStats.getScore() );
        //Log.e("Wash", "OK Handwash: " + hwStats.getOkWash());
    }
    @Override
    public Map<String,String> getMQTTMap() {
        Point3D v;
        if (SensorTagUtil.isSensorTag2(mBTDevice)) {
            v = Sensor.HUMIDITY2.convert(this.dataC.getValue());
        }
        else  v = Sensor.HUMIDITY.convert(this.dataC.getValue());
        Map<String,String> map = new HashMap<String, String>();
        map.put("humidity",String.format("%.2f",v.x));
        return map;
    }
}