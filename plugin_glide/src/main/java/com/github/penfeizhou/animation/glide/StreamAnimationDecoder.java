package com.github.penfeizhou.animation.glide;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.ResourceDecoder;
import com.bumptech.glide.load.engine.Resource;
import com.github.penfeizhou.animation.apng.decode.APNGDecoder;
import com.github.penfeizhou.animation.apng.decode.APNGParser;
import com.github.penfeizhou.animation.decode.FrameSeqDecoder;
import com.github.penfeizhou.animation.gif.decode.GifDecoder;
import com.github.penfeizhou.animation.gif.decode.GifParser;
import com.github.penfeizhou.animation.io.Reader;
import com.github.penfeizhou.animation.io.StreamReader;
import com.github.penfeizhou.animation.loader.FileLoader;
import com.github.penfeizhou.animation.loader.Loader;
import com.github.penfeizhou.animation.webp.decode.WebPDecoder;
import com.github.penfeizhou.animation.webp.decode.WebPParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @Description: StreamAnimationDecoder
 * @Author: pengfei.zhou
 * @CreateDate: 2019-05-14
 */
public class StreamAnimationDecoder implements ResourceDecoder<InputStream, FrameSeqDecoder> {

    private static final String TAG = "StreamAnimDecoder";
    private static final String SPILL_DIR_NAME = "animation_spill";
    private static final String SPILL_FILE_PREFIX = "anim_";
    private static final String SPILL_FILE_SUFFIX = ".tmp";
    /** 落盘临时文件超过该时长未更新即允许清扫 */
    private static final long SPILL_STALE_MS = 24L * 60 * 60 * 1000;
    private static final AtomicLong SPILL_SEQ = new AtomicLong();

    private final ResourceDecoder<ByteBuffer, FrameSeqDecoder> byteBufferDecoder;
    private final Context mContext;

    public StreamAnimationDecoder(ResourceDecoder<ByteBuffer, FrameSeqDecoder> byteBufferDecoder, Context context) {
        this.byteBufferDecoder = byteBufferDecoder;
        this.mContext = context.getApplicationContext();
    }

    @Override
    public boolean handles(@NonNull InputStream source, @NonNull Options options) {
        return (!options.get(AnimationDecoderOption.DISABLE_ANIMATION_WEBP_DECODER) && WebPParser.isAWebP(new StreamReader(source)))
                || (!options.get(AnimationDecoderOption.DISABLE_ANIMATION_APNG_DECODER) && APNGParser.isAPNG(new StreamReader(source)))
                || (!options.get(AnimationDecoderOption.DISABLE_ANIMATION_GIF_DECODER) && GifParser.isGif(new StreamReader(source)));
    }

    @Nullable
    @Override
    public Resource<FrameSeqDecoder> decode(@NonNull final InputStream source, int width, int height, @NonNull Options options) throws IOException {
        Integer threshold = options.get(AnimationDecoderOption.ANIMATION_STREAM_SPILL_THRESHOLD);
        if (threshold != null && threshold > 0) {
            // 读流时带上限：小文件留在内存 buffer；大文件落盘按需解码。
            // 无论走哪条路，流只在这里消费一次，避免"先探测再重读"导致的丢头问题。
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.min(threshold, 64 * 1024));
            byte[] chunk = new byte[16 * 1024];
            int total = 0;
            int nRead;
            while (total < threshold && (nRead = source.read(chunk, 0, Math.min(chunk.length, threshold - total))) != -1) {
                buffer.write(chunk, 0, nRead);
                total += nRead;
            }
            if (total >= threshold) {
                Resource<FrameSeqDecoder> spilled = spillToDisk(source, buffer, total, chunk, options);
                if (spilled != null) {
                    return spilled;
                }
                // 落盘失败：继续把剩余流读完，退回全量内存路径（行为与旧版一致）
                ByteArrayOutputStream rest = new ByteArrayOutputStream(64 * 1024);
                rest.write(buffer.toByteArray(), 0, total);
                while ((nRead = source.read(chunk)) != -1) {
                    rest.write(chunk, 0, nRead);
                }
                ByteBuffer byteBuffer = ByteBuffer.wrap(rest.toByteArray());
                return byteBufferDecoder.decode(byteBuffer, width, height, options);
            }
            // 小文件：buffer 即完整数据
            ByteBuffer byteBuffer = ByteBuffer.wrap(buffer.toByteArray());
            return byteBufferDecoder.decode(byteBuffer, width, height, options);
        }
        byte[] data = inputStreamToBytes(source);
        if (data == null) {
            return null;
        }
        ByteBuffer byteBuffer = ByteBuffer.wrap(data);
        return byteBufferDecoder.decode(byteBuffer, width, height, options);
    }

    /**
     * 大文件磁盘中转：把已缓冲字节（buffer，total 字节）连同流中剩余数据写入 cacheDir 临时文件，
     * decoder 通过 FileLoader 按需逐帧 seek 读取，避免整个源文件常驻内存。
     *
     * @return 成功返回 Resource；落盘失败返回 null（流可能已被部分消费，由调用方兜底）
     */
    @Nullable
    private Resource<FrameSeqDecoder> spillToDisk(InputStream source, ByteArrayOutputStream buffer, int total, byte[] chunk, Options options) throws IOException {
        File spillDir = new File(mContext.getCacheDir(), SPILL_DIR_NAME);
        if (!spillDir.exists() && !spillDir.mkdirs()) {
            Log.w(TAG, "spill: cannot create dir " + spillDir);
            return null;
        }
        File file = new File(spillDir, SPILL_FILE_PREFIX + System.currentTimeMillis()
                + "_" + SPILL_SEQ.incrementAndGet() + SPILL_FILE_SUFFIX);
        FileOutputStream fos = null;
        boolean success = false;
        try {
            fos = new FileOutputStream(file);
            buffer.writeTo(fos);
            int nRead;
            while ((nRead = source.read(chunk)) != -1) {
                fos.write(chunk, 0, nRead);
                total += nRead;
            }
            fos.getFD().sync();
            success = true;
        } catch (IOException e) {
            Log.w(TAG, "spill: write failed", e);
            return null;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException ignored) {
                }
            }
            if (!success && file.exists()) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
        Log.i(TAG, "spill: " + total + " bytes -> " + file.getAbsolutePath());
        sweepStaleSpillFiles(mContext);
        FrameSeqDecoder decoder = createDecoder(new FileLoader(file.getAbsolutePath()), options);
        if (decoder == null) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            return null;
        }
        return new ByteBufferAnimationDecoder.FrameSeqDecoderResource(decoder, total);
    }

    /**
     * 按 Loader 创建对应格式的解码器；复用各 Parser 的格式嗅探能力。
     */
    @Nullable
    static FrameSeqDecoder createDecoder(Loader loader, Options options) throws IOException {
        Reader reader = loader.obtain();
        try {
            if (!options.get(AnimationDecoderOption.DISABLE_ANIMATION_WEBP_DECODER) && WebPParser.isAWebP(reader)) {
                return new WebPDecoder(loader, null);
            }
            reader.reset();
            if (!options.get(AnimationDecoderOption.DISABLE_ANIMATION_APNG_DECODER) && APNGParser.isAPNG(reader)) {
                return new APNGDecoder(loader, null);
            }
            reader.reset();
            if (!options.get(AnimationDecoderOption.DISABLE_ANIMATION_GIF_DECODER) && GifParser.isGif(reader)) {
                return new GifDecoder(loader, null);
            }
            return null;
        } finally {
            reader.close();
        }
    }

    /**
     * 清扫超过 24 小时未更新的落盘文件。临时文件不能在 decoder stop/recycle 时删除
     * （decoder 生命周期长于 Glide 的中间 Resource，且 stop/start 可反复），以过期时间兜底。
     * 可在进程启动时主动调用一次，清理上次进程残留。
     */
    public static void sweepStaleSpillFiles(Context context) {
        final File dir = new File(context.getApplicationContext().getCacheDir(), SPILL_DIR_NAME);
        final File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        final long now = System.currentTimeMillis();
        for (File file : files) {
            if (now - file.lastModified() > SPILL_STALE_MS) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    private static byte[] inputStreamToBytes(InputStream is) {
        final int bufferSize = 16384;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(bufferSize);
        try {
            int nRead;
            byte[] data = new byte[bufferSize];
            while ((nRead = is.read(data)) != -1) {
                buffer.write(data, 0, nRead);
            }
            buffer.flush();
        } catch (IOException e) {
            return null;
        }
        return buffer.toByteArray();
    }
}
