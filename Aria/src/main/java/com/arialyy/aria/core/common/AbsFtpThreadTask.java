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

import com.arialyy.aria.core.inf.AbsNormalEntity;
import com.arialyy.aria.core.inf.AbsTaskEntity;
import com.arialyy.aria.core.inf.IEventListener;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

import java.io.IOException;

/**
 * Created by lyy on 2017/9/26.
 * FTP单任务父类
 */
public abstract class AbsFtpThreadTask<ENTITY extends AbsNormalEntity, TASK_ENTITY extends AbsTaskEntity<ENTITY>>
        extends AbsThreadTask<ENTITY, TASK_ENTITY> {
    /**
     * FTP 服务器编码
     */
    public static String SERVER_CHARSET = "ISO-8859-1";
    protected String charSet, serverIp, port;

    protected AbsFtpThreadTask(StateConstance constance, IEventListener listener,
                               SubThreadConfig<TASK_ENTITY> info) {
        super(constance, listener, info);
    }

    /**
     * 构建FTP客户端
     */
    protected FTPClient createClient() throws IOException {
        String url = mEntity.getUrl();
        String[] pp = url.split("/")[2].split(":");
        serverIp = pp[0];
        port = pp[1];
        FTPClient client = new FTPClient();
        // 连接服务器
        client.connect(serverIp, Integer.parseInt(port));
        if (!TextUtils.isEmpty(mTaskEntity.account)) {
            client.login(mTaskEntity.userName, mTaskEntity.userPw);
        } else {
            client.login(mTaskEntity.userName, mTaskEntity.userPw, mTaskEntity.account);
        }
        int reply = client.getReplyCode();
        if (!FTPReply.isPositiveCompletion(reply)) {
            client.disconnect();
            fail(STATE.CURRENT_LOCATION, "无法连接到ftp服务器，错误码为：" + reply, null);
            return null;
        }
        // 开启服务器对UTF-8的支持，如果服务器支持就用UTF-8编码
        charSet = "UTF-8";
        if (!TextUtils.isEmpty(mTaskEntity.charSet) || !FTPReply.isPositiveCompletion(
                client.sendCommand("OPTS UTF8", "ON"))) {
            charSet = mTaskEntity.charSet;
        }
        client.setControlEncoding(charSet);
        client.setDataTimeout(STATE.READ_TIME_OUT);
        client.enterLocalPassiveMode();
        client.setFileType(FTP.BINARY_FILE_TYPE);
        client.setBufferSize(mBufSize);
        client.setControlKeepAliveTimeout(5);
        //client.setCopyStreamListener();
        return client;
    }
}
