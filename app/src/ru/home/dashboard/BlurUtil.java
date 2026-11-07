package ru.home.dashboard;

import android.content.Context;
import android.graphics.Bitmap;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;

/** Размытие фона для вечернего окна (RenderScript, один раз при показе). */
public final class BlurUtil {

    private BlurUtil() {
    }

    /**
     * Возвращает уменьшенную размытую копию исходного изображения.
     * Масштаб 1/4 — размытие на слабом CPU быстрое, визуально достаточно.
     */
    public static Bitmap blur(Context context, Bitmap source, float radius) {
        if (context == null || source == null) return null;
        Bitmap small = null;
        Bitmap blurred = null;
        RenderScript rs = null;
        try {
            int sw = Math.max(1, source.getWidth() / 4);
            int sh = Math.max(1, source.getHeight() / 4);
            small = Bitmap.createScaledBitmap(source, sw, sh, true);
            blurred = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);

            rs = RenderScript.create(context);
            Allocation input = Allocation.createFromBitmap(rs, small);
            Allocation output = Allocation.createFromBitmap(rs, blurred);
            ScriptIntrinsicBlur blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            blur.setRadius(Math.max(1f, Math.min(25f, radius)));
            blur.setInput(input);
            blur.forEach(output);
            output.copyTo(blurred);
            return blurred;
        } catch (Throwable t) {
            return null;
        } finally {
            if (rs != null) {
                try { rs.destroy(); } catch (Throwable ignored) {}
            }
            if (small != null) small.recycle();
        }
    }
}
