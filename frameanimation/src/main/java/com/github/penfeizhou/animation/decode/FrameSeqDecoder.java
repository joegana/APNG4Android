package com.github.penfeizhou.animation.decode;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.os.Trace;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import com.github.penfeizhou.animation.executor.FrameDecoderExecutor;
import com.github.penfeizhou.animation.frame.BuildConfig;
import com.github.penfeizhou.animation.io.Reader;
import com.github.penfeizhou.animation.io.Writer;
import com.github.penfeizhou.animation.loader.Loader;
import com.moorgen.sdk.common.CUtilKt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class FrameSeqDecoder <R extends Reader, W extends Writer> {
    private static Logger logger = LoggerFactory.getLogger("apng.FrameSeqDecoder");
    private static final String TAG = FrameSeqDecoder.class.getSimpleName();
    private static final Rect RECT_EMPTY = new Rect();
    private String mResName;
    private final Loader mLoader;
    private FrameDecoderExecutor executor;
    protected List<Frame<R, W>> frames = new ArrayList<>();
    protected int frameIndex = -1;
    private int playCount;
    private Integer loopLimit = null;
    private final Set<FrameSeqDecoder.RenderListener> renderListeners = new HashSet<>();
    private final AtomicBoolean paused = new AtomicBoolean(true);
    private final Runnable renderTask = new Runnable() {
        @Override
        public void run() {
            if (paused.get()) {
                return;
            }
            if (canStep()) {
                Trace.beginSection("renderTask:"+Thread.currentThread().getName());
                long start = System.currentTimeMillis();
                long delay = step();
                long cost = System.currentTimeMillis() - start;
                remove(renderTaskHl);
                renderTaskHl =  post(this, Math.max(0, delay - cost));
                Set<FrameSeqDecoder.RenderListener> renders = new HashSet<>(renderListeners);
                CUtilKt.callOnMain(0, null, () -> {
                    for (RenderListener renderListener : renders) {
                        renderListener.onRender(frameBuffer);
                    }
                });

                Trace.endSection();
            } else {
                stop();
            }
        }
    };
    private ArrayList<Future> taskHls = new ArrayList<>();
    private Future renderTaskHl;
    protected int sampleSize = 1;

    private final Set<Bitmap> cacheBitmaps = new HashSet<>();
    private final Object cacheBitmapsLock = new Object();

    protected Map<Bitmap, Canvas> cachedCanvas = new WeakHashMap<>();
    protected ByteBuffer frameBuffer;
    protected volatile Rect fullRect;
    private W mWriter = getWriter();
    private R mReader = null;
    public static final boolean DEBUG = BuildConfig.DEBUG;
    /**
     * If played all the needed
     */
    private boolean finished = false;

    private enum State {
        IDLE,
        RUNNING,
        INITIALIZING,
        FINISHING,
    }

    private volatile FrameSeqDecoder.State mState = FrameSeqDecoder.State.IDLE;

    protected abstract W getWriter();

    protected abstract R getReader(Reader reader);

    protected Bitmap obtainBitmap(int width, int height) {
        synchronized (cacheBitmapsLock) {
            Bitmap ret = null;
            Iterator<Bitmap> iterator = cacheBitmaps.iterator();
            while (iterator.hasNext()) {
                int reuseSize = width * height * 4;
                ret = iterator.next();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    if (ret != null && ret.getAllocationByteCount() >= reuseSize) {
                        iterator.remove();
                        if ((ret.getWidth() != width || ret.getHeight() != height)) {
                            if (width > 0 && height > 0) {
                                ret.reconfigure(width, height, Bitmap.Config.ARGB_8888);
                            }
                        }
                        ret.eraseColor(0);
                        return ret;
                    }
                } else {
                    if (ret != null && ret.getByteCount() >= reuseSize) {
                        if (ret.getWidth() == width && ret.getHeight() == height) {
                            iterator.remove();
                            ret.eraseColor(0);
                        }
                        return ret;
                    }
                }
            }

            if (width <= 0 || height <= 0) {
                return null;
            }
            try {
                Bitmap.Config config = Bitmap.Config.ARGB_8888;
                ret = Bitmap.createBitmap(width, height, config);
            } catch (Exception | OutOfMemoryError e) {
                logger.error("{} obtainBitmap:",mResName,e);
            }
            return ret;
        }
    }

    protected void recycleBitmap(Bitmap bitmap) {
        synchronized (cacheBitmapsLock) {
            if (bitmap != null) {
                cacheBitmaps.add(bitmap);
            }
        }
    }

    /**
     * 解码器的渲染回调
     */
    public interface RenderListener {
        /**
         * 播放开始
         */
        void onStart();

        /**
         * 帧播放
         */
        void onRender(ByteBuffer byteBuffer);

        /**
         * 播放结束
         */
        void onEnd();
    }

    /**
     * @param loader         webp的reader
     * @param renderListener 渲染的回调
     */
    public FrameSeqDecoder(Loader loader, @Nullable FrameSeqDecoder.RenderListener renderListener) {
        this.mLoader = loader;
        this.mResName = loader.getResName();
        if (renderListener != null) {
            this.renderListeners.add(renderListener);
        }
        executor  = FrameDecoderExecutor.getInstance();
    }


    public void addRenderListener(final FrameSeqDecoder.RenderListener renderListener) {
        renderListeners.add(renderListener);
    }

    public void removeRenderListener(final FrameSeqDecoder.RenderListener renderListener) {
        renderListeners.remove(renderListener);
    }

    public void stopIfNeeded() {
        if (renderListeners.isEmpty()) {
            stop();
        }
    }

    public Rect getBounds() {
        if (fullRect == null) {
            if (mState == FrameSeqDecoder.State.FINISHING) {
                logger.warn("{}:In finishing,do not interrupt",mResName);
            }
            long start = System.currentTimeMillis();
            FutureTask<Rect> task = new FutureTask<>(() -> {
                try {
                    if (fullRect == null) {
                        if (mReader == null) {
                            mReader = getReader(mLoader.obtain());
                        } else {
                            mReader.reset();
                        }
                        initCanvasBounds(read(mReader));
                    }
                } catch (Exception e) {
                    logger.error("{} getBounds error:",mResName,e);
                    fullRect = RECT_EMPTY;
                }
                return fullRect;
            });
           post(task);
            try {
                task.get();
            }catch (Exception e){
                logger.error("{} getBounds error:",mResName,e);
            }
            long timeUsed = System.currentTimeMillis() - start;
            logger.info("{} getBounds time used:{} ms",mResName,timeUsed);
        }
        return fullRect == null ? RECT_EMPTY : fullRect;
    }

    private void initCanvasBounds(Rect rect) {
        fullRect = rect;
        frameBuffer = ByteBuffer.allocateDirect((rect.width() * rect.height() / (sampleSize * sampleSize) + 1) * 4);
        if (mWriter == null) {
            mWriter = getWriter();
        }
        logger.info("{} initCanvasBounds:{}",mResName,fullRect);
    }


    public int getFrameCount() {
        return this.frames.size();
    }

    /**
     * @return Loop Count defined in file
     */
    protected abstract int getLoopCount();

    public void start() {
        if (fullRect == RECT_EMPTY) {
            return;
        }
        if (mState == FrameSeqDecoder.State.RUNNING || mState == FrameSeqDecoder.State.INITIALIZING) {
            logger.debug("{}:{}, Already started",mResName,debugInfo());
            return;
        }
        if (mState == FrameSeqDecoder.State.FINISHING) {
            logger.debug( "{}:{}  Processing,wait for finish at {}" ,mResName,debugInfo(), mState);
        }
        if (DEBUG) {
            logger.debug("{}:{} Set state to INITIALIZING",mResName,debugInfo());
        }
        mState = FrameSeqDecoder.State.INITIALIZING;
        post(this::innerStart);
    }

    @WorkerThread
    private void innerStart() {
        paused.compareAndSet(true, false);

        final long start = System.currentTimeMillis();
        try {
            if (frames.isEmpty()) {
                try {
                    if (mReader == null) {
                        mReader = getReader(mLoader.obtain());
                    } else {
                        mReader.reset();
                    }
                    initCanvasBounds(read(mReader));
                } catch (Throwable e) {
                    logger.error("{}:innerStart error ",mResName,e);
                }
            }
        } finally {
            logger.info("{}:{} Set state to RUNNING,cost {} ms", mResName,debugInfo(),
                    (System.currentTimeMillis() - start));
            mState = FrameSeqDecoder.State.RUNNING;
        }
        if (getNumPlays() == 0 || !finished) {
            this.frameIndex = -1;
            renderTask.run();
            Set<FrameSeqDecoder.RenderListener> renders = new HashSet<>(renderListeners);
            CUtilKt.callOnMain(0, null, () -> {
                for (RenderListener renderListener : renders) {
                    renderListener.onStart();
                }
            });

        } else {
            logger.info("{}:{},No need to started",mResName,debugInfo());
        }
    }

    @WorkerThread
    private void innerStop() {
        for (Future<?> taskHl : taskHls) {
            taskHl.cancel(true);
        }
        taskHls.clear();
        frames.clear();
        synchronized (cacheBitmapsLock) {
            for (Bitmap bitmap : cacheBitmaps) {
                if (bitmap != null && !bitmap.isRecycled()) {
                    bitmap.recycle();
                }
            }
            cacheBitmaps.clear();
        }
        if (frameBuffer != null) {
            frameBuffer = null;
        }
        cachedCanvas.clear();
        try {
            if (mReader != null) {
                mReader.close();
                mReader = null;
            }
            if (mWriter != null) {
                mWriter.close();
            }
        } catch (IOException e) {
            logger.error("{}:innerStop",mResName,e);
        }
        release();
        logger.debug("{}:{}  release and Set state to IDLE",mResName,debugInfo());
        mState = FrameSeqDecoder.State.IDLE;
        Set<FrameSeqDecoder.RenderListener> renders = new HashSet<>(renderListeners);
        CUtilKt.callOnMain(0, null, () -> {
            for (RenderListener renderListener : renders) {
                renderListener.onEnd();
            }
        });

    }

    public void stop() {
        if (fullRect == RECT_EMPTY) {
            return;
        }
        if (mState == FrameSeqDecoder.State.FINISHING || mState == FrameSeqDecoder.State.IDLE) {
            logger.info("{}:{} No need to stop",mResName,debugInfo());
            return;
        }
        if (mState == FrameSeqDecoder.State.INITIALIZING) {
            logger.info("{}:{} Processing,wait for finish at {}",mResName,debugInfo(),mState);
        }

        logger.debug("{}:{}  Set state to finishing",mResName,debugInfo());

        mState = FrameSeqDecoder.State.FINISHING;
        post(this::innerStop);
    }

    private String debugInfo() {
        if (DEBUG) {
            return String.format(Locale.getDefault(),"thread is %s, decoder is %s,state is %s",
                    Thread.currentThread(), FrameSeqDecoder.this, mState.toString());
        }
        return "";
    }

    protected abstract void release();

    public boolean isRunning() {
        return mState == FrameSeqDecoder.State.RUNNING || mState == FrameSeqDecoder.State.INITIALIZING;
    }

    public boolean isPaused() {
        return paused.get();
    }

    public void setLoopLimit(int limit) {
        this.loopLimit = limit;
    }

    public void reset() {
        playCount = 0;
        frameIndex = -1;
        finished = false;
    }

    public void pause() {
        remove(renderTaskHl);
        paused.compareAndSet(false, true);
    }

    public void resume() {
        paused.compareAndSet(true, false);
        remove(renderTaskHl);
        post(renderTask);
    }


    public int getSampleSize() {
        return sampleSize;
    }

    public boolean setDesiredSize(int width, int height) {
        boolean sampleSizeChanged = false;
        final int sample = getDesiredSample(width, height);
        if (sample != this.sampleSize) {
            sampleSizeChanged = true;
            final boolean tempRunning = isRunning();
            remove(renderTaskHl);
            post(() -> {
                innerStop();
                try {
                    sampleSize = sample;
                    initCanvasBounds(read(getReader(mLoader.obtain())));
                    if (tempRunning) {
                        innerStart();
                    }
                } catch (IOException e) {
                    logger.error("{}:setDesiredSize:",mResName,e);
                }
            });
        }
        return sampleSizeChanged;
    }

    protected int getDesiredSample(int desiredWidth, int desiredHeight) {
        if (desiredWidth == 0 || desiredHeight == 0) {
            return 1;
        }
        int radio = Math.min(getBounds().width() / desiredWidth, getBounds().height() / desiredHeight);
        int sample = 1;
        while ((sample * 2) <= radio) {
            sample *= 2;
        }
        return sample;
    }

    protected abstract Rect read(R reader) throws IOException;

    private int getNumPlays() {
        return this.loopLimit != null ? this.loopLimit : this.getLoopCount();
    }

    private boolean canStep() {
        if (!isRunning()) {
            return false;
        }
        if (frames.isEmpty()) {
            return false;
        }
        if (getNumPlays() <= 0) {
            return true;
        }
        if (this.playCount < getNumPlays() - 1) {
            return true;
        } else if (this.playCount == getNumPlays() - 1 && this.frameIndex < this.getFrameCount() - 1) {
            return true;
        }
        finished = true;
        return false;
    }

    @WorkerThread
    private long step() {
        this.frameIndex++;
        if (this.frameIndex >= this.getFrameCount()) {
            this.frameIndex = 0;
            this.playCount++;
        }
        Frame<R, W> frame = getFrame(this.frameIndex);
        if (frame == null) {
            return 0;
        }
        renderFrame(frame);
        return frame.frameDuration;
    }

    protected abstract void renderFrame(Frame<R, W> frame);

    public Frame<R, W> getFrame(int index) {
        if (index < 0 || index >= frames.size()) {
            return null;
        }
        return frames.get(index);
    }

    /**
     * Get Indexed frame
     *
     * @param index <0 means reverse from last index
     */
    public Bitmap getFrameBitmap(int index) throws IOException {
        if (mState != FrameSeqDecoder.State.IDLE) {
            logger.error("{}: {} ,stop first",mResName,debugInfo());
            return null;
        }
        mState = FrameSeqDecoder.State.RUNNING;
        paused.compareAndSet(true, false);
        if (frames.isEmpty()) {
            if (mReader == null) {
                mReader = getReader(mLoader.obtain());
            } else {
                mReader.reset();
            }
            initCanvasBounds(read(mReader));
        }
        if (index < 0) {
            index += this.frames.size();
        }
        if (index < 0) {
            index = 0;
        }
        frameIndex = -1;
        while (frameIndex < index) {
            if (canStep()) {
                step();
            } else {
                break;
            }
        }
        frameBuffer.rewind();
        Bitmap bitmap = Bitmap.createBitmap(getBounds().width() / getSampleSize(), getBounds().height() / getSampleSize(), Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(frameBuffer);
        innerStop();
        return bitmap;
    }

    public int getMemorySize() {
        synchronized (cacheBitmapsLock) {
            int size = 0;
            for (Bitmap bitmap : cacheBitmaps) {
                if (bitmap.isRecycled()) {
                    continue;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    size += bitmap.getAllocationByteCount();
                } else {
                    size += bitmap.getByteCount();
                }
            }
            if (frameBuffer != null) {
                size += frameBuffer.capacity();
            }
            return size;
        }
    }

    private Future post(Runnable runnable){
        Future f = executor.post(runnable);
        taskHls.add(f);
        return f ;
    }

    private Future post(Runnable runnable,long delay){
        Future f = executor.post(runnable,delay);
        taskHls.add(f);
        return f ;
    }

    private void remove(Future f){
        if(f != null) {
            f.cancel(true);
            taskHls.remove(f);
        }
    }


    public Rect getEmptyRect(){
        return  RECT_EMPTY;
    }
}
