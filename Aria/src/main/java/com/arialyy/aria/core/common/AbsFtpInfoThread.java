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

import android.text.TextUtils;
import android.util.Log;

import com.arialyy.aria.core.AriaManager;
import com.arialyy.aria.core.download.DownloadEntity;
import com.arialyy.aria.core.download.DownloadGroupEntity;
import com.arialyy.aria.core.inf.AbsEntity;
import com.arialyy.aria.core.inf.AbsTaskEntity;
import com.arialyy.aria.core.upload.UploadEntity;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import java.io.IOException;

/**
 * Created by Aria.Lao on 2017/7/25.
 * 获取ftp文件夹信息
 */
public abstract class AbsFtpInfoThread<ENTITY extends AbsEntity, TASK_ENTITY extends AbsTaskEntity<ENTITY>>
        implements Runnable {

    private final String TAG = "AbsFtpInfoThread";
    protected ENTITY mEntity;
    protected TASK_ENTITY mTaskEntity;
    protected OnFileInfoCallback mCallback;
    protected long mSize = 0;
    protected String mServerIp, mPort;
    protected String charSet = "UTF-8";
    private int mConnectTimeOut;
    private boolean isUpload = false;

    public AbsFtpInfoThread(TASK_ENTITY taskEntity, OnFileInfoCallback callback) {
        mTaskEntity = taskEntity;
        mEntity = taskEntity.getEntity();
        mConnectTimeOut =
                AriaManager.getInstance(AriaManager.APP).getDownloadConfig().getConnectTimeOut();
        mCallback = callback;
        if (mEntity instanceof UploadEntity) {
            isUpload = true;
        }
    }

    /**
     * 设置请求的远程文件路径
     *
     * @return 远程文件路径
     */
    protected abstract String setRemotePath();

    @Override
    public void run() {
        FTPClient client = null;
        try {
            client = createFtpClient();
            if (client == null) return;
            String remotePath =
                    new String(setRemotePath().getBytes(charSet), AbsFtpThreadTask.SERVER_CHARSET);
            FTPFile[] files = client.listFiles(remotePath);
            mSize = getFileSize(files, client, remotePath);
            int reply = client.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                if (isUpload) {
                    //服务器上没有该文件路径，表示该任务为新的上传任务
                    mTaskEntity.isNewTask = true;
                } else {
                    client.disconnect();
                    failDownload("获取文件信息错误，错误码为：" + reply);
                    return;
                }
            }
            mTaskEntity.code = reply;
            if (mSize != 0 && !isUpload) {
                mEntity.setFileSize(mSize);
            }
            mEntity.update();
            mTaskEntity.update();
            onPreComplete(reply);
        } catch (IOException e) {
            failDownload(e.getMessage());
        } finally {
            if (client != null) {
                try {
                    client.disconnect();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void start() {
        new Thread(this).start();
    }

    protected void onPreComplete(int code) {

    }

    /**
     * 创建FTP客户端
     */
    private FTPClient createFtpClient() throws IOException {
        String url = "";
        if (mEntity instanceof DownloadEntity) {
            url = ((DownloadEntity) mEntity).getUrl();
        } else if (mEntity instanceof UploadEntity) {
            url = ((UploadEntity) mEntity).getUrl();
        } else if (mEntity instanceof DownloadGroupEntity) {
            url = mEntity.getKey();
        } else {
            failDownload("未知实体");
            Log.e(TAG, "未知实体");
            return null;
        }
        String[] pp = url.split("/")[2].split(":");
        mServerIp = pp[0];
        mPort = pp[1];
        FTPClient client = new FTPClient();
        // 连接服务器
        client.connect(mServerIp, Integer.parseInt(mPort));
        if (!TextUtils.isEmpty(mTaskEntity.account)) {
            client.login(mTaskEntity.userName, mTaskEntity.userPw);
        } else {
            client.login(mTaskEntity.userName, mTaskEntity.userPw, mTaskEntity.account);
        }
        int reply = client.getReplyCode();
        if (!FTPReply.isPositiveCompletion(reply)) {
            client.disconnect();
            failDownload("无法连接到ftp服务器，错误码为：" + reply);
            return null;
        }
        // 开启服务器对UTF-8的支持，如果服务器支持就用UTF-8编码
        charSet = "UTF-8";
        if (!TextUtils.isEmpty(mTaskEntity.charSet) || !FTPReply.isPositiveCompletion(
                client.sendCommand("OPTS UTF8", "ON"))) {
            charSet = mTaskEntity.charSet;
        }
        client.setControlEncoding(charSet);
        client.setDataTimeout(10 * 1000);
        client.enterLocalPassiveMode();
        client.setFileType(FTP.BINARY_FILE_TYPE);
        client.setControlKeepAliveTimeout(5);
        return client;
    }

    /**
     * 遍历FTP服务器上对应文件或文件夹大小
     *
     * @throws IOException 字符串编码转换错误
     */
    private long getFileSize(FTPFile[] files, FTPClient client, String dirName) throws IOException {
        long size = 0;
        String path = dirName + "/";
        for (FTPFile file : files) {
            if (file.isFile()) {
                size += file.getSize();
                handleFile(path + file.getName(), file);
            } else {
                String remotePath =
                        new String((path + file.getName()).getBytes(charSet), AbsFtpThreadTask.SERVER_CHARSET);
                size += getFileSize(client.listFiles(remotePath), client, path + file.getName());
            }
        }
        return size;
    }

    /**
     * 处理FTP文件信息
     *
     * @param remotePath ftp服务器文件夹路径
     * @param ftpFile    ftp服务器上对应的文件
     */
    protected void handleFile(String remotePath, FTPFile ftpFile) {
    }

    private void failDownload(String errorMsg) {
        Log.e(TAG, errorMsg);
        if (mCallback != null) {
            mCallback.onFail(mEntity.getKey(), errorMsg);
        }
    }
}
