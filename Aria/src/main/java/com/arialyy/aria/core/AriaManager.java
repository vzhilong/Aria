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
package com.arialyy.aria.core;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.app.Service;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.util.Log;
import android.widget.PopupWindow;

import com.arialyy.aria.core.command.ICmd;
import com.arialyy.aria.core.common.QueueMod;
import com.arialyy.aria.core.download.DownloadEntity;
import com.arialyy.aria.core.download.DownloadGroupEntity;
import com.arialyy.aria.core.download.DownloadGroupTaskEntity;
import com.arialyy.aria.core.download.DownloadReceiver;
import com.arialyy.aria.core.download.DownloadTaskEntity;
import com.arialyy.aria.core.inf.IReceiver;
import com.arialyy.aria.core.upload.UploadEntity;
import com.arialyy.aria.core.upload.UploadReceiver;
import com.arialyy.aria.core.upload.UploadTaskEntity;
import com.arialyy.aria.orm.DbEntity;
import com.arialyy.aria.orm.DbUtil;
import com.arialyy.aria.util.CommonUtil;

import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * Created by lyy on 2016/12/1.
 * https://github.com/AriaLyy/Aria
 * Aria管理器，任务操作在这里执行
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class AriaManager {
    public static final Object LOCK = new Object();
    public static final String DOWNLOAD_TEMP_DIR = "/Aria/temp/download/";
    public static final String UPLOAD_TEMP_DIR = "/Aria/temp/upload/";
    private static final String TAG = "AriaManager";
    private static final String DOWNLOAD = "_download";
    private static final String UPLOAD = "_upload";
    @SuppressLint("StaticFieldLeak")
    private static volatile AriaManager INSTANCE = null;
    private Map<String, IReceiver> mReceivers = new ConcurrentHashMap<>();
    private List<ICmd> mCommands = new ArrayList<>();
    private Configuration.DownloadConfig mDConfig;
    private Configuration.UploadConfig mUConfig;

    public static Application APP;

    private AriaManager() {
        DbUtil.init(APP);
        regAppLifeCallback(APP);
        initConfig();
    }

    public static void init(Application app) {
        APP = app;
    }

    public static AriaManager getInstance() {
        if (INSTANCE == null) {
            synchronized (LOCK) {
                INSTANCE = new AriaManager();
            }
        }
        return INSTANCE;
    }

    public Map<String, IReceiver> getReceiver() {
        return mReceivers;
    }

    /**
     * 设置上传任务的执行队列类型
     *
     * @param mod {@link QueueMod}
     */
    public AriaManager setUploadQueueMod(QueueMod mod) {
        mUConfig.setQueueMod(mod.tag);
        return this;
    }

    /**
     * 设置下载任务的执行队列类型
     *
     * @param mod {@link QueueMod}
     */
    public AriaManager setDownloadQueueMod(QueueMod mod) {
        mDConfig.setQueueMod(mod.tag);
        return this;
    }

    /**
     * 如果需要在代码中修改下载配置，请使用以下方法
     * <pre>
     *   <code>
     *     //修改最大任务队列数
     *     Aria.get(this).getDownloadConfig().setMaxTaskNum(3);
     *   </code>
     * </pre>
     */
    public Configuration.DownloadConfig getDownloadConfig() {
        return mDConfig;
    }

    /**
     * 如果需要在代码中修改下载配置，请使用以下方法
     * <pre>
     *   <code>
     *     //修改最大任务队列数
     *     Aria.get(this).getUploadConfig().setMaxTaskNum(3);
     *   </code>
     * </pre>
     */
    public Configuration.UploadConfig getUploadConfig() {
        return mUConfig;
    }

    /**
     * 设置命令
     */
    public AriaManager setCmd(ICmd command) {
        mCommands.add(command);
        return this;
    }

    /**
     * 设置一组命令
     */
    public <T extends ICmd> AriaManager setCmds(List<T> commands) {
        if (commands != null && commands.size() > 0) {
            mCommands.addAll(commands);
        }
        return this;
    }

    /**
     * 执行所有设置的命令
     */
    public synchronized void exe() {
        for (ICmd command : mCommands) {
            command.executeCmd();
        }
        mCommands.clear();
    }

    /**
     * 处理下载操作
     */
    DownloadReceiver download(Object obj) {
        IReceiver receiver = mReceivers.get(getKey(true, obj));
        if (receiver == null) {
            receiver = putReceiver(true, obj);
        }
        return (receiver instanceof DownloadReceiver) ? (DownloadReceiver) receiver : null;
    }

    /**
     * 处理上传操作
     */
    UploadReceiver upload(Object obj) {
        IReceiver receiver = mReceivers.get(getKey(false, obj));
        if (receiver == null) {
            receiver = putReceiver(false, obj);
        }
        return (receiver instanceof UploadReceiver) ? (UploadReceiver) receiver : null;
    }

    /**
     * 获取Aria下载错误日志
     *
     * @return 如果错误日志不存在则返回空，否则返回错误日志列表
     */
    public List<ErrorEntity> getErrorLog() {
        return DbEntity.findAllData(ErrorEntity.class);
    }

    /**
     * 清楚错误日志
     */
    public void cleanLog() {
        DbEntity.clean(ErrorEntity.class);
    }

    /**
     * 删除任务记录
     *
     * @param type 需要删除的任务类型，1、表示单任务下载。2、表示任务组下载。3、单任务上传
     * @param key  下载为保存路径、任务组为任务组名、上传为上传文件路径
     */
    public void delRecord(int type, String key) {
        switch (type) {
            case 1:
                DbEntity.deleteData(DownloadEntity.class, "url=?", key);
                DbEntity.deleteData(DownloadTaskEntity.class, "key=? and isGroupTask='false'", key);
                break;
            case 2:
                DbEntity.deleteData(DownloadGroupEntity.class, "groupName=?", key);
                DbEntity.deleteData(DownloadGroupTaskEntity.class, "key=?", key);
                break;
            case 3:
                DbEntity.deleteData(UploadEntity.class, "filePath=?", key);
                DbEntity.deleteData(UploadTaskEntity.class, "key=?", key);
                break;
        }
    }

    private IReceiver putReceiver(boolean isDownload, Object obj) {
        final String key = getKey(isDownload, obj);
        IReceiver receiver = mReceivers.get(key);
        boolean needRmReceiver = false;
        final WidgetLiftManager widgetLiftManager = new WidgetLiftManager();
        if (obj instanceof Dialog) {
            needRmReceiver = widgetLiftManager.handleDialogLift((Dialog) obj);
        } else if (obj instanceof PopupWindow) {
            needRmReceiver = widgetLiftManager.handlePopupWindowLift((PopupWindow) obj);
        } else if (obj instanceof DialogFragment) {
            needRmReceiver = widgetLiftManager.handleDialogFragmentLift((DialogFragment) obj);
        } else if (obj instanceof android.app.DialogFragment) {
            needRmReceiver = widgetLiftManager.handleDialogFragmentLift((android.app.DialogFragment) obj);
        }

        if (receiver == null) {
            if (isDownload) {
                DownloadReceiver dReceiver = new DownloadReceiver();
                dReceiver.targetName = obj.getClass().getName();
                dReceiver.obj = obj;
                dReceiver.needRmReceiver = needRmReceiver;
                mReceivers.put(key, dReceiver);
                receiver = dReceiver;
            } else {
                UploadReceiver uReceiver = new UploadReceiver();
                uReceiver.targetName = obj.getClass().getName();
                uReceiver.obj = obj;
                uReceiver.needRmReceiver = needRmReceiver;
                mReceivers.put(key, uReceiver);
                receiver = uReceiver;
            }
        }
        return receiver;
    }

    /**
     * 根据功能类型和控件类型获取对应的key
     */
    private String getKey(boolean isDownload, Object obj) {
        String clsName = obj.getClass().getName();
        String key = "";
        if (!(obj instanceof Activity)) {
            if (obj instanceof DialogFragment) {
                key = clsName + "_" + ((DialogFragment) obj).getActivity().getClass().getName();
            } else if (obj instanceof android.app.DialogFragment) {
                key = clsName + "_" + ((android.app.DialogFragment) obj).getActivity().getClass().getName();
            } else if (obj instanceof android.support.v4.app.Fragment) {
                key = clsName + "_" + ((Fragment) obj).getActivity().getClass().getName();
            } else if (obj instanceof android.app.Fragment) {
                key = clsName + "_" + ((android.app.Fragment) obj).getActivity().getClass().getName();
            } else if (obj instanceof Dialog) {
                Activity activity = ((Dialog) obj).getOwnerActivity();
                if (activity != null) {
                    key = clsName + "_" + activity.getClass().getName();
                } else {
                    key = clsName;
                }
            } else if (obj instanceof PopupWindow) {
                Context context = ((PopupWindow) obj).getContentView().getContext();
                if (context instanceof Activity) {
                    key = clsName + "_" + context.getClass().getName();
                } else {
                    key = clsName;
                }
            } else if (obj instanceof Service) {
                key = clsName;
            } else if (obj instanceof Application) {
                key = clsName;
            }
        }
        if (obj instanceof Activity || obj instanceof Service) {
            key = clsName;
        } else if (obj instanceof Application) {
            key = clsName;
        }
        if (TextUtils.isEmpty(key)) {
            throw new IllegalArgumentException("未知类型");
        }
        key += isDownload ? DOWNLOAD : UPLOAD;
        return key;
    }

    /**
     * 初始化配置文件
     */
    private void initConfig() {
        File xmlFile = new File(APP.getFilesDir().getPath() + Configuration.XML_FILE);
        File tempDir = new File(APP.getFilesDir().getPath() + "/temp");
        if (!xmlFile.exists()) {
            loadConfig();
        } else {
            try {
                String md5Code = CommonUtil.getFileMD5(xmlFile);
                File file = new File(APP.getFilesDir().getPath() + "temp.xml");
                if (file.exists()) {
                    file.delete();
                }
                CommonUtil.createFileFormInputStream(APP.getAssets().open("aria_config.xml"),
                        file.getPath());
                if (!CommonUtil.checkMD5(md5Code, file)) {
                    loadConfig();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        mDConfig = Configuration.DownloadConfig.getInstance();
        mUConfig = Configuration.UploadConfig.getInstance();
        if (tempDir.exists()) {
            File newDir = new File(APP.getFilesDir().getPath() + DOWNLOAD_TEMP_DIR);
            newDir.mkdirs();
            tempDir.renameTo(newDir);
        }
    }

    /**
     * 加载配置文件
     */
    private void loadConfig() {
        try {
            ConfigHelper helper = new ConfigHelper();
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser parser = factory.newSAXParser();
            parser.parse(APP.getAssets().open("aria_config.xml"), helper);
            CommonUtil.createFileFormInputStream(APP.getAssets().open("aria_config.xml"),
                    APP.getFilesDir().getPath() + Configuration.XML_FILE);
        } catch (ParserConfigurationException | IOException | SAXException e) {
            Log.e(TAG, e.toString());
        }
    }

    /**
     * 注册APP生命周期回调
     */
    private void regAppLifeCallback(Context context) {
        Context app = context.getApplicationContext();
        if (app instanceof Application) {
            LifeCallback mLifeCallback = new LifeCallback();
            ((Application) app).registerActivityLifecycleCallbacks(mLifeCallback);
        }
    }

    /**
     * 移除指定对象的receiver
     */
    public void removeReceiver(Object obj) {
        String clsName = obj.getClass().getName();
        for (Iterator<Map.Entry<String, IReceiver>> iter = mReceivers.entrySet().iterator();
             iter.hasNext(); ) {
            Map.Entry<String, IReceiver> entry = iter.next();
            String key = entry.getKey();
            if (key.contains(clsName)) {
                iter.remove();
            }
        }
    }

    /**
     * Aria注册对象被销毁时调用
     */
    void destroySchedulerListener(Object obj) {
        String clsName = obj.getClass().getName();
        for (Iterator<Map.Entry<String, IReceiver>> iter = mReceivers.entrySet().iterator();
             iter.hasNext(); ) {
            Map.Entry<String, IReceiver> entry = iter.next();
            String key = entry.getKey();
            if (key.contains(clsName)) {
                IReceiver receiver = mReceivers.get(key);
                if (receiver != null) {
                    receiver.unRegister();
                    receiver.destroy();
                }
                iter.remove();
            }
        }
    }

    /**
     * Activity生命周期
     */
    private class LifeCallback implements Application.ActivityLifecycleCallbacks {

        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {

        }

        @Override
        public void onActivityStarted(Activity activity) {

        }

        @Override
        public void onActivityResumed(Activity activity) {

        }

        @Override
        public void onActivityPaused(Activity activity) {

        }

        @Override
        public void onActivityStopped(Activity activity) {

        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

        }

        @Override
        public void onActivityDestroyed(Activity activity) {
            destroySchedulerListener(activity);
        }
    }
}
