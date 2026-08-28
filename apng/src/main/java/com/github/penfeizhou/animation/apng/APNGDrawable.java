package com.github.penfeizhou.animation.apng;


import android.content.Context;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import com.github.penfeizhou.animation.FrameAnimationDrawable;
import com.github.penfeizhou.animation.apng.decode.APNGDecoder;
import com.github.penfeizhou.animation.decode.FrameSeqDecoder;
import com.github.penfeizhou.animation.loader.AssetStreamLoader;
import com.github.penfeizhou.animation.loader.FileLoader;
import com.github.penfeizhou.animation.loader.Loader;
import com.github.penfeizhou.animation.loader.ResourceStreamLoader;

/**
 * @Description: APNGDrawable
 * @Author: pengfei.zhou
 * @CreateDate: 2019/3/27
 */
public class APNGDrawable extends FrameAnimationDrawable<APNGDecoder> {
    public APNGDrawable(Context context,Loader provider) {
        super(context,provider);
    }

    public APNGDrawable(Context context,APNGDecoder decoder) {
        super(context,decoder);
    }

    @Override
    protected APNGDecoder createFrameSeqDecoder(Loader streamLoader, FrameSeqDecoder.RenderListener listener) {
        return new APNGDecoder(streamLoader, listener);
    }


    public static APNGDrawable fromAsset(@NonNull Context context,@NonNull String assetPath) {
        if(TextUtils.isEmpty(assetPath)){
            return null;
        }
        AssetStreamLoader assetStreamLoader = new AssetStreamLoader(context, assetPath);
        return new APNGDrawable(context,assetStreamLoader);
    }

    public static APNGDrawable fromFile(@NonNull Context context,@NonNull String filePath) {
        if(TextUtils.isEmpty(filePath)){
            return null;
        }
        FileLoader fileLoader = new FileLoader(filePath);
        return new APNGDrawable(context,fileLoader);
    }

    public static APNGDrawable fromResource(@NonNull Context context, int resId) {
        if(resId == 0) {
            return null;
        }
        ResourceStreamLoader resourceStreamLoader = new ResourceStreamLoader(context, resId);
        return new APNGDrawable(context,resourceStreamLoader);
    }

}
