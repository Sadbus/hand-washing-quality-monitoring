package com.example.ti.ble.sensortag;

import android.util.Log;

public class HandWashStatistics {

    private int BadWash;
    private int OkWash;
    private int GoodWash;

    public HandWashStatistics()
    {
        this.BadWash = 0;
        this.OkWash = 0;
        this.GoodWash = 0;
    }
    public float getScore()
    {
        return (float)(GoodWash + 3*OkWash + 5*GoodWash)/50;
    }
    public int getTotalWash(){
        return BadWash+OkWash+GoodWash;
    }

    // Increments a wash 1 = Bad, 2 = Ok, 3 = Good
    public void updateWash(int wash)
    {
        switch (wash)
        {
            case 1:
                BadWash++;
                break;

            case 2:
                OkWash++;
                break;

            case 3:
                GoodWash++;
                break;

            default:
                break;
        }
    }
    public int getBadWash() {
        return BadWash;
    }
    public int getOkWash() {
        return OkWash;
    }
    public int getGoodWash() {
        return GoodWash;
    }


}
