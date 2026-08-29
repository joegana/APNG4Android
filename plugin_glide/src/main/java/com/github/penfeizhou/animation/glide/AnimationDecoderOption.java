package com.github.penfeizhou.animation.glide;

import com.bumptech.glide.load.Option;

/**
 * @Description: AnimationDecoderOption
 * @Author: pengfei.zhou
 * @CreateDate: 2019-06-05
 */
public final class AnimationDecoderOption {

    /**
     * If set to {@code true}, disables the Frame Animation Decoder {@link com.github.penfeizhou.animation.gif.GifDrawable}
     * Defaults to {@code false}.
     */
    public static final Option<Boolean> DISABLE_ANIMATION_GIF_DECODER = Option.memory(
            "com.github.penfeizhou.animation.glide.AnimationDecoderOption.DISABLE_ANIMATION_GIF_DECODER", false);
    /**
     * If set to {@code true}, disables the Frame Animation Decoder {@link com.github.penfeizhou.animation.webp.WebPDrawable}
     * Defaults to {@code false}.
     */
    public static final Option<Boolean> DISABLE_ANIMATION_WEBP_DECODER = Option.memory(
            "com.github.penfeizhou.animation.glide.AnimationDecoderOption.DISABLE_ANIMATION_WEBP_DECODER", false);
    /**
     * If set to {@code true}, disables the Frame Animation Decoder {@link com.github.penfeizhou.animation.apng.APNGDrawable}
     * Defaults to {@code false}.
     */
    public static final Option<Boolean> DISABLE_ANIMATION_APNG_DECODER = Option.memory(
            "com.github.penfeizhou.animation.glide.AnimationDecoderOption.DISABLE_ANIMATION_APNG_DECODER", false);

    /**
     * If set to {@code true},  call {@link com.github.penfeizhou.animation.FrameAnimationDrawable#setNoMeasure(boolean)}
     * Defaults to {@code false}.
     */
    public static final Option<Boolean> NO_ANIMATION_BOUNDS_MEASURE = Option.memory(
            "com.github.penfeizhou.animation.glide.AnimationDecoderOption.DISABLE_ANIMATION_BOUNDS_MEASURE", false);


    /**
     * 流式加载动画时，超过该字节数的源数据不再整体驻留内存，
     * 而是落盘到 cacheDir 临时文件并按需逐帧 seek 解码（内存占用从 O(文件大小) 降为 O(画布大小)）。
     * 默认 2MB；设为小于等于 0 关闭落盘，保持旧的全量内存行为。
     */
    public static final Option<Integer> ANIMATION_STREAM_SPILL_THRESHOLD = Option.memory(
            "com.github.penfeizhou.animation.glide.AnimationDecoderOption.ANIMATION_STREAM_SPILL_THRESHOLD",
            2 * 1024 * 1024);

    private AnimationDecoderOption() {
    }
}
