package com.example.myapplication;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Log;

import com.blankj.utilcode.util.ThreadUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * 系统截屏监听工具，监听系统截屏，然后对截图进行处理
 * 需要权限：Manifest.permission.READ_EXTERNAL_STORAGE
 * 使用方法：
 * 在onCreate中注册
 *
 *         if (PermissionUtils.isGranted(Manifest.permission.READ_EXTERNAL_STORAGE)) {
 *             ScreenShotUtils.getInstance().register(this) {
 *                 //系统截屏
 *                 val bottomShareToView = BottomShareToView(this, 2)
 *                 bottomShareToView.sessionBean = model.sessionLiveData.value
 *                 bottomShareToView.sharePath = it
 *                 bottomShareToView.shareContent = 2
 *                 DialogUtils.showBottomView(this, bottomShareToView)
 *             }
 *         }
 *
 * 在onDestroy中注销
 * 
 *        ScreenShotUtils.getInstance().unregister()
 *
 */
public class ScreenShotUtils {
    private static final String TAG = "ScreenShot";
    
    private WeakReference<Context> mContext;
    
    private final List<String> mSnapshotList = new ArrayList<>();

    /**
     * 截屏依据中的路径判断关键字
     */
    private static final String[] KEYWORDS = {
            "screenshot", "screen_shot", "screen-shot", "screen shot", "screencapture",
            "screen_capture", "screen-capture", "screen capture", "screencap", "screen_cap",
            "screen-cap", "screen cap"
    };

    private ContentResolver mContentResolver;
    private CallbackListener mCallbackListener;
    private MediaContentObserver mInternalObserver;
    private MediaContentObserver mExternalObserver;
    private static ScreenShotUtils mInstance;

    private ScreenShotUtils() {
    }

    /**
     * 获取 ScreenShot 对象
     *
     * @return ScreenShot对象
     */
    public static ScreenShotUtils getInstance() {
        if (mInstance == null) {
            synchronized (ScreenShotUtils.class) {
                mInstance = new ScreenShotUtils();
            }
        }
        
        return mInstance;
    }

    /**
     * 注册
     *
     * @param context          上下文
     * @param callbackListener 回调监听
     */
    public void register(Context context, CallbackListener callbackListener) {
        mContext = new WeakReference<>(context);
        mContentResolver = mContext.get().getContentResolver();
        mCallbackListener = callbackListener;

        HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());

        mInternalObserver = new MediaContentObserver(MediaStore.Images.Media.INTERNAL_CONTENT_URI, handler);
        mExternalObserver = new MediaContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, handler);

        mContentResolver.registerContentObserver(MediaStore.Images.Media.INTERNAL_CONTENT_URI,
                true, mInternalObserver);
        mContentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true, mExternalObserver);
    }

    /**
     * 注销
     */
    public void unregister() {
        mContext = null;
        mSnapshotList.clear();

        if (mContentResolver != null) {
            mContentResolver.unregisterContentObserver(mInternalObserver);
            mContentResolver.unregisterContentObserver(mExternalObserver);
            mContentResolver = null;
        }
        
        mCallbackListener = null;
        mInstance = null;
    }

    /**
     * 获取最近的一张图片
     * @param context  context
     * @param uri      uri
     * @return         path
     */
    private String getRecentlyPhotoPath(Context context, Uri uri) {
        try {
            // 这个地方利用like和通配符 来寻找 系统存储照片的地方
            // 实际上还可以做的更夸张一点，寻找所有目录下的照片 并且可以限定格式  只要修改这个通配符语句即可
            String selection = MediaStore.Images.ImageColumns.DATA + " LIKE '%" + "/Pictures/" + "%'" + " OR " + MediaStore.Images.ImageColumns.DATA + " LIKE '%" + "/DCIM/" + "%'";
            String[] projection = new String[] { MediaStore.Images.ImageColumns.DATA, MediaStore.Images.ImageColumns.SIZE };
            String sortOrder = MediaStore.Images.ImageColumns.DATE_ADDED + " DESC";
            // content://media/external/images/media
//            Uri uri = MediaStore.Files.getContentUri("external");
//            Uri uri = MediaStore.Images.Media.getContentUri("external");
            // 这里做一个排序，因为我们实际上只需要最新拍得那张即可 你甚至可以取表里的 时间那个字段 然后判断一下 距离现在是否超过2分钟 超过2分钟就可以不显示缩略图的 微信就是2分钟之内刚拍的图片
            // 会显示 超过了就不显示，这里主要就是看对表结构的理解
            Cursor cursor = context.getContentResolver().query(uri, projection, selection, null, sortOrder);
            String filePath = "";

            if (cursor != null && cursor.moveToFirst()) {
                filePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.DATA));
            }

            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }

            return filePath;
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return "";
    }
    
    private boolean checkScreenShot(String path) {
        if (path == null) {
            return false;
        }
        
        path = path.toLowerCase();
        
        for (String keyword : KEYWORDS) {
            if (path.contains(keyword)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * 媒体内容观察者
     */
    private class MediaContentObserver extends ContentObserver {
        private Uri mUri;

        MediaContentObserver(Uri uri, Handler handler) {
            super(handler);
            mUri = uri;
            Log.d(TAG, uri.toString());
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            Log.d(TAG, "图片数据库发生变化：" + selfChange);
            
            if (mContext != null && mContext.get() != null) {
                String path = getRecentlyPhotoPath(mContext.get(), mUri);
                // 防止执行多次
                if (!mSnapshotList.contains(path) && checkScreenShot(path)) {
                    if (mCallbackListener != null) {
                        ThreadUtils.runOnUiThread(() -> mCallbackListener.onShot(path));
                    }
                }

                mSnapshotList.add(path);
            }
        }
    }

    /**
     * 回调监听器
     */
    public interface CallbackListener {
        /**
         * 截屏
         *
         * @param path 图片路径
         */
        void onShot(String path);
    }
}