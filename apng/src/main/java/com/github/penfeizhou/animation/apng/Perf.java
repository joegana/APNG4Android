package com.github.penfeizhou.animation.apng;

import android.os.SystemClock;
import android.util.Log;

public class Perf {
    private static String TAG = "ApngPerf";
    private static  long START_TIME = 0 ;
    public static void perfBegin(String msg){
        Log.d(TAG,String.format("perf %s start",msg));
        START_TIME = SystemClock.uptimeMillis();
    }

    public static void perfEnd(String msg){
        Log.d(TAG,String.format("perf %s End,time used: %d ms",msg,SystemClock.uptimeMillis() - START_TIME));
    }
}
