/*
 * Copyright (C) 2016 AriaLyy(https://github.com/AriaLyy/Aria)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.arialyy.aria.core.common;

import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.arialyy.aria.core.AriaManager;
import com.arialyy.aria.core.inf.AbsNormalEntity;
import com.arialyy.aria.core.inf.AbsTaskEntity;
import com.arialyy.aria.core.inf.IEventListener;
import com.arialyy.aria.core.upload.UploadEntity;
import com.arialyy.aria.util.CommonUtil;
import com.arialyy.aria.util.ErrorHelp;
import com.arialyy.aria.util.NetUtils;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by lyy on 2017/1/18.
 * 任务线程
 */
public abstract class AbsThreadTask<ENTITY extends AbsNormalEntity, TASK_ENTITY extends AbsTaskEntity<ENTITY>>
        implements Runnable {
    /**
     * 线程重试次数
     */
    private final int RETRY_NUM = 5;
    /**
     * 线程重试间隔
     */
    private final int RETRY_INTERVAL = 5000;
    private final String TAG = "AbsThreadTask";
    protected long mChildCurrentLocation = 0, mSleepTime = 0;
    protected int mBufSize;
    protected String mConfigFPath;
    protected IEventListener mListener;
    protected StateConstance STATE;
    protected SubThreadConfig<TASK_ENTITY> mConfig;
    protected ENTITY mEntity;
    protected TASK_ENTITY mTaskEntity;
    private int mFailNum = 0;
    private String mTaskType;
    private Timer mFailTimer;
    private long mLastSaveTime;

    protected AbsThreadTask(StateConstance constance, IEventListener listener,
                            SubThreadConfig<TASK_ENTITY> info) {
        AriaManager manager = AriaManager.getInstance();
        STATE = constance;
        STATE.CONNECT_TIME_OUT = manager.getDownloadConfig().getConnectTimeOut();
        STATE.READ_TIME_OUT = manager.getDownloadConfig().getIOTimeOut();
        mListener = listener;
        mConfig = info;
        mTaskEntity = mConfig.TASK_ENTITY;
        mEntity = mTaskEntity.getEntity();
        mConfigFPath = info.CONFIG_FILE_PATH;
        mBufSize = manager.getDownloadConfig().getBuffSize();
        mTaskType = getTaskType();
        mLastSaveTime = System.currentTimeMillis();
    }

    protected abstract String getTaskType();


    private boolean filterVersion() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    /**
     * 停止任务
     */
    public void stop() {
        synchronized (AriaManager.LOCK) {
            try {
                if (mConfig.SUPPORT_BP) {
                    final long currentTemp = mChildCurrentLocation;
                    STATE.STOP_NUM++;
                    Log.d(TAG, "任务【"
                            + mConfig.TEMP_FILE.getName()
                            + "】thread__"
                            + mConfig.THREAD_ID
                            + "__停止, stop location ==> "
                            + currentTemp);
                    writeConfig(false, currentTemp);
                    if (STATE.isStop()) {
                        Log.d(TAG, "任务【" + mConfig.TEMP_FILE.getName() + "】已停止");
                        STATE.isRunning = false;
                        mListener.onStop(STATE.CURRENT_LOCATION);
                    }
                } else {
                    Log.d(TAG, "任务【" + mConfig.TEMP_FILE.getName() + "】已停止");
                    STATE.isRunning = false;
                    mListener.onStop(STATE.CURRENT_LOCATION);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 执行中
     */
    protected void progress(long len) {
        synchronized (AriaManager.LOCK) {
            mChildCurrentLocation += len;
            STATE.CURRENT_LOCATION += len;
            if (System.currentTimeMillis() - mLastSaveTime > 5000) {
                mLastSaveTime = System.currentTimeMillis();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final long currentTemp = mChildCurrentLocation;
                        try {
                            writeConfig(false, currentTemp);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
            }
        }
    }

    /**
     * 取消任务
     */
    public void cancel() {
        synchronized (AriaManager.LOCK) {
            if (mConfig.SUPPORT_BP) {
                STATE.CANCEL_NUM++;
                Log.d(TAG, "任务【" + mConfig.TEMP_FILE.getName() + "】thread__" + mConfig.THREAD_ID + "__取消");
                if (STATE.isCancel()) {
                    File configFile = new File(mConfigFPath);
                    if (configFile.exists()) {
                        configFile.delete();
                    }
                    if (mConfig.TEMP_FILE.exists() && !(mEntity instanceof UploadEntity)) {
                        mConfig.TEMP_FILE.delete();
                    }
                    Log.d(TAG, "任务【" + mConfig.TEMP_FILE.getName() + "】已取消");
                    STATE.isRunning = false;
                    mListener.onCancel();
                }
            } else {
                Log.d(TAG, "任务【" + mConfig.TEMP_FILE.getName() + "】已取消");
                STATE.isRunning = false;
                mListener.onCancel();
            }
        }
    }

    /**
     * 任务失败
     */
    protected void fail(final long currentLocation, String msg, Exception ex) {
        synchronized (AriaManager.LOCK) {
            try {
                if (ex != null) {
                    Log.e(TAG, msg + "\n" + CommonUtil.getPrintException(ex));
                } else {
                    Log.e(TAG, msg);
                }
                if (mConfig.SUPPORT_BP) {
                    writeConfig(false, currentLocation);
                    retryThis(STATE.THREAD_NUM != 1);
                } else {
                    Log.e(TAG, "任务【" + mConfig.TEMP_FILE.getName() + "】执行失败");
                    mListener.onFail(true);
                    ErrorHelp.saveError(mTaskType, mEntity, "", CommonUtil.getPrintException(ex));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 重试当前线程，如果其中一条线程已经下载失败，则任务该任务下载失败，并且停止该任务的所有线程
     *
     * @param needRetry 是否可以重试
     */
    private void retryThis(boolean needRetry) {
        if (!NetUtils.isConnected(AriaManager.APP)) {
            Log.w(TAG,
                    "任务【" + mConfig.TEMP_FILE.getName() + "】thread__" + mConfig.THREAD_ID + "__重试失败，网络未连接");
        }
        if (mFailNum < RETRY_NUM && needRetry && NetUtils.isConnected(AriaManager.APP)) {
            if (mFailTimer != null) {
                mFailTimer.purge();
                mFailTimer.cancel();
            }
            mFailTimer = new Timer(true);
            mFailTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    mFailNum++;
                    Log.w(TAG,
                            "任务【" + mConfig.TEMP_FILE.getName() + "】thread__" + mConfig.THREAD_ID + "__正在重试");
                    final long retryLocation = mChildCurrentLocation;
                    mConfig.START_LOCATION = retryLocation;
                    AbsThreadTask.this.run();
                }
            }, RETRY_INTERVAL);
        } else {
            STATE.FAIL_NUM++;
            if (STATE.isFail()) {
                STATE.isRunning = false;
                STATE.isStop = true;
                Log.e(TAG, "任务【" + mConfig.TEMP_FILE.getName() + "】执行失败");
                mListener.onFail(true);
            }
        }
    }

    /**
     * 将记录写入到配置文件
     */
    protected void writeConfig(boolean isComplete, final long record) throws IOException {
        synchronized (AriaManager.LOCK) {
            String key = null, value = null;
            if (0 < record && record < mConfig.END_LOCATION) {
                key = mConfig.TEMP_FILE.getName() + "_record_" + mConfig.THREAD_ID;
                value = String.valueOf(record);
            } else if (record >= mConfig.END_LOCATION || isComplete) {
                key = mConfig.TEMP_FILE.getName() + "_state_" + mConfig.THREAD_ID;
                value = "1";
            }
            if (!TextUtils.isEmpty(key) && !TextUtils.isEmpty(value)) {
                File configFile = new File(mConfigFPath);
                Properties pro = CommonUtil.loadConfig(configFile);
                pro.setProperty(key, value);
                CommonUtil.saveConfig(configFile, pro);
            }
        }
    }
}
