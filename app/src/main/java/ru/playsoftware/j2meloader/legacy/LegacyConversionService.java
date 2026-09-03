/*
 * Copyright 2026 J2ME-Loader contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package ru.playsoftware.j2meloader.legacy;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Performs JAR conversion in a private process and reports it over Messenger. */
public final class LegacyConversionService extends Service {
    public static final int MSG_START = 1;
    public static final int MSG_PROGRESS = 2;
    public static final int MSG_LOG = 3;
    public static final int MSG_RESULT = 4;
    public static final String KEY_SOURCE_PATH = "sourcePath";
    public static final String KEY_STAGE = "stage";
    public static final String KEY_CLASS_NAME = "className";
    public static final String KEY_COMPLETED = "completed";
    public static final String KEY_TOTAL = "total";
    public static final String KEY_PERCENT = "percent";
    public static final String KEY_LEVEL = "level";
    public static final String KEY_TEXT = "text";
    public static final String KEY_STATUS = "status";
    public static final String KEY_NAME = "name";
    public static final String KEY_DIRECTORY = "directory";
    public static final String KEY_MESSAGE = "message";

    private static final String TAG = "LegacyConversionService";

    private ExecutorService executor;
    private Messenger messenger;
    private volatile boolean running;

    @Override
    public void onCreate() {
        super.onCreate();
        executor = Executors.newSingleThreadExecutor();
        messenger = new Messenger(new Handler() {
            @Override
            public void handleMessage(Message message) {
                if (message.what == MSG_START) {
                    startConversion(message);
                } else {
                    super.handleMessage(message);
                }
            }
        });
    }

    @Override
    public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    private void startConversion(final Message request) {
        if (running) {
            sendLog(request.replyTo, "ERROR", "A conversion is already running");
            return;
        }
        final String path = request.getData() == null ? null
                : request.getData().getString(KEY_SOURCE_PATH);
        final Messenger replyTo = request.replyTo;
        running = true;
        executor.submit(new Runnable() {
            @Override
            public void run() {
                InstallResult result;
                try {
                    if (path == null || path.length() == 0) {
                        result = InstallResult.rejected("Source path is missing");
                    } else {
                        File source = new File(path).getCanonicalFile();
                        File storageRoot = android.os.Environment.getExternalStorageDirectory()
                                .getCanonicalFile();
                        if (!isWithin(source, storageRoot)) {
                            result = InstallResult.rejected("Only files on the SD-card are supported");
                        } else {
                            File emulatorDirectory = new File(storageRoot, "J2ME-Loader");
                            result = new LegacyInstaller(emulatorDirectory).install(source,
                                    new InstallProgressListener() {
                                        @Override
                                        public void onStage(String stage) {
                                            sendProgress(replyTo, stage, "", 0, 0);
                                        }

                                        @Override
                                        public void onProgress(int completed, int total,
                                                String className) {
                                            sendProgress(replyTo, "converting", className,
                                                    completed, total);
                                        }

                                        @Override
                                        public void onLog(String level, String text) {
                                            sendLog(replyTo, level, text);
                                        }
                                    });
                        }
                    }
                } catch (Throwable error) {
                    Log.e(TAG, "Conversion worker crashed", error);
                    result = InstallResult.failed(error.getClass().getName() + ": "
                            + (error.getMessage() == null ? "conversion worker failed"
                            : error.getMessage()));
                } finally {
                    running = false;
                }
                sendResult(replyTo, result);
                stopSelf();
            }
        });
    }

    private static boolean isWithin(File path, File root) {
        String rootPath = root.getPath();
        String pathValue = path.getPath();
        return pathValue.equals(rootPath) || pathValue.startsWith(rootPath + File.separator);
    }

    private static void sendProgress(Messenger target, String stage, String className,
            int completed, int total) {
        if (target == null) return;
        Message message = Message.obtain();
        message.what = MSG_PROGRESS;
        Bundle data = new Bundle();
        data.putString(KEY_STAGE, stage);
        data.putString(KEY_CLASS_NAME, className);
        data.putInt(KEY_COMPLETED, completed);
        data.putInt(KEY_TOTAL, total);
        data.putInt(KEY_PERCENT, total > 0 ? Math.min(100, completed * 100 / total)
                : ("publishing".equals(stage) ? 100 : 0));
        message.setData(data);
        try {
            target.send(message);
        } catch (RemoteException ignored) {
        }
    }

    private static void sendLog(Messenger target, String level, String text) {
        if (target == null) return;
        Message message = Message.obtain();
        message.what = MSG_LOG;
        Bundle data = new Bundle();
        data.putString(KEY_LEVEL, level);
        data.putString(KEY_TEXT, text == null ? "" : text);
        message.setData(data);
        try {
            target.send(message);
        } catch (RemoteException ignored) {
        }
    }

    private static void sendResult(Messenger target, InstallResult result) {
        if (target == null) return;
        Message message = Message.obtain();
        message.what = MSG_RESULT;
        Bundle data = new Bundle();
        data.putString(KEY_STATUS, result.getStatus().name());
        data.putString(KEY_NAME, result.getName());
        data.putString(KEY_DIRECTORY, result.getAppDirectory() == null ? null
                : result.getAppDirectory().getAbsolutePath());
        data.putString(KEY_MESSAGE, result.getMessage());
        message.setData(data);
        try {
            target.send(message);
        } catch (RemoteException ignored) {
        }
    }

    @Override
    public void onDestroy() {
        if (executor != null) executor.shutdownNow();
        super.onDestroy();
    }
}
