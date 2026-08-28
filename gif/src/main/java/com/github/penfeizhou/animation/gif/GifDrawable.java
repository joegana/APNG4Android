package com.github.penfeizhou.animation.gif;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import com.github.penfeizhou.animation.FrameAnimationDrawable;
import com.github.penfeizhou.animation.decode.FrameSeqDecoder;
import com.github.penfeizhou.animation.gif.decode.GifDecoder;
import com.github.penfeizhou.animation.loader.AssetStreamLoader;
import com.github.penfeizhou.animation.loader.FileLoader;
import com.github.penfeizhou.animation.loader.Loader;
import com.github.penfeizhou.animation.loader.ResourceStreamLoader;

/**
 * @Description: GifDrawable
 * @Author: pengfei.zhou
 * @CreateDate: 2019-05-16
 */
public class GifDrawable extends FrameAnimationDrawable<GifDecoder> {
    public GifDrawable(Context context,Loader provider) {
        super(context,provider);
    }

    public GifDrawable(Context context,GifDecoder decoder) {
        super(context,decoder);
    }

    @Override
    protected GifDecoder createFrameSeqDecoder(Loader loader, FrameSeqDecoder.RenderListener listener) {
        return new GifDecoder(loader, listener);
    }


    public static GifDrawable fromAsset(@NonNull Context context, @NonNull String assetPath) {
        if(TextUtils.isEmpty(assetPath)){
            return null ;
        }
        AssetStreamLoader assetStreamLoader = new AssetStreamLoader(context, assetPath);
        return new GifDrawable(context,assetStreamLoader);
    }

    public static GifDrawable fromFile(@NonNull Context context,@NonNull String filePath) {
        if(TextUtils.isEmpty(filePath)){
            return null ;
        }
        FileLoader fileLoader = new FileLoader(filePath);
        return new GifDrawable(context,fileLoader);
    }

    public static GifDrawable fromResource(@NonNull Context context, int resId) {
        if(resId == 0){
            return null ;
        }
        ResourceStreamLoader resourceStreamLoader = new ResourceStreamLoader(context, resId);
        return new GifDrawable(context,resourceStreamLoader);
    }
}
