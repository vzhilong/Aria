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

import android.annotation.TargetApi;
import android.app.Application;
import android.os.Build;

import com.arialyy.aria.core.download.DownloadReceiver;
import com.arialyy.aria.core.upload.UploadReceiver;

/**
 * Created by lyy on 2016/12/1.
 * https://github.com/AriaLyy/Aria
 * Aria启动，管理全局任务
 * <pre>
 *   <code>
 *   //下载
 *   Aria.download(this)
 *       .load(URL)     //下载地址，必填
 *       //文件保存路径，必填
 *       .setDownloadPath(Environment.getExternalStorageDirectory().getPath() + "/test.apk")
 *       .start();
 *   </code>
 *   <code>
 *    //上传
 *    Aria.upload(this)
 *        .load(filePath)     //文件路径，必填
 *        .setUploadUrl(uploadUrl)  //上传路径，必填
 *        .setAttachment(fileKey)   //服务器读取文件的key，必填
 *        .start();
 *   </code>
 * </pre>
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
public class Aria {

    private Aria() {
    }

    public static AriaManager get() {
        return AriaManager.getInstance();
    }

    /**
     * 初始化下载
     *
     * @param obj 支持类型有【Activity、Service、Application、DialogFragment、Fragment、PopupWindow、Dialog】
     */
    public static DownloadReceiver download() {
        return null;
//        return get().download(obj);
    }

    /**
     * 初始化上传
     *
     * @param obj 支持类型有【Activity、Service、Application、DialogFragment、Fragment、PopupWindow、Dialog】
     */
    public static UploadReceiver upload() {
        return null;
//        return get().upload(obj);
    }

    public void init(Application app) {
        AriaManager.init(app);
    }
}
