package com.hotupdate;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.facebook.react.ReactApplication;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.JSBundleLoader;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.common.LifecycleState;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.shell.MainReactPackage;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by Eton on 2018/11/27.
 */
public class NativeVersion extends ReactContextBaseJavaModule {

    public static final String TYPE_UNZIP = "unzip";
    public static final String RENEW_EVENT = "renew_event";
    private static final String TAG = "NativeVersion";
    private static final String URL = "https://tz-moapi-uat.paradise-soft.com.tw:9443/apis/v3/update?os=android&clientid=com.tz.app";
    private static final String APP_VERSION = "app_version";
    private ReactApplicationContext mContext;
    private String filesDir;
    private String workingDir;
    private String zipFileName = "android.jsbundle.zip";
    private String apkName;
    private DownloadManager mDownloadManager;
    private String flag;

    /**
     * 建構子
     *
     * @param context
     */
    NativeVersion(ReactApplicationContext context) {
        super(context);
        this.mContext = context;
        apkName = mContext.getPackageName() + ".apk";
    }

    public static void sendEvent(ReactContext reactContext, String eventName, @Nullable WritableMap params) {
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);

    }

    @Override
    public String getName() {
        return TAG;
    }

    /**
     * 更新版本流程
     */
    @ReactMethod
    public void updateVersion() {
        filesDir = mContext.getFilesDir().getAbsolutePath();
        workingDir = mContext.getFilesDir().getAbsolutePath() + File.separator + "android.jsbundle" + File.separator;
        mDownloadManager = new DownloadManager(filesDir);
//        reloadVersion();
        String nowVersion = getLatestVersion();
        String appVersion = "";
        if (nowVersion.contains(".")) {
            try {
                appVersion = nowVersion.split("\\.")[0];
                // 將 appVersion 補上 .0 的動作，因為 API 只吃 x.0 , 尾數非'.0', 不更新, EX: 7.0
                appVersion = appVersion + ".0";
            } catch (IndexOutOfBoundsException e) {
                Log.e(TAG, "onCreate: What happened = ", e);
            }
        }
        // 下載:75% unzip:25% 將%數回傳給 RN 前端
        updateVersionRequest(appVersion, nowVersion, "0", new UpdateVersionCallback());
    }

    /**
     * 取得本地端最新的版本
     *
     * @return 最新版本
     */
    public String getLatestVersion() {
        String localVersion = getLocalVersionName(mContext);
        String filesBundleVersion = getFilesBundleVersion();
        if (filesBundleVersion.isEmpty()) {
            return localVersion;
        }
        String[] localVersionArray = localVersion.split("\\.");
        String[] filesBundleVersionArray = filesBundleVersion.split("\\.");
        try {
            for (int i = 0; i < 3; i++) {
                int lv = Integer.parseInt(localVersionArray[i]);
                int fv = Integer.parseInt(filesBundleVersionArray[i]);
                if (i == 0) {
                    if (lv == fv) {
                        continue;
                    } else {
                        return localVersion;
                    }
                } else {
                    if (lv > fv) {
                        return localVersion;
                    } else if (lv < fv) {
                        return filesBundleVersion;
                    } else {
                        if (i == 2) {
                            return filesBundleVersion;
                        }
                    }
                }
            }
            if (localVersion.compareTo(filesBundleVersion) >= 0) {
                return localVersion;
            } else {
                return filesBundleVersion;
            }
        } catch (Exception e) {
            Log.e(TAG, "getLatestVersion: 沒有成功解析最新版本號，使用本地端版本號：" + localVersion, e);
            return localVersion;
        }
    }

    /**
     * 取得 versionName
     */
    public String getLocalVersionName(Context context) {
        String localVersion = "";
        try {
            PackageInfo packageInfo = context.getApplicationContext()
                    .getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            localVersion = packageInfo.versionName;
            Log.d(TAG, "版本名稱: " + localVersion);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            Log.e(TAG, "getLocalVersionName: exception", e);
        }
        return localVersion;
    }

    /**
     * 取得 /data/data/com.tzdev.app/files/android.jsbundle/info.json 底下的 bundle version
     *
     * @return 遠端 temp version
     */
    public String getFilesBundleVersion() {
        File file = new File(workingDir, "info.json");
        if (!file.exists()) {
            Log.d(TAG, "getFilesBundleVersion: file not exists.");
            return "";
        }

        String data = readFromFile(file);
        Log.d(TAG, "getFilesBundleVersion: data = " + data);

        String version = "";
        try {
            JSONObject jsonObject = new JSONObject(data);
            version = jsonObject.getString(APP_VERSION);
        } catch (JSONException e) {
            e.printStackTrace();
            Log.e(TAG, "getFilesBundleVersion: error", e);
        }
        return version;
    }

    /**
     * 取得檔案內部資料
     *
     * @param file 欲讀取檔案
     * @return 檔案內資料
     */
    @NonNull
    private String readFromFile(File file) {
        //Read text from file
        StringBuilder text = new StringBuilder();

        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;

            while ((line = br.readLine()) != null) {
                text.append(line);
                text.append('\n');
            }
            br.close();
        } catch (IOException e) {
            Log.e(TAG, "readFromFile: ", e);
        }
        return text.toString();
    }

    /**
     * 更新版本請求
     *
     * @param appVersion 目前使用者 app 的版本 x.0 目前 api 限制後面固定".0"，不然會出錯
     * @param jsVersion  目前使用者 js 的版本 x.x.x
     * @param xMode      目前均帶入"0"，忘記是做什麼的
     * @param callback   callback
     */
    private void updateVersionRequest(String appVersion, String jsVersion, String xMode, @NonNull Callback callback) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(URL)
                .addHeader("Accept", "application/json")
                .addHeader("x-app-ver", appVersion)
                .addHeader("x-js-ver", jsVersion)
                .addHeader("x-mode", xMode)
                .build();
        client.newCall(request).enqueue(callback);
    }

    /**
     * 遞回方式刪除檔案，除了傳入的根目錄不會刪除
     *
     * @param dir 欲刪除的檔案目錄
     */
    private void recursivelyDelete(File dir) {
        if (dir.exists()) {
            File[] files = dir.listFiles();
            for (int i = 0; i < files.length; i++) {
                File file = files[i];
                if (file.isDirectory()) {
                    recursivelyDelete(file);
                }
                file.delete();
            }
        }
    }

    @ReactMethod
    public void reloadLocal() {
        ReactApplication reactApplication = (ReactApplication) getCurrentActivity().getApplication();
        ReactInstanceManager mReactInstanceManager = ReactInstanceManager
                .builder()
                .setCurrentActivity(getCurrentActivity())
                .setApplication((Application) reactApplication)
                .setBundleAssetName("index.android.white.jsbundle")
                .addPackage(new MainReactPackage())
                .addPackage(new NativeVersionPackage())
                .setInitialLifecycleState(LifecycleState.RESUMED)
                .setJSBundleFile(workingDir + "index.android.white.jsbundle")
                .build();

//        mReactInstanceManager.recreateReactContextInBackground();
    }

    @ReactMethod
    public void reloadVersion(String jsBundle) {
        try {
            // #1) Get the ReactInstanceManager instance, which is what includes the
            //     logic to reload the current React context.
            Log.d(TAG, "reloadVersion: reload start.");
            final ReactInstanceManager instanceManager = resolveInstanceManager();

            if (instanceManager == null) {
                return;
            }

//            String latestJSBundleFile;
//            /*if (config.getType("bundlePath") == String){
//                latestJSBundleFile = config.getString("bundlePath") + File.separator + bundleName;
//            } else {
//                latestJSBundleFile = workingDir + File.separator + bundleName;
//            }*/
//            if (jsBundle.isEmpty()) {
//                latestJSBundleFile = workingDir + "index.android.jsbundle.bundle";
//            } else {
//                latestJSBundleFile = jsBundle;
//            }
//            File f = new File(latestJSBundleFile);
//            if (f.exists()) {
//                Log.d(TAG, "reloadVersion: exists");
//            }
//            // #2) Update the locally stored JS bundle file path
//            setJSBundle(instanceManager, latestJSBundleFile);
            Log.d(TAG, "reloadVersion: set js bundle finish");
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    try {
                        // 重新讓 JS bundle reload
                        Log.d(TAG, "reloadVersion: handler handle");
                        instanceManager.recreateReactContextInBackground();
                    } catch (Exception e) {
                        // The recreation method threw an unknown exception
                        // so just simply fallback to restarting the Activity (if it exists)
                        reCreateActivity();
                        Log.d(TAG, "reloadVersion: re create activity.");
                    }
                }
            });
        } catch (Exception e) {
            // Our reflection logic failed somewhere
            // so fall back to restarting the Activity (if it exists)
            reCreateActivity();
            Log.d(TAG, "reloadVersion: re create activity");
        }
    }

    private ReactInstanceManager resolveInstanceManager() throws NoSuchFieldException, IllegalAccessException {
        final Activity currentActivity = getCurrentActivity();
        if (currentActivity == null) {
            return null;
        }

        ReactApplication reactApplication = (ReactApplication) currentActivity.getApplication();
        ReactInstanceManager instanceManager = reactApplication.getReactNativeHost().getReactInstanceManager();

        return instanceManager;
    }

    private void setJSBundle(ReactInstanceManager instanceManager, String latestJSBundleFile) throws IllegalAccessException {
        try {
            JSBundleLoader latestJSBundleLoader;
            if (latestJSBundleFile.toLowerCase().startsWith("assets://")) {
                latestJSBundleLoader = JSBundleLoader.createAssetLoader(getReactApplicationContext(), latestJSBundleFile, false);
            } else {
                latestJSBundleLoader = JSBundleLoader.createFileLoader(latestJSBundleFile);
            }

            Field bundleLoaderField = instanceManager.getClass().getDeclaredField("mBundleLoader");
            bundleLoaderField.setAccessible(true);
            bundleLoaderField.set(instanceManager, latestJSBundleLoader);
        } catch (Exception e) {
            throw new IllegalAccessException("Could not setJSBundle");
        }
    }

    private void reCreateActivity() {

        final Activity currentActivity = getCurrentActivity();
        if (currentActivity == null) {
            // The currentActivity can be null if it is backgrounded / destroyed, so we simply
            // no-op to prevent any null pointer exceptions.
            return;
        }

        currentActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                currentActivity.recreate();
            }
        });
    }

    /**
     * 更新版本的 callback
     */
    private class UpdateVersionCallback implements Callback {

        @Override
        public void onFailure(Call call, IOException e) {
            // TODO: Call API 失敗處理，通知 RN
            Log.e(TAG, "onFailure in onCreate", e);
            WritableMap writableMap = Arguments.createMap();
            writableMap.putString("renewType", "Failed");
            writableMap.putString("data", "API 呼叫失敗");
            sendEvent(mContext, RENEW_EVENT, writableMap);
        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {
            if (response.isSuccessful()) {
                String responseStr = response.body().string();
                Log.d(TAG, "onResponse: " + responseStr);
                try {
                    JSONObject jsonObject = new JSONObject(responseStr);
                    JSONObject resultObject = jsonObject.getJSONObject("result");
                    String code = resultObject.getString("code");
                    String message = resultObject.getString("message");
                    if (code.contentEquals("200-12-005") && message.contentEquals("请强制更新")) {
                        JSONObject data = jsonObject.getJSONObject("data");
                        flag = data.getString("update_flag");
                        if (flag.contentEquals("js")) {
                            String androidUrl = data.getString("android");
                            String androidMd5 = data.getString("android_md5");
                            String androidVersion = data.getString("android_version");
                            int androidBytes = data.getInt("android_bytes");
                            // 下載 js bundle
                            mDownloadManager.addTask(zipFileName, androidUrl, androidMd5, androidBytes, new DownloadCallback());
                        }
                    } else if (code.contentEquals("200-12-004") && message.contentEquals("请强制更新应用程序")) {
                        JSONObject data = jsonObject.getJSONObject("data");
                        flag = data.getString("update_flag");
                        if (flag.contentEquals("app")) {
                            String androidUrl = data.getString("android");
                            String androidMd5 = data.getString("android_md5");
                            String androidVersion = data.getString("android_version");
                            int androidBytes = data.getInt("android_bytes");
                            mDownloadManager.dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
                            // 下載 app 應用程式
                            mDownloadManager.addTask(apkName, androidUrl, androidMd5, androidBytes, new DownloadCallback());
                        }
                    } else {

                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    Log.e(TAG, "onResponse: 可能是 api 格式不一樣", e);
                    // TODO: Json 解析失敗，通知 RN 顯示錯誤畫面
                    WritableMap writableMap = Arguments.createMap();
                    writableMap.putString("renewType", "Failed");
                    writableMap.putString("data", "JSON 格式錯誤");
                    sendEvent(mContext, RENEW_EVENT, writableMap);
                }
            } else {
                Log.w(TAG, "onResponse: failure");
                // TODO: Response 不成功，通知 RN 顯示錯誤畫面
            }
        }
    }

    /**
     * 下載的 callback
     */
    private class DownloadCallback implements DownloadManager.Callback {

        @Override
        public void call(boolean downloaded, String fileName) {
            if (downloaded) {
                Log.d(TAG, "call: " + fileName + " download finished.");
                if (flag.contentEquals("js")) {
                    // 檢查是否有 "android.jsbundle" 資料夾，有的話刪除底下檔案
                    File workDir = new File(workingDir);
                    if (!workDir.mkdirs()) {
                        File[] files = workDir.listFiles();
                        if (files.length > 0) {
                            recursivelyDelete(workDir);
                        }
                    }
                    // unzip
                    String srcPath = filesDir + File.separator + zipFileName;
                    String targetPath = filesDir;
                    try {
                        mDownloadManager.unzip(srcPath, targetPath);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else if (flag.contentEquals("app")) {
                    // install
                    File apkFile = new File(mDownloadManager.dir + File.separator + apkName);
                    mDownloadManager.installAPK(mContext, apkFile);
                }

                // TODO: 更新 RN view

            } else {
                Log.d(TAG, "call: download failed.");
                // 一分鐘自動重新下載
                try {
                    Thread.sleep(60000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                updateVersion();
            }
        }

        @Override
        public void onProgress(float progress, String type) {
            // 更新 app 進度，回傳給 RN
            if (type.contentEquals(TYPE_UNZIP)) {
                int progressInt = (int) ((progress * 0.25 + 0.75f) * 100);
                WritableMap writableMap = Arguments.createMap();
                writableMap.putString("progress", String.valueOf(progressInt));
                sendEvent(mContext, "ProgressEvent", writableMap);
                if (progress == 1) {
                    Log.d(TAG, "onProgress: unzip 完成");
                    // TODO: RN 更新 View 提示已完成
                }

            } else if (type.contentEquals(zipFileName)) {
                int progressInt = ((int) (progress * 0.75 * 100));
                WritableMap writableMap = Arguments.createMap();
                writableMap.putString("progress", String.valueOf(progressInt));
                sendEvent(mContext, "ProgressEvent", writableMap);

            } else if (type.contentEquals(apkName)) {
                int progressInt = (int) (progress * 100);
                WritableMap writableMap = Arguments.createMap();
                writableMap.putDouble("progress", progressInt);
                sendEvent(mContext, "ProgressEvent", writableMap);
                if (progress == 1) {
                    Log.d(TAG, "onProgress: app 下載完成");
                    // TODO: RN 更新 View 提示已完成
                }
            }
        }
    }
}
