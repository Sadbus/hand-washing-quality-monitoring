package com.example.ti.ble.sensortag;

//import com.google.gson.annotations.SerializedName;

public class DataSamples {

    private float Acc_X;
    private float Acc_Y;
    private float Acc_Z;

    private float Gyr_X;
    private float Gyr_Y;
    private float Gyr_Z;

    public DataSamples(float acc_X, float acc_Y, float acc_Z, float gyr_X, float gyr_Y, float gyr_Z )
    {
        this.Acc_X = acc_X;
        this.Acc_Y = acc_Y;
        this.Acc_Z = acc_Z;

        this.Gyr_X = gyr_X;
        this.Gyr_Y = gyr_Y;
        this.Gyr_Z = gyr_Z;

    }

}
