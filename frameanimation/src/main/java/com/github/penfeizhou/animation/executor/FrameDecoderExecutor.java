package com.github.penfeizhou.animation.executor;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FrameDecoderExecutor {
    private final String TAG = "FrameDecoderExecutor" ;
    private static int sPoolNumber = 4;
    private ScheduledExecutorService executor;

    private FrameDecoderExecutor() {
        executor = Executors.newScheduledThreadPool(sPoolNumber);
    }

    static class Inner {
        static final FrameDecoderExecutor sInstance = new FrameDecoderExecutor();
    }

    public void setPoolSize(int size) {
        sPoolNumber = size;
    }

    public static FrameDecoderExecutor getInstance() {
        return FrameDecoderExecutor.Inner.sInstance;
    }

    public Future post(Runnable run){
        return executor.submit(run);
    }

    public Future post(Runnable run, long delay){

       return executor.schedule(run,delay, TimeUnit.MILLISECONDS);
    }
}
