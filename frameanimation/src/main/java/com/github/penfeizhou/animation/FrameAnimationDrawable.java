package com.github.penfeizhou.animation;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.DrawFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import com.github.penfeizhou.animation.decode.FrameSeqDecoder;
import com.github.penfeizhou.animation.loader.Loader;
import com.moorgen.sdk.common.CUtilKt;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.vectordrawable.graphics.drawable.Animatable2Compat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Description: Frame animation drawable
 * @Author: pengfei.zhou
 * @CreateDate: 2019/3/27
 */
public abstract class FrameAnimationDrawable<Decoder extends FrameSeqDecoder>
        extends Drawable implements Animatable2Compat, FrameSeqDecoder.RenderListener {
    private static Logger logger = LoggerFactory.getLogger("apng.FrameAnimationDrawable");
    private static final String TAG = FrameAnimationDrawable.class.getSimpleName();
    private final Paint paint = new Paint();
    private final Decoder frameSeqDecoder;
    private Context context;
    private final DrawFilter drawFilter = new PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Matrix matrix = new Matrix();
    private final Set<AnimationCallback> animationCallbacks = new HashSet<>();
    private Bitmap bitmap;
    private String mResName;
    private boolean autoPlay = true;

    private final Set<WeakReference<Callback>> obtainedCallbacks = new HashSet<>();

    private boolean noMeasure = false;

    public FrameAnimationDrawable(@NonNull Context context,@NonNull  Decoder frameSeqDecoder) {
        this.context = context;
        paint.setAntiAlias(true);
        this.frameSeqDecoder = frameSeqDecoder;
        mResName = CUtilKt.format("%s@%d", frameSeqDecoder.getResName(),this.hashCode());
    }

    public FrameAnimationDrawable(@NonNull Context context,@NonNull Loader provider) {
        this.context = context;
        paint.setAntiAlias(true);
        this.frameSeqDecoder = createFrameSeqDecoder(provider, this);
        mResName = CUtilKt.format("%s@%d", frameSeqDecoder.getResName(),this.hashCode());
    }

    public void setAutoPlay(boolean autoPlay) {
        this.autoPlay = autoPlay;
    }

    public void setNoMeasure(boolean noMeasure) {
        this.noMeasure = noMeasure;
    }

    protected abstract Decoder createFrameSeqDecoder(Loader streamLoader, FrameSeqDecoder.RenderListener listener);

     /**
     * @param loopLimit 小于等于0为无限播放,  大于0为实际播放次数
     */
    public void setLoopLimit(int loopLimit) {
        frameSeqDecoder.setLoopLimit(loopLimit);
    }

    public void reset() {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.eraseColor(Color.TRANSPARENT);
        }
        frameSeqDecoder.reset();
    }

    public void pause() {
        frameSeqDecoder.pause();
    }

    public void resume() {
        frameSeqDecoder.resume();
    }

    public boolean isPaused() {
        return frameSeqDecoder.isPaused();
    }

    @Override
    public void start() {
        if (this.frameSeqDecoder.isRunning()) {
            this.frameSeqDecoder.stop();
        }
        this.frameSeqDecoder.reset();
        innerStart();
    }

    private void innerStart() {
        logger.debug("{} , start ！",mResName);

        this.frameSeqDecoder.addRenderListener(this);
        if (autoPlay) {
            frameSeqDecoder.start();
        } else {
            if (!this.frameSeqDecoder.isRunning()) {
                this.frameSeqDecoder.start();
            }
        }
        logger.debug("{} , start End！",mResName);
    }

    @Override
    public void stop() {
        innerStop();
    }

    private void innerStop() {
        logger.debug("{} , stop ！",mResName);

        this.frameSeqDecoder.removeRenderListener(this);
        if (autoPlay) {
            frameSeqDecoder.stop();
        } else {
            this.frameSeqDecoder.stopIfNeeded();
        }
        logger.debug("{} , stop End！",mResName);
    }

    @Override
    public boolean isRunning() {
        return frameSeqDecoder.isRunning();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }
        canvas.setDrawFilter(drawFilter);
        canvas.drawBitmap(bitmap, matrix, paint);
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);
        Rect wBounds = getBounds();
        Rect dBounds = frameSeqDecoder.getBounds();
        boolean sampleSizeChanged = frameSeqDecoder.setDesiredSize(wBounds.width(), wBounds.height());
        matrix.setScale(
                1.0f * getBounds().width() * frameSeqDecoder.getSampleSize() / dBounds.width(),
                1.0f * getBounds().height() * frameSeqDecoder.getSampleSize() / dBounds.height());

        if (sampleSizeChanged) {
            this.bitmap = Bitmap.createBitmap(
                    context.getResources().getDisplayMetrics(),
                    dBounds.width() / frameSeqDecoder.getSampleSize(),
                    dBounds.height() / frameSeqDecoder.getSampleSize(),
                    Bitmap.Config.ARGB_8888);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override

    public void onStart() {
        CUtilKt.callOnMain(0, null, () -> {
            ArrayList<AnimationCallback> callbacks = new ArrayList<>(animationCallbacks);
            for (AnimationCallback animationCallback : callbacks) {
                animationCallback.onAnimationStart(FrameAnimationDrawable.this);
            }
        });
    }

    @Override
    public void onRender(ByteBuffer byteBuffer) {
        if (!isRunning()) {
            return;
        }
        if (this.bitmap == null || this.bitmap.isRecycled()) {
            this.bitmap = Bitmap.createBitmap(
                            context.getResources().getDisplayMetrics(),
                    frameSeqDecoder.getBounds().width() / frameSeqDecoder.getSampleSize(),
                    frameSeqDecoder.getBounds().height() / frameSeqDecoder.getSampleSize(),
                    Bitmap.Config.ARGB_8888);
        }
        byteBuffer.rewind();
        if (byteBuffer.remaining() < this.bitmap.getByteCount()) {
            Log.e(TAG, "onRender:Buffer not large enough for pixels");
            return;
        }
        this.bitmap.copyPixelsFromBuffer(byteBuffer);

        CUtilKt.callOnMain(0, null, this::invalidateSelf);
    }

    @Override
    public void onEnd() {
        CUtilKt.callOnMain(0, null, () -> {
            ArrayList<AnimationCallback> callbacks = new ArrayList<>(animationCallbacks);
            for (AnimationCallback animationCallback : callbacks) {
                animationCallback.onAnimationEnd(FrameAnimationDrawable.this);
            }
        });
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        hookRecordCallbacks();
        if (this.autoPlay) {
            logger.debug("{} , visible:{}, restart:{}",mResName,visible, restart);
            if (visible) {
                if (!isRunning()) {
                    innerStart();
                }
            } else if (isRunning()) {
                innerStop();
            }
        }
        return super.setVisible(visible, restart);
    }

    @Override
    public int getIntrinsicWidth() {
        if (noMeasure) {
            return -1;
        }
        try {
            return frameSeqDecoder.getBounds().width();
        } catch (Exception exception) {
            return 0;
        }
    }

    @Override
    public int getIntrinsicHeight() {
        if (noMeasure) {
            return -1;
        }
        try {
            return frameSeqDecoder.getBounds().height();
        } catch (Exception exception) {
            return 0;
        }
    }

    @Override
    public void registerAnimationCallback(@NonNull AnimationCallback animationCallback) {
        this.animationCallbacks.add(animationCallback);
    }

    @Override
    public boolean unregisterAnimationCallback(@NonNull AnimationCallback animationCallback) {
        return this.animationCallbacks.remove(animationCallback);
    }

    @Override
    public void clearAnimationCallbacks() {
        this.animationCallbacks.clear();
    }

    public int getMemorySize() {
        int size = frameSeqDecoder.getMemorySize();
        if (bitmap != null && !bitmap.isRecycled()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                size += bitmap.getAllocationByteCount();
            } else {
                size += bitmap.getByteCount();
            }
        }
        return Math.max(1, size);
    }

    @Nullable
    @Override
    public Callback getCallback() {
        return super.getCallback();
    }

    private void hookRecordCallbacks() {
        List<WeakReference<Callback>> lost = new ArrayList<>();
        Callback callback = getCallback();
        boolean recorded = false;
        Set<WeakReference<Callback>> temp = new HashSet<>(obtainedCallbacks);
        for (WeakReference<Callback> ref : temp) {
            Callback cb = ref.get();
            if (cb == null) {
                lost.add(ref);
            } else {
                if (cb == callback) {
                    recorded = true;
                } else {
                    cb.invalidateDrawable(this);
                }
            }
        }
        for (WeakReference<Callback> ref : lost) {
            obtainedCallbacks.remove(ref);
        }
        if (!recorded) {
            obtainedCallbacks.add(new WeakReference<>(callback));
        }
    }

    @Override
    public void invalidateSelf() {
        super.invalidateSelf();
        Set<WeakReference<Callback>> temp = new HashSet<>(obtainedCallbacks);
        for (WeakReference<Callback> ref : temp) {
            Callback callback = ref.get();
            if (callback != null) {
                callback.invalidateDrawable(this);
            }
        }
    }

    public Decoder getFrameSeqDecoder() {
        return frameSeqDecoder;
    }

    /**
     * 获取当前正在显示的Bitmap
     * @return
     */
    public Bitmap getCurrentFrame(){
        return bitmap;
    }

    public String getResName(){
        return mResName;
    }
}
