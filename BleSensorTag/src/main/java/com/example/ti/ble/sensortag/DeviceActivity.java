/**************************************************************************************************
 Filename:       DeviceActivity.java

 Copyright (c) 2013 - 2014 Texas Instruments Incorporated

 All rights reserved not granted herein.
 Limited License.

 Texas Instruments Incorporated grants a world-wide, royalty-free,
 non-exclusive license under copyrights and patents it now or hereafter
 owns or controls to make, have made, use, import, offer to sell and sell ("Utilize")
 this software subject to the terms herein.  With respect to the foregoing patent
 license, such license is granted  solely to the extent that any such patent is necessary
 to Utilize the software alone.  The patent license shall not apply to any combinations which
 include this software, other than combinations with devices manufactured by or for TI ('TI Devices').
 No hardware patent is licensed hereunder.

 Redistributions must preserve existing copyright notices and reproduce this license (including the
 above copyright notice and the disclaimer and (if applicable) source code license limitations below)
 in the documentation and/or other materials provided with the distribution

 Redistribution and use in binary form, without modification, are permitted provided that the following
 conditions are met:

 * No reverse engineering, decompilation, or disassembly of this software is permitted with respect to any
 software provided in binary form.
 * any redistribution and use are licensed by TI for use only with TI Devices.
 * Nothing shall obligate TI to provide you with source code for the software licensed and provided to you in object code.

 If software source code is provided to you, modification and redistribution of the source code are permitted
 provided that the following conditions are met:

 * any redistribution and use of the source code, including any resulting derivative works, are licensed by
 TI for use only with TI Devices.
 * any redistribution and use of any object code compiled from the source code and any resulting derivative
 works, are licensed by TI for use only with TI Devices.

 Neither the name of Texas Instruments Incorporated nor the names of its suppliers may be used to endorse or
 promote products derived from this software without specific prior written permission.

 DISCLAIMER.

 THIS SOFTWARE IS PROVIDED BY TI AND TI'S LICENSORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 IN NO EVENT SHALL TI AND TI'S LICENSORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 POSSIBILITY OF SUCH DAMAGE.


 **************************************************************************************************/
package com.example.ti.ble.sensortag;

import java.util.ArrayList;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Toast;


import com.example.ti.ble.btsig.profiles.DeviceInformationServiceProfile;
import com.example.ti.ble.common.BluetoothGATTDefines;
import com.example.ti.ble.common.BluetoothLeService;
import com.example.ti.ble.common.GattInfo;
import com.example.ti.ble.common.GenericBluetoothProfile;
import com.example.ti.util.PreferenceWR;


@SuppressLint("InflateParams")
public class DeviceActivity extends ViewPagerActivity
{
    // Activity
    public static final String EXTRA_DEVICE = "EXTRA_DEVICE";
    private static final int PREF_ACT_REQ = 0;
    private static final int FWUPDATE_ACT_REQ = 1;

    private DeviceView mDeviceView = null;

    private HandWashStatistics hwStatMain = new HandWashStatistics();

    // BLE
    private BluetoothLeService mBtLeService = null;
    private BluetoothDevice mBluetoothDevice = null;
    private BluetoothGatt mBtGatt = null;
    private List<BluetoothGattService> mServiceList = null;
    private boolean mServicesRdy = false;
    private boolean mIsReceiving = false;

    // SensorTagGatt
    private BluetoothGattService mConnControlService = null;
    private BluetoothGattService mTestService = null;
    private boolean mIsSensorTag2;
    private String mFwRev;
    public ProgressDialog progressDialog;

    //GUI
    private List<GenericBluetoothProfile> mProfiles;

    public DeviceActivity()
    {
        mResourceFragmentPager = R.layout.fragment_pager;
        mResourceIdPager = R.id.pager;
        mFwRev = "1.5"; // Assuming all SensorTags are up to date until actual FW revision is read
    }

    public static DeviceActivity getInstance()
    {
        return (DeviceActivity) mThis;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();

        // BLE
        mBtLeService = BluetoothLeService.getInstance();
        mBluetoothDevice = intent.getParcelableExtra(EXTRA_DEVICE);
        mServiceList = new ArrayList<>();

        mIsSensorTag2 = false;
        // Determine type of SensorTagGatt
        String deviceName = mBluetoothDevice.getName();
        if ((deviceName.equals("SensorTag2")) || (deviceName.equals("CC2650 SensorTag")))
        {
            mIsSensorTag2 = true;
        } else mIsSensorTag2 = false;

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        // Log.i(TAG, "Preferences for: " + deviceName);

        // GUI
        mDeviceView = new DeviceView();
        mSectionsPagerAdapter.addSection(mDeviceView, "Sensors");
        mProfiles = new ArrayList<>();
        progressDialog = new ProgressDialog(DeviceActivity.this);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setIndeterminate(true);
        progressDialog.setTitle("Discovering Services");
        progressDialog.setMessage("");
        progressDialog.setMax(100);
        progressDialog.setProgress(0);
        progressDialog.show();

        // GATT database
        Resources res = getResources();
        XmlResourceParser xpp = res.getXml(R.xml.gatt_uuid);
        new GattInfo(xpp);
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        if (mIsReceiving)
        {
            unregisterReceiver(mGattUpdateReceiver);
            mIsReceiving = false;
        }
        for (GenericBluetoothProfile p : mProfiles)
        {
            p.onPause();
        }
        if (!this.isEnabledByPrefs("keepAlive"))
        {
            this.mBtLeService.timedDisconnect();
        }
        //View should be started again from scratch
        this.mDeviceView.first = true;
        this.mProfiles = null;
        this.mDeviceView.removeRowsFromTable();
        this.mDeviceView = null;
        finishActivity(PREF_ACT_REQ);
        finishActivity(FWUPDATE_ACT_REQ);
    }


    public boolean isEnabledByPrefs(String prefName)
    {
        String preferenceKeyString = "pref_"
                + prefName;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mBtLeService);

        Boolean defaultValue = true;
        return prefs.getBoolean(preferenceKeyString, defaultValue);
    }

    @Override
    protected void onResume()
    {
        // Log.d(TAG, "onResume");
        super.onResume();
        if (!mIsReceiving)
        {
            registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
            mIsReceiving = true;
        }
        for (GenericBluetoothProfile p : mProfiles)
        {
            if (!p.isConfigured) p.configureService();
            if (!p.isEnabled) p.enableService();
            p.onResume();
        }
        this.mBtLeService.abortTimedDisconnect();
    }

    @Override
    protected void onPause()
    {
        // Log.d(TAG, "onPause");
        super.onPause();
    }

    private static IntentFilter makeGattUpdateIntentFilter()
    {
        final IntentFilter fi = new IntentFilter();
        fi.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        fi.addAction(BluetoothLeService.ACTION_DATA_NOTIFY);
        fi.addAction(BluetoothLeService.ACTION_DATA_WRITE);
        fi.addAction(BluetoothLeService.ACTION_DATA_READ);
        fi.addAction(DeviceInformationServiceProfile.ACTION_FW_REV_UPDATED);
        return fi;
    }

    void onViewInflated(View view)
    {
        // Log.d(TAG, "Gatt view ready");
        setBusy(true);

        // Set title bar to device name
        setTitle(mBluetoothDevice.getName());

        // Create GATT object
        mBtGatt = BluetoothLeService.getBtGatt();

        PreferenceWR p = new PreferenceWR(mBluetoothDevice.getAddress(), this);
        if (p.getBooleanPreference(PreferenceWR.PREFERENCEWR_NEEDS_REFRESH))
        {
            Log.d("DeviceActivity", "Need to refresh device cache, calling refreshDeviceCache()");
            progressDialog.setTitle("Refreshing device cache ");
            boolean refresh = this.mBtLeService.refreshDeviceCache(this.mBtGatt);
            //We need a wait here, because this takes time ...
            if (refresh == true)
            {
                if (!mServicesRdy && mBtGatt != null)
                {
                    if (mBtLeService.getNumServices() == 0)
                    {
                        progressDialog.setTitle("Refreshing device cache ");
                        discoverServices();
                    }
                }
            }
            p.setBooleanPreference(PreferenceWR.PREFERENCEWR_NEEDS_REFRESH, false);
        } else
        {
            // Start service discovery
            if (!mServicesRdy && mBtGatt != null)
            {
                if (mBtLeService.getNumServices() == 0)
                    discoverServices();
            }
        }
    }

    boolean isSensorTag2()
    {
        return mIsSensorTag2;
    }

    String firmwareRevision()
    {
        return mFwRev;
    }

    private void startPreferenceActivity()
    {
        // Launch preferences
        final Intent i = new Intent(this, PreferencesActivity.class);
        i.putExtra(PreferencesActivity.EXTRA_SHOW_FRAGMENT,
                PreferencesFragment.class.getName());
        i.putExtra(PreferencesActivity.EXTRA_NO_HEADERS, true);
        i.putExtra(EXTRA_DEVICE, mBluetoothDevice);
        startActivityForResult(i, PREF_ACT_REQ);
    }

    private void discoverServices()
    {
        if (mBtGatt.discoverServices())
        {
            mServiceList.clear();
            setBusy(true);
        }
    }

    private void setBusy(boolean b)
    {
        mDeviceView.setBusy(b);
    }

    // Activity result handling
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode)
        {
            default:
                break;
        }
    }

    private void setError(String txt)
    {
        setBusy(false);
        Toast.makeText(this, txt, Toast.LENGTH_LONG).show();
    }

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver()
    {
        List<BluetoothGattService> serviceList;
        List<BluetoothGattCharacteristic> charList = new ArrayList<>();

        @Override
        public void onReceive(final Context context, Intent intent)
        {
            final String action = intent.getAction();
            final int status = intent.getIntExtra(BluetoothLeService.EXTRA_STATUS,
                    BluetoothGatt.GATT_SUCCESS);

            if (DeviceInformationServiceProfile.ACTION_FW_REV_UPDATED.equals(action))
            {
                mFwRev = intent.getStringExtra(DeviceInformationServiceProfile.EXTRA_FW_REV_STRING);
                Log.d("DeviceActivity", "Got FW revision : " + mFwRev + " from DeviceInformationServiceProfile");
                for (GenericBluetoothProfile p : mProfiles)
                {
                    p.didUpdateFirmwareRevision(mFwRev);
                }
            }
            if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action))
            {
                if (status == BluetoothGatt.GATT_SUCCESS)
                {

                    serviceList = mBtLeService.getSupportedGattServices();
                    if (serviceList.size() > 0)
                    {
                        for (int ii = 0; ii < serviceList.size(); ii++)
                        {
                            BluetoothGattService s = serviceList.get(ii);
                            List<BluetoothGattCharacteristic> c = s.getCharacteristics();
                            if (c.size() > 0)
                            {
                                charList.addAll(c);
                            }
                        }
                    }
                    Log.d("DeviceActivity", "Total characteristics " + charList.size());
                    Thread worker = new Thread(new Runnable()
                    {
                        @Override
                        public void run()
                        {

                            //Iterate through the services and add GenericBluetoothServices for each service
                            int nrNotificationsOn = 0;
                            int maxNotifications;
                            int servicesDiscovered = 0;
                            int totalCharacteristics = 0;
                            //serviceList = mBtLeService.getSupportedGattServices();
                            for (BluetoothGattService s : serviceList)
                            {
                                List<BluetoothGattCharacteristic> chars = s.getCharacteristics();
                                totalCharacteristics += chars.size();
                            }
                            //Special profile for Cloud service

                            if (totalCharacteristics == 0)
                            {
                                //Something bad happened, we have a problem
                                runOnUiThread(new Runnable()
                                {
                                    @Override
                                    public void run()
                                    {
                                        progressDialog.hide();
                                        progressDialog.dismiss();
                                        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                                                context);
                                        alertDialogBuilder.setTitle("Error !");
                                        alertDialogBuilder.setMessage(serviceList.size() + " Services found, but no characteristics found, device will be disconnected !");
                                        alertDialogBuilder.setPositiveButton("Retry", new DialogInterface.OnClickListener()
                                        {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which)
                                            {
                                                mBtLeService.refreshDeviceCache(mBtGatt);
                                                //Try again
                                                discoverServices();
                                            }
                                        });
                                        alertDialogBuilder.setNegativeButton("Disconnect", new DialogInterface.OnClickListener()
                                        {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which)
                                            {
                                                mBtLeService.disconnect(mBluetoothDevice.getAddress());
                                            }
                                        });
                                        AlertDialog a = alertDialogBuilder.create();
                                        a.show();
                                    }
                                });
                                return;
                            }

                            final int final_totalCharacteristics = totalCharacteristics;
                            runOnUiThread(new Runnable()
                            {
                                @Override
                                public void run()
                                {
                                    progressDialog.setIndeterminate(false);
                                    progressDialog.setTitle("Generating GUI");
                                    progressDialog.setMessage("Found a total of " + serviceList.size() + " services with a total of " + final_totalCharacteristics + " characteristics on this device");

                                }
                            });
                            maxNotifications = 7;
                            for (int ii = 0; ii < serviceList.size(); ii++)
                            {
                                BluetoothGattService s = serviceList.get(ii);
                                List<BluetoothGattCharacteristic> chars = s.getCharacteristics();
                                if (chars.size() == 0)
                                {

                                    Log.d("DeviceActivity", "No characteristics found for this service !!!");

                                }
                                servicesDiscovered++;
                                final float serviceDiscoveredcalc = (float) servicesDiscovered;
                                final float serviceTotalcalc = (float) serviceList.size();
                                runOnUiThread(new Runnable()
                                {
                                    @Override
                                    public void run()
                                    {
                                        progressDialog.setProgress((int) ((serviceDiscoveredcalc / (serviceTotalcalc - 1)) * 100));
                                    }
                                });
                                Log.d("DeviceActivity", "Configuring service with uuid : " + s.getUuid().toString());

                                if (SensorTagMovementProfile.isCorrectService(s))
                                {
                                    SensorTagMovementProfile mov = new SensorTagMovementProfile(context, mBluetoothDevice, s, mBtLeService, hwStatMain);
                                    mProfiles.add(mov);
                                    if (nrNotificationsOn < maxNotifications)
                                    {
                                        mov.configureService();
                                        nrNotificationsOn++;
                                    } else
                                    {
                                        mov.grayOutCell(true);
                                    }
                                    Log.d("DeviceActivity", "Found Motion !");
                                }
                                if (SensorTagAccelerometerProfile.isCorrectService(s))
                                {
                                    SensorTagAccelerometerProfile acc = new SensorTagAccelerometerProfile(context, mBluetoothDevice, s, mBtLeService);
                                    mProfiles.add(acc);
                                    if (nrNotificationsOn < maxNotifications)
                                    {
                                        acc.configureService();
                                        nrNotificationsOn++;
                                    } else
                                    {
                                        acc.grayOutCell(true);
                                    }
                                    Log.d("DeviceActivity", "Found Motion !");

                                }
                                if (StatisticsProfile.isCorrectService(s))
                                {
                                    StatisticsProfile acc = new StatisticsProfile(context, mBluetoothDevice, s, mBtLeService, hwStatMain);
                                    mProfiles.add(acc);
                                    if (nrNotificationsOn < maxNotifications)
                                    {
                                        acc.configureService();
                                        nrNotificationsOn++;
                                    } else
                                    {
                                        acc.grayOutCell(true);
                                    }
                                    Log.d("DeviceActivity", "Found Motion !");

                                }
                                if (SensorTagTestProfile.isCorrectService(s))
                                {
                                    mTestService = s;
                                }
                                if ((s.getUuid().toString().compareTo("f000ccc0-0451-4000-b000-000000000000")) == 0)
                                {
                                    mConnControlService = s;
                                }
                            }
                            runOnUiThread(new Runnable()
                            {
                                @Override
                                public void run()
                                {
                                    progressDialog.setTitle("Enabling Services");
                                    progressDialog.setMax(mProfiles.size());
                                    progressDialog.setProgress(0);
                                }
                            });
                            for (final GenericBluetoothProfile p : mProfiles)
                            {

                                runOnUiThread(new Runnable()
                                {
                                    @Override
                                    public void run()
                                    {
                                        mDeviceView.addRowToTable(p.getTableRow());
                                        p.enableService();
                                        progressDialog.setProgress(progressDialog.getProgress() + 1);
                                    }
                                });
                                p.onResume();
                            }
                            runOnUiThread(new Runnable()
                            {
                                @Override
                                public void run()
                                {
                                    progressDialog.hide();
                                    progressDialog.dismiss();
                                }
                            });
                        }
                    });
                    worker.start();
                } else
                {
                    Toast.makeText(getApplication(), "Service discovery failed",
                            Toast.LENGTH_LONG).show();
                    return;
                }
            } else if (BluetoothLeService.ACTION_DATA_NOTIFY.equals(action))
            {
                // Notification
                byte[] value = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
                String uuidStr = intent.getStringExtra(BluetoothLeService.EXTRA_UUID);
                //Log.d("DeviceActivity","Got Characteristic : " + uuidStr);
                for (int ii = 0; ii < charList.size(); ii++)
                {
                    BluetoothGattCharacteristic tempC = charList.get(ii);
                    if ((tempC.getUuid().toString().equals(uuidStr)))
                    {
                        for (int jj = 0; jj < mProfiles.size(); jj++)
                        {
                            GenericBluetoothProfile p = mProfiles.get(jj);
                            if (p.isDataC(tempC))
                            {
                                p.didUpdateValueForCharacteristic(tempC);
                            }
                        }
                        //Log.d("DeviceActivity","Got Characteristic : " + tempC.getUuid().toString());
                        break;
                    }
                }

                //onCharacteristicChanged(uuidStr, value);
            } else if (BluetoothLeService.ACTION_DATA_WRITE.equals(action))
            {
                // Data written
                byte[] value = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
                String uuidStr = intent.getStringExtra(BluetoothLeService.EXTRA_UUID);
                for (int ii = 0; ii < charList.size(); ii++)
                {
                    BluetoothGattCharacteristic tempC = charList.get(ii);
                    if ((tempC.getUuid().toString().equals(uuidStr)))
                    {
                        for (int jj = 0; jj < mProfiles.size(); jj++)
                        {
                            GenericBluetoothProfile p = mProfiles.get(jj);
                            p.didWriteValueForCharacteristic(tempC);
                        }
                        //Log.d("DeviceActivity","Got Characteristic : " + tempC.getUuid().toString());
                        break;
                    }
                }
            } else if (BluetoothLeService.ACTION_DATA_READ.equals(action))
            {
                // Data read
                byte[] value = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
                String uuidStr = intent.getStringExtra(BluetoothLeService.EXTRA_UUID);
                for (int ii = 0; ii < charList.size(); ii++)
                {
                    BluetoothGattCharacteristic tempC = charList.get(ii);
                    if ((tempC.getUuid().toString().equals(uuidStr)))
                    {
                        for (int jj = 0; jj < mProfiles.size(); jj++)
                        {
                            GenericBluetoothProfile p = mProfiles.get(jj);
                            p.didReadValueForCharacteristic(tempC);
                        }
                        //Log.d("DeviceActivity","Got Characteristic : " + tempC.getUuid().toString());
                        break;
                    }
                }
            }
            if (status != BluetoothGatt.GATT_SUCCESS)
            {
                try
                {
                    Log.d("DeviceActivity", "Failed UUID was " + intent.getStringExtra(BluetoothLeService.EXTRA_UUID));
                    setError("GATT error code: " + BluetoothGATTDefines.gattErrorCodeStrings.get(status));
                } catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
            
        }
    };
}
