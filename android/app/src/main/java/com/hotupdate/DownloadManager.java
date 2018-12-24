package com.hotupdate;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Created by Eton on 2018/11/28.
 */
public class DownloadManager {
    private static final String TAG = "DownloadManager";
    private static final int BUFFER_SIZE = 1024;
    String dir;
    private HashMap<String, DownloadTask> hashMap = new HashMap<>();
    private ExecutorService downloaderPool;
    private Callback mCallback;
    private File zipFile;
    private FileChannel channel;

    public DownloadManager(String dir) {
        downloaderPool = Executors.newFixedThreadPool(1);
        this.dir = dir;
    }

    /**
     * 加压压缩包.
     *
     * @param srcPath    原路径
     * @param targetPath 目标路径
     * @throws IOException 读写异常
     */
    public void unzip(@NonNull String srcPath, @NonNull String targetPath) throws IOException {
        if (!targetPath.endsWith(File.separator)) {
            targetPath += File.separator;
        }
        zipFile = new File(srcPath);
        FileInputStream fis = new FileInputStream(srcPath);
        channel = fis.getChannel();
        BufferedInputStream bis = new BufferedInputStream(fis, BUFFER_SIZE);
        ZipInputStream zis = null;
        try {
            zis = new ZipInputStream(bis);
            unzip(targetPath, zis);
        } finally {
            if (zis != null) {
                zis.close();
            }
        }

        new File(srcPath).delete();
    }

    private void unzip(@NonNull String targetPath, @NonNull ZipInputStream zis) throws IOException {
        ZipEntry zipEntry;
        float temp = 0;
        while ((zipEntry = zis.getNextEntry()) != null) {
            String path = targetPath + zipEntry.getName();
            File file = new File(path);

            if (zipEntry.isDirectory()) {
                if (!file.isDirectory()) {
                    file.mkdirs();
                }
            } else {
                File parentDir = file.getParentFile();
                if (parentDir != null) {
                    if (!parentDir.isDirectory()) {
                        parentDir.mkdirs();
                    }
                }
                byte[] buffer = new byte[BUFFER_SIZE];
                FileOutputStream fos = new FileOutputStream(file, false);
                BufferedOutputStream bos = new BufferedOutputStream(fos, BUFFER_SIZE);
                try {
                    long zipLength = zipFile.length();
                    int length;
                    while ((length = zis.read(buffer, 0, BUFFER_SIZE)) != -1) {
                        bos.write(buffer, 0, length);

                        // 計算進度
                        float progress = (int) (channel.position() / (float) zipLength * 100) / 100.0f;
                        Log.d(TAG, "unzip: progress = " + progress);
                        if (progress - temp >= 0.01) {
                            temp = progress;
                            mCallback.onProgress(progress, NativeVersion.TYPE_UNZIP);
                            Log.d(TAG, "unzip: progress = " + progress);
                        }
                    }
                    zis.closeEntry();
                } finally {
                    bos.flush();
                    bos.close();
                }
            }
        }
        Log.d(TAG, "unzip: finish unzip");
        mCallback.onProgress(1, NativeVersion.TYPE_UNZIP);
    }

    public void addTask(String fileName, String url, String md5, Callback callback) {
        addTask(fileName, url, md5, 0, callback);
    }

    public void addTask(String fileName, String url, String md5, int bytes, Callback callback) {
        synchronized (DownloadManager.class) {
            if (hashMap.containsKey(fileName)) {
                return;
            }
            this.mCallback = callback;
            DownloadTask task = new DownloadTask(fileName, url, md5, bytes);
            hashMap.put(fileName, task);

            downloaderPool.submit(task);
        }
    }

    public void quit() {
        downloaderPool.shutdownNow();
    }

    public void installAPK(Context context, File path) {
        Log.d(TAG, "installAPK: 開始安裝apk");
        String cmd = "chmod 777 " + path;
        try {
            Runtime.getRuntime().exec(cmd);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setDataAndType(Uri.fromFile(path), "application/vnd.android.package-archive");
        context.startActivity(intent);
    }

    public interface Callback {
        void call(boolean downloaded, String fileName);

        void onProgress(float progress, String type);
    }

    class DownloadTask implements Callable<Boolean> {

        String fileName = "";
        String url = "";
        String md5 = "";
        int bytes;

        DownloadTask(String fileName, String url, String md5, int bytes) {
            this.fileName = fileName;
            this.url = url;
            this.md5 = md5;
            this.bytes = bytes;
        }

        @Override
        public Boolean call() throws Exception {
            boolean ret = false;
            try {
                ret = download();
            } catch (Exception e) {
                Log.e(TAG, "call: ", e);
            }

            mCallback.call(ret, fileName);

            synchronized (DownloadManager.class) {
                hashMap.remove(fileName);
            }

            return ret;
        }


        /**
         * 開始下載文件
         *
         * @throws IOException
         */
        public boolean download() throws IOException {

            File fileSaveDir = new File(dir);
            if (!fileSaveDir.exists()) {
                if (!fileSaveDir.mkdirs()) {
                    throw new IOException("DownloadTask create dir error!");
                }
            }

            String cacheName = fileName + ".cache";
            File saveFile = new File(fileSaveDir, cacheName);

            if (md5 == null || md5.length() == 0) {
                // md5 不存在 直接下載
                if (saveFile.exists()) {
                    if (!saveFile.delete()) {
                        throw new IOException("delete apk failed");
                    }
                }

                direct(url, saveFile);
            } else {
                if (saveFile.exists()) {
                    if (!equalsMD5(saveFile, md5)) {
                        continue_down(url, saveFile);
                    }
                } else {
                    direct(url, saveFile);
                }

                if (!equalsMD5(saveFile, md5)) {
                    saveFile.delete();
                    throw new IOException("md5 not equals " + md5 + " " + fileName);
                } else {
                    Log.d(TAG, "download: not need download");
                }
            }

            File realFile = new File(fileSaveDir, fileName);
            if (realFile.exists()) {
                realFile.delete();
            }

            return saveFile.renameTo(realFile);
        }

        boolean equalsMD5(File saveFile, String md5) {
            String fileMD5 = MD5Util.getFileMD5String(saveFile);
            return md5.equalsIgnoreCase(fileMD5);
        }

        void direct(String downUrl, File saveFile) throws IOException {
            int process = 0;
            RandomAccessFile randOut = null;
            try {

                randOut = new RandomAccessFile(saveFile, "rwd");

                Random r = new Random();
                int ver = 1000 + r.nextInt(9000);
                downUrl = downUrl + "?v=" + ver;

                URL url = new URL(downUrl);
                HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
                httpURLConnection.setConnectTimeout(5000);
                httpURLConnection.setReadTimeout(5000);
                httpURLConnection.setRequestMethod("GET");
                httpURLConnection.connect();
                if (httpURLConnection.getResponseCode() == 200) {
                    InputStream is = httpURLConnection.getInputStream();
                    byte[] buffer = new byte[1024];
                    int len;
                    float temp = 0;
                    while ((len = is.read(buffer)) != -1) {
                        process += len;
                        randOut.write(buffer, 0, len);
                        if (bytes != 0) {
                            float percent = (int) (process / (float) bytes * 100) / 100.0f;
                            if (percent - temp >= 0.01) {
                                temp = percent;
                                if (mCallback != null) {
                                    mCallback.onProgress(percent, fileName);
                                }
                            }
                        }
                    }

                    randOut.close();
                    randOut = null;
                    is.close();
                }
            } catch (IOException e) {
                throw e;
            } finally {
                if (randOut != null) {
                    randOut.close();
                }
            }
        }

        void continue_down(String downUrl, File saveFile) throws IOException {
            long downLength = saveFile.length();
            RandomAccessFile threadFile = null;
            try {
                Random r = new Random();
                int ver = 1000 + r.nextInt(9000);
                downUrl = downUrl + "?v=" + ver;

                URL url = new URL(downUrl);
                HttpURLConnection http = (HttpURLConnection) url.openConnection();
                http.setConnectTimeout(5000);
                http.setReadTimeout(5000);
                http.setRequestMethod("GET");
                http.setRequestProperty("Range", "bytes=" + downLength + "-");// 設置獲取實體數據的範圍
                http.setRequestProperty("Connection", "Keep-Alive");
                http.connect();
                if (http.getResponseCode() == 206) {
                    InputStream inStream = http.getInputStream();
                    byte[] buffer = new byte[1024];
                    int offset = 0;
                    threadFile = new RandomAccessFile(saveFile, "rwd");
                    threadFile.seek(downLength);
                    while ((offset = inStream.read(buffer)) != -1) {
                        threadFile.write(buffer, 0, offset);
                    }
                    threadFile.close();
                    threadFile = null;
                    inStream.close();
                }
            } catch (IOException e) {
                throw e;
            } finally {
                if (threadFile != null) {
                    threadFile.close();
                }
            }
        }
    }
}
