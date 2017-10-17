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
package com.arialyy.aria.core.download.downloader;

import android.util.Log;

import com.arialyy.aria.core.AriaManager;
import com.arialyy.aria.core.common.IUtil;
import com.arialyy.aria.core.download.DownloadEntity;
import com.arialyy.aria.core.download.DownloadGroupTaskEntity;
import com.arialyy.aria.core.download.DownloadTaskEntity;
import com.arialyy.aria.core.inf.IDownloadListener;
import com.arialyy.aria.core.inf.IEntity;
import com.arialyy.aria.orm.DbEntity;
import com.arialyy.aria.util.CommonUtil;
import com.arialyy.aria.util.NetUtils;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by AriaL on 2017/6/30.
 * 任务组核心逻辑
 */
public abstract class AbsGroupUtil implements IUtil {
    private final String TAG = "AbsGroupUtil";
    protected IDownloadGroupListener mListener;
    protected DownloadGroupTaskEntity mTaskEntity;
    /**
     * 任务组所有任务总长度
     */
    long mTotalLen = 0;
    long mCurrentLocation = 0;
    /**
     * 保存所有没有下载完成的任务，key为下载地址
     */
    Map<String, DownloadTaskEntity> mExeMap = new HashMap<>();
    /**
     * 下载失败的映射表，key为下载地址
     */
    Map<String, DownloadTaskEntity> mFailMap = new HashMap<>();
    /**
     * 是否需要读取文件长度，{@code true}需要
     */
    boolean isNeedLoadFileSize = true;
    //实际的下载任务数
    int mActualTaskNum = 0;
    //初始化完成的任务数
    int mInitNum = 0;
    // 初始化失败的任务数
    int mInitFailNum = 0;
    //任务组大小
    int mGroupSize = 0;
    private ExecutorService mExePool;
    private boolean isRunning = false;
    private Timer mTimer;
    /**
     * 下载器映射表，key为下载地址
     */
    private Map<String, Downloader> mDownloaderMap = new HashMap<>();
    /**
     * 该任务组对应的所有任务
     */
    private Map<String, DownloadTaskEntity> mTasksMap = new HashMap<>();
    //已经完成的任务数
    private int mCompleteNum = 0;
    //失败的任务数
    private int mFailNum = 0;
    //停止的任务数
    private int mStopNum = 0;

    AbsGroupUtil(IDownloadGroupListener listener, DownloadGroupTaskEntity taskEntity) {
        mListener = listener;
        mTaskEntity = taskEntity;
        mExePool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        List<DownloadTaskEntity> tasks =
                DbEntity.findDatas(DownloadTaskEntity.class, "groupName=?", mTaskEntity.key);
        if (tasks != null && !tasks.isEmpty()) {
            for (DownloadTaskEntity te : tasks) {
                te.removeFile = mTaskEntity.removeFile;
                if (te.getEntity() == null) continue;
                mTasksMap.put(te.getEntity().getUrl(), te);
            }
        }
        mGroupSize = mTaskEntity.entity.getSubTask().size();
        mTotalLen = taskEntity.getEntity().getFileSize();
        isNeedLoadFileSize = mTotalLen <= 1;
        for (DownloadEntity entity : mTaskEntity.entity.getSubTask()) {
            File file = new File(entity.getDownloadPath());
            if (entity.getState() == IEntity.STATE_COMPLETE && file.exists()) {
                mCompleteNum++;
                mCurrentLocation += entity.getFileSize();
            } else {
                mExeMap.put(entity.getUrl(), createChildDownloadTask(entity));
                mCurrentLocation += file.exists() ? entity.getCurrentProgress() : 0;
                mActualTaskNum++;
            }
            if (isNeedLoadFileSize) {
                mTotalLen += entity.getFileSize();
            }
        }
        updateFileSize();
    }

    void updateFileSize() {
        if (isNeedLoadFileSize) {
            mTaskEntity.getEntity().setFileSize(mTotalLen);
            mTaskEntity.getEntity().update();
        }
    }

    /**
     * 启动子任务下载
     *
     * @param url 子任务下载地址
     */
    public void startSubTask(String url) {
        if (!checkSubTask(url, "开始")) return;
        if (!isRunning) {
            isRunning = true;
            startTimer();
        }
        Downloader d = getDownloader(url, false);
        if (d != null && !d.isRunning()) {
            d.setNewTask(false);
            d.start();
        }
    }

    /**
     * 停止子任务下载
     *
     * @param url 子任务下载地址
     */
    public void stopSubTask(String url) {
        if (!checkSubTask(url, "停止")) return;
        Downloader d = getDownloader(url, false);
        if (d != null && d.isRunning()) {
            d.stop();
        }
    }

    /**
     * 删除子任务
     *
     * @param url 子任务下载地址
     */
    public void cancelSunTask(String url) {
        Downloader d = getDownloader(url, false);
        if (d != null) {
            d.cancel();
        }
    }

    /**
     * 检查子任务
     *
     * @param url  子任务url
     * @param type 任务类型
     * @return {@code true} 任务可以下载
     */
    private boolean checkSubTask(String url, String type) {
        DownloadTaskEntity entity = mTasksMap.get(url);
        if (entity != null) {
            if (entity.getState() == IEntity.STATE_COMPLETE) {
                Log.w(TAG, "任务【" + url + "】已完成，" + type + "失败");
                return false;
            }
        } else {
            Log.w(TAG, "任务组中没有该任务【" + url + "】，" + type + "失败");
            return false;
        }
        return true;
    }

    /**
     * 通过地址获取下载器
     *
     * @param url   子任务下载地址
     * @param start 是否启动任务
     */
    private Downloader getDownloader(String url, boolean start) {
        Downloader d = mDownloaderMap.get(url);
        if (d == null) {
            return createChildDownload(mTasksMap.get(url), start);
        }
        return d;
    }

    @Override
    public long getFileSize() {
        return mTotalLen;
    }

    @Override
    public long getCurrentLocation() {
        return mCurrentLocation;
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    @Override
    public void cancel() {
        closeTimer(false);
        mListener.onCancel();
        onCancel();
        if (!mExePool.isShutdown()) {
            mExePool.shutdown();
        }

        Set<String> keys = mDownloaderMap.keySet();
        for (String key : keys) {
            Downloader dt = mDownloaderMap.get(key);
            if (dt != null) {
                dt.cancel();
            }
        }
        delDownloadInfo();
        mTaskEntity.deleteData();
    }

    public void onCancel() {

    }

    /**
     * 删除所有子任务的下载信息
     */
    private void delDownloadInfo() {
        List<DownloadTaskEntity> tasks =
                DbEntity.findDatas(DownloadTaskEntity.class, "groupName=?", mTaskEntity.key);
        if (tasks != null && !tasks.isEmpty()) {
            for (DownloadTaskEntity taskEntity : tasks) {
                CommonUtil.delDownloadTaskConfig(mTaskEntity.removeFile, taskEntity);
            }
        }

        File dir = new File(mTaskEntity.getEntity().getDirPath());
        if (mTaskEntity.removeFile) {
            if (dir.exists()) {
                dir.delete();
            }
        } else {
            if (!mTaskEntity.getEntity().isComplete()) {
                dir.delete();
            }
        }
    }

    @Override
    public void stop() {
        closeTimer(false);
        onStop();
        if (!mExePool.isShutdown()) {
            mExePool.shutdown();
        }

        Set<String> keys = mDownloaderMap.keySet();
        for (String key : keys) {
            Downloader dt = mDownloaderMap.get(key);
            if (dt != null) {
                dt.stop();
            }
        }
    }

    protected void onStop() {

    }

    @Override
    public void start() {
        isRunning = true;
        mFailNum = 0;
        mListener.onPre();
        onStart();
    }

    protected void onStart() {

    }

    @Override
    public void resume() {
        start();
        mListener.onResume(mCurrentLocation);
    }


    private void closeTimer(boolean isRunning) {
        this.isRunning = isRunning;
        if (mTimer != null) {
            mTimer.purge();
            mTimer.cancel();
            mTimer = null;
        }
    }

    /**
     * 开始进度流程
     */
    void startRunningFlow() {
        closeTimer(true);
        mListener.onPostPre(mTotalLen);
        mListener.onStart(mCurrentLocation);
        startTimer();
    }

    private void startTimer() {
        mTimer = new Timer(true);
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!isRunning) {
                    closeTimer(false);
                } else if (mCurrentLocation >= 0) {
                    mListener.onProgress(mCurrentLocation);
                }
            }
        }, 0, 1000);
    }

    /**
     * 创建子任务下载器，默认创建完成自动启动
     */
    Downloader createChildDownload(DownloadTaskEntity taskEntity) {
        return createChildDownload(taskEntity, true);
    }

    /**
     * 创建子任务下载器，启动子任务下载器
     *
     * @param start 是否启动下载
     */
    Downloader createChildDownload(DownloadTaskEntity taskEntity, boolean start) {
        ChildDownloadListener listener = new ChildDownloadListener(taskEntity);
        Downloader dt = new Downloader(listener, taskEntity);
        mDownloaderMap.put(taskEntity.getEntity().getUrl(), dt);
        if (mExePool.isShutdown()) return dt;
        if (start) {
            mExePool.execute(dt);
        }
        return dt;
    }

    /**
     * 创建子任务下载信息
     */
    DownloadTaskEntity createChildDownloadTask(DownloadEntity entity) {
        DownloadTaskEntity taskEntity = mTasksMap.get(entity.getUrl());
        if (taskEntity != null) {
            taskEntity.entity = entity;
            //ftp登录的
            taskEntity.userName = mTaskEntity.userName;
            taskEntity.userPw = mTaskEntity.userPw;
            taskEntity.account = mTaskEntity.account;
            mTasksMap.put(entity.getUrl(), taskEntity);
            return taskEntity;
        }
        taskEntity = new DownloadTaskEntity();
        taskEntity.entity = entity;
        taskEntity.headers = mTaskEntity.headers;
        taskEntity.requestEnum = mTaskEntity.requestEnum;
        taskEntity.redirectUrlKey = mTaskEntity.redirectUrlKey;
        taskEntity.removeFile = mTaskEntity.removeFile;
        taskEntity.groupName = mTaskEntity.key;
        taskEntity.isGroupTask = true;
        taskEntity.requestType = mTaskEntity.requestType;
        //ftp登录的
        taskEntity.userName = mTaskEntity.userName;
        taskEntity.userPw = mTaskEntity.userPw;
        taskEntity.account = mTaskEntity.account;
        taskEntity.key = entity.getDownloadPath();
        taskEntity.save();
        mTasksMap.put(entity.getUrl(), taskEntity);
        return taskEntity;
    }

    /**
     * 子任务事件监听
     */
    private class ChildDownloadListener implements IDownloadListener {

        DownloadTaskEntity taskEntity;
        DownloadEntity entity;
        long lastLen = 0;
        private int RUN_SAVE_INTERVAL = 5 * 1000;  //5s保存一次下载中的进度
        private long mLastSaveTime;

        ChildDownloadListener(DownloadTaskEntity entity) {
            this.taskEntity = entity;
            this.entity = taskEntity.getEntity();
            this.entity.setFailNum(0);
            lastLen = this.entity.getCurrentProgress();
            mLastSaveTime = System.currentTimeMillis();
        }

        @Override
        public void onPre() {
            saveData(IEntity.STATE_PRE, -1);
        }

        @Override
        public void onPostPre(long fileSize) {
            entity.setFileSize(fileSize);
            entity.setConvertFileSize(CommonUtil.formatFileSize(fileSize));
            saveData(IEntity.STATE_POST_PRE, -1);
            mListener.onSubPre(entity);
        }

        @Override
        public void onResume(long resumeLocation) {
            saveData(IEntity.STATE_POST_PRE, IEntity.STATE_RUNNING);
            lastLen = resumeLocation;
            mListener.onSubStart(entity);
        }

        @Override
        public void onStart(long startLocation) {
            saveData(IEntity.STATE_POST_PRE, IEntity.STATE_RUNNING);
            lastLen = startLocation;
            mListener.onSubStart(entity);
        }

        @Override
        public void onProgress(long currentLocation) {
            long speed = currentLocation - lastLen;
            mCurrentLocation += speed;
            entity.setCurrentProgress(currentLocation);
            handleSpeed(speed);
            mListener.onSubRunning(entity);
            if (System.currentTimeMillis() - mLastSaveTime >= RUN_SAVE_INTERVAL) {
                saveData(IEntity.STATE_RUNNING, currentLocation);
                mLastSaveTime = System.currentTimeMillis();
            }
            lastLen = currentLocation;
        }

        @Override
        public void onStop(long stopLocation) {
            saveData(IEntity.STATE_STOP, stopLocation);
            handleSpeed(0);
            mListener.onSubStop(entity);
            synchronized (AbsGroupUtil.class) {
                mStopNum++;
                if (mStopNum + mCompleteNum + mInitFailNum + mFailNum >= mGroupSize) {
                    closeTimer(false);
                    mListener.onStop(mCurrentLocation);
                }
            }
        }

        @Override
        public void onCancel() {
            saveData(IEntity.STATE_CANCEL, -1);
            handleSpeed(0);
            mListener.onSubCancel(entity);
        }

        @Override
        public void onComplete() {
            saveData(IEntity.STATE_COMPLETE, entity.getFileSize());
            handleSpeed(0);
            mListener.onSubComplete(entity);
            synchronized (AbsGroupUtil.class) {
                mCompleteNum++;
                //如果子任务完成的数量和总任务数一致，表示任务组任务已经完成
                if (mCompleteNum >= mGroupSize) {
                    closeTimer(false);
                    mListener.onComplete();
                } else if (mStopNum + mCompleteNum + mInitFailNum + mFailNum >= mGroupSize) {
                    //如果子任务完成数量加上失败的数量和总任务数一致，则任务组停止下载
                    closeTimer(false);
                    mListener.onStop(mCurrentLocation);
                }
            }
        }

        @Override
        public void onFail(boolean needRetry) {
            entity.setFailNum(entity.getFailNum() + 1);
            saveData(IEntity.STATE_FAIL, lastLen);
            handleSpeed(0);
            reTry(needRetry);
        }

        /**
         * 失败后重试下载，如果失败次数超过5次，不再重试
         */
        private void reTry(boolean needRetry) {
            synchronized (AriaManager.LOCK) {
                if (entity.getFailNum() < 5 && isRunning && needRetry && NetUtils.isConnected(
                        AriaManager.APP)) {
                    reStartTask();
                } else {
                    mFailNum++;
                    mListener.onSubFail(entity);
                    //如果失败的任务数大于实际的下载任务数，任务组停止下载
                    if (mFailNum >= mActualTaskNum) {
                        closeTimer(false);
                        mListener.onStop(mCurrentLocation);
                    }
                }
            }
        }

        private void reStartTask() {
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Downloader dt = mDownloaderMap.get(entity.getUrl());
                    dt.start();
                }
            }, 3000);
        }

        private void handleSpeed(long speed) {
            entity.setSpeed(speed);
            entity.setConvertSpeed(speed <= 0 ? "" : CommonUtil.formatFileSize(speed) + "/s");
            entity.setPercent((int) (entity.getCurrentProgress() * 100 / entity.getFileSize()));
        }

        private void saveData(int state, long location) {
            entity.setState(state);
            entity.setComplete(state == IEntity.STATE_COMPLETE);
            if (entity.isComplete()) {
                entity.setCompleteTime(System.currentTimeMillis());
                entity.setCurrentProgress(entity.getFileSize());
            } else if (location > 0) {
                entity.setCurrentProgress(location);
            }
            entity.update();
        }

        @Override
        public void supportBreakpoint(boolean support) {

        }
    }
}
