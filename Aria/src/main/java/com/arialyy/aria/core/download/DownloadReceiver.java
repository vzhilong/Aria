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

import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.arialyy.aria.core.AriaManager;
import com.arialyy.aria.core.command.ICmd;
import com.arialyy.aria.core.command.normal.CancelAllCmd;
import com.arialyy.aria.core.command.normal.NormalCmdFactory;
import com.arialyy.aria.core.common.ProxyHelper;
import com.arialyy.aria.core.inf.AbsReceiver;
import com.arialyy.aria.core.scheduler.DownloadSchedulers;
import com.arialyy.aria.core.scheduler.ISchedulerListener;
import com.arialyy.aria.orm.DbEntity;
import com.arialyy.aria.util.CheckUtil;
import com.arialyy.aria.util.CommonUtil;

import java.util.List;
import java.util.Set;

/**
 * Created by lyy on 2016/12/5.
 * 下载功能接收器
 */
public class DownloadReceiver extends AbsReceiver {
    private final String TAG = "DownloadReceiver";
    public ISchedulerListener<DownloadTask> listener;

    /**
     * 使用下载实体执行下载操作
     *
     * @param entity 下载实体
     */
    public DownloadTarget load(DownloadEntity entity) {
        return load(entity, false);
    }

    /**
     * 使用下载实体执行下载操作
     *
     * @param refreshInfo 是否刷新下载信息
     */
    public DownloadTarget load(DownloadEntity entity, boolean refreshInfo) {
        return new DownloadTarget(entity, targetName, refreshInfo);
    }

    /**
     * 加载Http、https单任务下载地址
     *
     * @param url 下载地址
     */
    public DownloadTarget load(@NonNull String url) {
        return load(url, false);
    }

    /**
     * 加载Http、https单任务下载地址
     *
     * @param url         下载地址
     * @param refreshInfo 是否刷新下载信息
     */
    public DownloadTarget load(@NonNull String url, boolean refreshInfo) {
        CheckUtil.checkDownloadUrl(url);
        return new DownloadTarget(url, targetName, refreshInfo);
    }

    /**
     * 将当前类注册到Aria
     */
    public DownloadReceiver register() {
        String className = obj.getClass().getName();
        Set<String> dCounter = ProxyHelper.getInstance().downloadCounter;
        if (dCounter != null && dCounter.contains(className)) {
            DownloadSchedulers.getInstance().register(obj);
        }
        return this;
    }

    /**
     * 取消注册
     */
    @Override
    public void unRegister() {
        String className = obj.getClass().getName();
        Set<String> dCounter = ProxyHelper.getInstance().downloadCounter;
        if (dCounter != null && dCounter.contains(className)) {
            DownloadSchedulers.getInstance().unRegister(obj);
        }
        if (needRmReceiver) {
            AriaManager.getInstance().removeReceiver(obj);
        }
    }

    @Override
    public void destroy() {
        targetName = null;
        listener = null;
    }

    /**
     * 通过下载链接获取下载实体
     */
    public DownloadEntity getDownloadEntity(String downloadUrl) {
        CheckUtil.checkDownloadUrl(downloadUrl);
        return DbEntity.findFirst(DownloadEntity.class, "url=? and isGroupChild='false'", downloadUrl);
    }

    /**
     * 通过下载链接获取保存在数据库的下载任务实体
     */
    public DownloadTaskEntity getDownloadTask(String downloadUrl) {
        CheckUtil.checkDownloadUrl(downloadUrl);
        DownloadEntity entity = getDownloadEntity(downloadUrl);
        if (entity == null || TextUtils.isEmpty(entity.getDownloadPath())) return null;
        return DbEntity.findFirst(DownloadTaskEntity.class, "key=? and isGroupTask='false'",
                entity.getDownloadPath());
    }

    /**
     * 下载任务是否存在
     */
    @Override
    public boolean taskExists(String downloadUrl) {
        return DownloadEntity.findFirst(DownloadEntity.class, "url=?", downloadUrl) != null;
    }

    /**
     * 获取普通下载任务列表
     */
    @Override
    public List<DownloadEntity> getSimpleTaskList() {
        return DownloadEntity.findDatas(DownloadEntity.class, "isGroupChild=? and downloadPath!=''",
                "false");
    }

    /**
     * 停止所有正在下载的任务，并清空等待队列。
     */
    @Override
    public void stopAllTask() {
        AriaManager.getInstance()
                .setCmd(NormalCmdFactory.getInstance()
                        .createCmd(targetName, new DownloadTaskEntity(), NormalCmdFactory.TASK_STOP_ALL,
                                ICmd.TASK_TYPE_DOWNLOAD))
                .exe();
    }

    /**
     * 恢复所有正在下载的任务
     * 1.如果执行队列没有满，则开始下载任务，直到执行队列满
     * 2.如果队列执行队列已经满了，则将所有任务添加到等待队列中
     */
    public void resumeAllTask() {
        AriaManager.getInstance()
                .setCmd(NormalCmdFactory.getInstance()
                        .createCmd(targetName, new DownloadTaskEntity(), NormalCmdFactory.TASK_RESUME_ALL,
                                ICmd.TASK_TYPE_DOWNLOAD))
                .exe();
    }

    /**
     * 删除所有任务
     *
     * @param removeFile {@code true} 删除已经下载完成的任务，不仅删除下载记录，还会删除已经下载完成的文件，{@code false}
     *                   如果文件已经下载完成，只删除下载记录
     */
    @Override
    public void removeAllTask(boolean removeFile) {
        final AriaManager ariaManager = AriaManager.getInstance();
        CancelAllCmd cancelCmd =
                (CancelAllCmd) CommonUtil.createNormalCmd(targetName, new DownloadTaskEntity(),
                        NormalCmdFactory.TASK_CANCEL_ALL, ICmd.TASK_TYPE_DOWNLOAD);
        cancelCmd.removeFile = removeFile;
        ariaManager.setCmd(cancelCmd).exe();
        Set<String> keys = ariaManager.getReceiver().keySet();
        for (String key : keys) {
            ariaManager.getReceiver().remove(key);
        }
    }
}