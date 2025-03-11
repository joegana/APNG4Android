package com.github.penfeizhou.animation.demo;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;

import com.github.penfeizhou.animation.apng.APNGDrawable;
import com.github.penfeizhou.animation.gif.GifDrawable;
import com.github.penfeizhou.animation.webp.WebPDrawable;

import java.util.Locale;

import androidx.vectordrawable.graphics.drawable.Animatable2Compat;
import androidx.vectordrawable.graphics.drawable.Animatable2Compat.AnimationCallback;


/**
 * @Description: 作用描述
 * @Author: pengfei.zhou
 * @CreateDate: 2019/3/29
 */
public class AnimationTestActivity extends Activity {
    private static String TAG = "AnimationTestActivity";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apnglib);
        LinearLayout linearLayout = findViewById(R.id.layout);
        String[] files = getIntent().getStringArrayExtra("files");
        for (String assetFile : files) {
            ImageView imageView = new ImageView(this);
            imageView.setScaleType(ScaleType.MATRIX);
            Drawable drawable = null;
            if (assetFile.endsWith("png")) {
                drawable = APNGDrawable.fromAsset(this, assetFile);
            }
            if (assetFile.endsWith("webp")) {
                WebPDrawable  d = WebPDrawable.fromAsset(this, assetFile);
                d.setLoopLimit(1);
                drawable = d ;
            }
            if (assetFile.endsWith("gif")) {
                drawable = GifDrawable.fromAsset(this, assetFile);
            }
            imageView.setImageDrawable(drawable);

            Log.d(TAG,String.format(Locale.CHINESE,
                "w=%d,h=%d",drawable.getIntrinsicWidth(),drawable.getIntrinsicHeight()));

            LinearLayout.LayoutParams layoutParams = new
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
            layoutParams.bottomMargin = 50;
            layoutParams.topMargin = 50;
            linearLayout.addView(imageView, layoutParams);
        }
    }
}
