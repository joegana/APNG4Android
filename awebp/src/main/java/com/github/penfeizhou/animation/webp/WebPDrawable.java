package com.github.penfeizhou.animation.webp;


import android.content.Context;

import com.github.penfeizhou.animation.FrameAnimationDrawable;
import com.github.penfeizhou.animation.decode.FrameSeqDecoder;
import com.github.penfeizhou.animation.loader.AssetStreamLoader;
import com.github.penfeizhou.animation.loader.FileLoader;
import com.github.penfeizhou.animation.loader.Loader;
import com.github.penfeizhou.animation.loader.ResourceStreamLoader;
import com.github.penfeizhou.animation.webp.decode.WebPDecoder;

/**
 * @Description: Animated webp drawable
 * @Author: pengfei.zhou
 * @CreateDate: 2019/3/27
 */
public class WebPDrawable extends FrameAnimationDrawable<WebPDecoder> {

    public WebPDrawable(Context context,Loader provider) {
        super(context,provider);
    }

    public WebPDrawable(Context context,WebPDecoder decoder) {
        super(context,decoder);
    }

    @Override
    protected WebPDecoder createFrameSeqDecoder(Loader streamLoader, FrameSeqDecoder.RenderListener listener) {
        return new WebPDecoder(streamLoader, listener);
    }

    public static WebPDrawable fromAsset(Context context, String assetPath) {
        AssetStreamLoader assetStreamLoader = new AssetStreamLoader(context, assetPath);
        return new WebPDrawable(context,assetStreamLoader);
    }

    public static WebPDrawable fromFile(Context context,String filePath) {
        FileLoader fileLoader = new FileLoader(filePath);
        return new WebPDrawable(context,fileLoader);
    }

    public static WebPDrawable fromResource(Context context, int resId) {
        ResourceStreamLoader resourceStreamLoader = new ResourceStreamLoader(context, resId);
        return new WebPDrawable(context,resourceStreamLoader);
    }
}