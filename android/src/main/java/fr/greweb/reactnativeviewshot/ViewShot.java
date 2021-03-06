package fr.greweb.reactnativeviewshot;

import javax.annotation.Nullable;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.util.Base64;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.uimanager.NativeViewHierarchyManager;
import com.facebook.react.uimanager.UIBlock;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot utility class allow to screenshot a view.
 */
public class ViewShot implements UIBlock {

    static final String ERROR_UNABLE_TO_SNAPSHOT = "E_UNABLE_TO_SNAPSHOT";

    private final int tag;
    private final String extension;
    private final Bitmap.CompressFormat format;
    private final double quality;
    private final Integer width;
    private final Integer height;
    private final File output;
    private final String result;
    private final Promise promise;
    private final Boolean snapshotContentContainer;
    private final ReactApplicationContext reactContext;
    private final Activity currentActivity;

    public ViewShot(
            int tag,
            String extension,
            Bitmap.CompressFormat format,
            double quality,
            @Nullable Integer width,
            @Nullable Integer height,
            File output,
            String result,
            Boolean snapshotContentContainer,
            ReactApplicationContext reactContext,
            Activity currentActivity,
            Promise promise) {
        this.tag = tag;
        this.extension = extension;
        this.format = format;
        this.quality = quality;
        this.width = width;
        this.height = height;
        this.output = output;
        this.result = result;
        this.snapshotContentContainer = snapshotContentContainer;
        this.reactContext = reactContext;
        this.currentActivity = currentActivity;
        this.promise = promise;
    }

    @Override
    public void execute(NativeViewHierarchyManager nativeViewHierarchyManager) {
        final View view;

        if (tag == -1) {
            view = currentActivity.getWindow().getDecorView().findViewById(android.R.id.content);
        } else {
            view = nativeViewHierarchyManager.resolveView(tag);
        }

        if (view == null) {
            promise.reject(ERROR_UNABLE_TO_SNAPSHOT, "No view found with reactTag: "+tag);
            return;
        }
        try {
            if ("tmpfile".equals(result)) {
                captureView(view, new FileOutputStream(output));
                final String uri = Uri.fromFile(output).toString();
                promise.resolve(uri);
            } else if ("base64".equals(result)) {
                final ByteArrayOutputStream os = new ByteArrayOutputStream();
                captureView(view, os);
                final byte[] bytes = os.toByteArray();
                final String data = Base64.encodeToString(bytes, Base64.NO_WRAP);
                promise.resolve(data);
            } else if ("data-uri".equals(result)) {
                final ByteArrayOutputStream os = new ByteArrayOutputStream();
                captureView(view, os);
                final byte[] bytes = os.toByteArray();
                String data = Base64.encodeToString(bytes, Base64.NO_WRAP);
                // correct the extension if JPG
                String imageFormat = extension;
                if ("jpg".equals(extension)) {
                    imageFormat = "jpeg";
                }
                data = "data:image/"+imageFormat+";base64," + data;
                promise.resolve(data);
            }
        } catch (Exception e) {
            e.printStackTrace();
            promise.reject(ERROR_UNABLE_TO_SNAPSHOT, "Failed to capture view snapshot");
        }
    }

    private List<View> getAllChildren(View v) {

        if (!(v instanceof ViewGroup)) {
            ArrayList<View> viewArrayList = new ArrayList<View>();
            viewArrayList.add(v);
            return viewArrayList;
        }

        ArrayList<View> result = new ArrayList<View>();

        ViewGroup viewGroup = (ViewGroup) v;
        for (int i = 0; i < viewGroup.getChildCount(); i++) {

            View child = viewGroup.getChildAt(i);

            //Do not add any parents, just add child elements
            result.addAll(getAllChildren(child));
        }
        return result;
    }

    /**
     * Screenshot a view and return the captured bitmap.
     * @param view the view to capture
     * @return the screenshot or null if it failed.
     */
    private void captureView(View view, OutputStream os) throws IOException {
        try {
            captureViewImpl(view, os);
        } finally {
            os.close();
        }
    }

    private void captureViewImpl(View view, OutputStream os) {
        int w = view.getWidth();
        int h = view.getHeight();

        if (w <= 0 || h <= 0) {
            throw new RuntimeException("Impossible to snapshot the view: view is invalid");
        }

        //evaluate real height
        if (snapshotContentContainer) {
            h=0;
            ScrollView scrollView = (ScrollView)view;
            for (int i = 0; i < scrollView.getChildCount(); i++) {
                h += scrollView.getChildAt(i).getHeight();
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Bitmap childBitmapBuffer;
        Canvas c = new Canvas(bitmap);
        view.draw(c);

        //after view is drawn, go through children
        List<View> childrenList = getAllChildren(view);

        for (View child : childrenList) {
            if(child instanceof TextureView) {
                ((TextureView) child).setOpaque(false);
                childBitmapBuffer = ((TextureView) child).getBitmap(child.getWidth(), child.getHeight());
                if (childBitmapBuffer != null) {
                    int left = child.getLeft();
                    int top = child.getTop();
                    View parentElem = (View)child.getParent();
                    while (parentElem != null) {
                        if (parentElem == view) {
                            break;
                        }
                        left += parentElem.getLeft();
                        top += parentElem.getTop();
                        parentElem = (View)parentElem.getParent();
                    }
                    c.drawBitmap(childBitmapBuffer, left + child.getPaddingLeft(), top + child.getPaddingTop(), null);
                }
            }
        }

        if (width != null && height != null && (width != w || height != h)) {
            bitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
        }
        if (bitmap == null) {
            throw new RuntimeException("Impossible to snapshot the view");
        }
        bitmap.compress(format, (int)(100.0 * quality), os);
    }
}
