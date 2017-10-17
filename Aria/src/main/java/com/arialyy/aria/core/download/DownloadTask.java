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

package com.arialyy.aria.core.download;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.arialyy.aria.core.AriaManager;
import com.arialyy.aria.core.common.IUtil;
import com.arialyy.aria.core.download.downloader.SimpleDownloadUtil;
import com.arialyy.aria.core.inf.AbsNormalTask;
import com.arialyy.aria.core.scheduler.ISchedulers;

import java.io.File;

/**
 * Created by lyy on 2016/8/11.
 * 下载任务类
 */
public class DownloadTask extends AbsNormalTask<DownloadTaskEntity> {
    public static final String TAG = "DownloadTask";

    private DownloadListener mListener;
    private DownloadEntity mEntity;
    private IUtil mUtil;

    private DownloadTask(DownloadTaskEntity taskEntity, Handler outHandler) {
        mTaskEntity = taskEntity;
        mOutHandler = outHandler;
        mContext = AriaManager.APP;
        mListener = new DownloadListener(this, mOutHandler);
        mUtil = new SimpleDownloadUtil(taskEntity, mListener);
        mEntity = taskEntity.getEntity();
    }

    /**
     * 获取文件保存路径
     *
     * @return 如果路径不存在，返回null
     */
    public String getDownloadPath() {
        File file = new File(mEntity.getDownloadPath());
        if (!file.exists()) {
            return null;
        }
        return mEntity.getDownloadPath();
    }

    public DownloadEntity getEntity() {
        return mTaskEntity.getEntity();
    }


    @Override
    public String getKey() {
        return mEntity.getUrl();
    }


    @Override
    public boolean isRunning() {
        return mUtil.isRunning();
    }

    public DownloadEntity getDownloadEntity() {
        return mEntity;
    }

    /**
     * 暂停任务，并让任务处于等待状态
     */
    @Override
    public void stopAndWait() {
        stop(true);
    }

    /**
     * 开始下载
     */
    @Override
    public void start() {
        mListener.isWait = false;
        if (mUtil.isRunning()) {
            Log.d(TAG, "任务正在下载");
        } else {
            mUtil.start();
        }
    }

    /**
     * 停止下载
     */
    @Override
    public void stop() {
        stop(false);
    }

    private void stop(boolean isWait) {
        mListener.isWait = isWait;
        if (mUtil.isRunning()) {
            mUtil.stop();
        } else {
            mListener.onStop(mEntity.getCurrentProgress());
        }
    }

    /**
     * 取消下载
     */
    @Override
    public void cancel() {
        if (!mUtil.isRunning()) {
            mListener.onCancel();
        }
        mUtil.cancel();
    }

    public static class Builder {
        DownloadTaskEntity taskEntity;
        Handler outHandler;
        String targetName;

        public Builder(String targetName, DownloadTaskEntity taskEntity) {
            this.targetName = targetName;
            this.taskEntity = taskEntity;
        }

        /**
         * 设置自定义Handler处理下载状态时间
         *
         * @param schedulers {@link ISchedulers}
         */
        public Builder setOutHandler(ISchedulers schedulers) {
            try {
                outHandler = new Handler(schedulers);
            } catch (Exception e) {
                e.printStackTrace();
                outHandler = new Handler(Looper.getMainLooper(), schedulers);
            }
            return this;
        }

        public DownloadTask build() {
            DownloadTask task = new DownloadTask(taskEntity, outHandler);
            task.setTargetName(targetName);
            taskEntity.getEntity().save();
            taskEntity.save();
            return task;
        }
    }
}