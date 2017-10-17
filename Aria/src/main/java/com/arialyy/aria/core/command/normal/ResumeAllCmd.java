package com.arialyy.aria.core.command.normal;

import android.util.Log;

import com.arialyy.aria.core.AriaManager;
import com.arialyy.aria.core.download.DownloadTaskEntity;
import com.arialyy.aria.core.inf.AbsTaskEntity;
import com.arialyy.aria.core.inf.IEntity;
import com.arialyy.aria.core.queue.DownloadTaskQueue;
import com.arialyy.aria.core.queue.UploadTaskQueue;
import com.arialyy.aria.core.upload.UploadTaskEntity;
import com.arialyy.aria.orm.DbEntity;
import com.arialyy.aria.util.NetUtils;

import java.util.ArrayList;
import java.util.List;

//import com.arialyy.aria.core.download.DownloadGroupTaskEntity;
//import com.arialyy.aria.core.queue.DownloadGroupTaskQueue;

/**
 * Created by AriaL on 2017/6/13.
 * 恢复所有停止的任务
 * 1.如果执行队列没有满，则开始下载任务，直到执行队列满
 * 2.如果队列执行队列已经满了，则将所有任务添加到等待队列中
 * 3.如果队列中只有等待状态的任务，如果执行队列没有满，则会启动等待状态的任务，如果执行队列已经满了，则会将所有等待状态的任务加载到缓存队列中
 */
final class ResumeAllCmd<T extends AbsTaskEntity> extends AbsNormalCmd<T> {
    private List<AbsTaskEntity> mWaitList = new ArrayList<>();

    /**
     * @param targetName 产生任务的对象名
     */
    ResumeAllCmd(String targetName, T entity, int taskType) {
        super(targetName, entity, taskType);
    }

    @Override
    public void executeCmd() {
        if (!NetUtils.isConnected(AriaManager.APP)) {
            Log.w(TAG, "恢复任务失败，网络未连接");
            return;
        }
        if (isDownloadCmd) {
            resumeTask(findTaskData(1));
            resumeTask(findTaskData(2));
        } else {
            resumeTask(findTaskData(3));
        }
        resumeWaitTask();
    }

    /**
     * 查找数据库中的所有任务数据
     *
     * @param type {@code 1}单任务下载任务；{@code 2}任务组下载任务；{@code 3} 单任务上传任务
     */
    private List<AbsTaskEntity> findTaskData(int type) {
        List<AbsTaskEntity> tempList = new ArrayList<>();
        switch (type) {
            case 1:
                List<DownloadTaskEntity> dTaskEntity =
                        DbEntity.findDatas(DownloadTaskEntity.class, "isGroupTask=?", "false");
                if (dTaskEntity != null && !dTaskEntity.isEmpty()) {
                    tempList.addAll(dTaskEntity);
                }
                break;
            case 3:
                List<UploadTaskEntity> uTaskEntity =
                        DbEntity.findDatas(UploadTaskEntity.class, "isGroupTask=?", "false");
                if (uTaskEntity != null && !uTaskEntity.isEmpty()) {
                    tempList.addAll(uTaskEntity);
                }
                break;
        }
        return tempList;
    }

    /**
     * 恢复任务
     */
    private void resumeTask(List<AbsTaskEntity> taskList) {
        if (taskList != null && !taskList.isEmpty()) {
            for (AbsTaskEntity te : taskList) {
                if (te == null || te.getEntity() == null) continue;
                int state = te.getState();
                if (state == IEntity.STATE_STOP || state == IEntity.STATE_OTHER) {
                    resumeEntity(te);
                } else if (state == IEntity.STATE_WAIT) {
                    mWaitList.add(te);
                } else if (state == IEntity.STATE_RUNNING) {
                    if (!mQueue.taskIsRunning(te.getEntity().getKey())) {
                        resumeEntity(te);
                    }
                }
            }
        }
    }

    /**
     * 处理等待状态的任务
     */
    private void resumeWaitTask() {
        int maxTaskNum;
        AriaManager manager = AriaManager.getInstance();
        if (isDownloadCmd) {
            maxTaskNum = manager.getDownloadConfig().getMaxTaskNum();
        } else {
            maxTaskNum = manager.getUploadConfig().getMaxTaskNum();
        }
        if (mWaitList == null || mWaitList.isEmpty()) return;
        for (AbsTaskEntity te : mWaitList) {
            if (mQueue.getCurrentExePoolNum() < maxTaskNum) {
                startTask(createTask(te));
            } else {
                createTask(te);
            }
        }
    }

    /**
     * 恢复实体任务
     *
     * @param te 任务实体
     */
    private void resumeEntity(AbsTaskEntity te) {
        if (te instanceof DownloadTaskEntity) {
            mQueue = DownloadTaskQueue.getInstance();
        } else if (te instanceof UploadTaskEntity) {
            mQueue = UploadTaskQueue.getInstance();
        }
        int exeNum = mQueue.getCurrentExePoolNum();
        if (exeNum == 0 || exeNum < mQueue.getMaxTaskNum()) {
            startTask(createTask(te));
        } else {
            te.getEntity().setState(IEntity.STATE_WAIT);
            createTask(te);
        }
    }
}
