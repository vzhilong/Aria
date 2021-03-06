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
package com.arialyy.aria.core.inf;

import android.os.Parcel;
import android.os.Parcelable;

import com.arialyy.aria.orm.DbEntity;
import com.arialyy.aria.orm.Ignore;

/**
 * Created by AriaL on 2017/6/29.
 */
public abstract class AbsEntity extends DbEntity implements IEntity, Parcelable {
    /**
     * 速度
     */
    @Ignore
    private long speed = 0;

    /**
     * 下载失败计数，每次开始都重置为0
     */
    @Ignore
    private int failNum = 0;

    /**
     * 扩展字段
     */
    private String str = "";

    /**
     * 文件大小
     */
    private long fileSize = 1;

    private int state = STATE_WAIT;

    /**
     * 当前下载进度
     */
    private long currentProgress = 0;

    /**
     * 完成时间
     */
    private long completeTime;

    /**
     * 进度百分比
     */
    @Ignore
    private int percent;

    private boolean isComplete = false;

    /**
     * 服务器地址
     */
    private String url = "";

    /**
     * 文件名
     */
    private String fileName = "";

    private boolean isRedirect = false; //是否重定向

    private String redirectUrl = ""; //重定向链接

    public AbsEntity() {
    }

    protected AbsEntity(Parcel in) {
        this.speed = in.readLong();
        this.failNum = in.readInt();
        this.str = in.readString();
        this.fileSize = in.readLong();
        this.state = in.readInt();
        this.currentProgress = in.readLong();
        this.completeTime = in.readLong();
        this.percent = in.readInt();
        this.isComplete = in.readByte() != 0;
        this.url = in.readString();
        this.fileName = in.readString();
        this.isRedirect = in.readByte() != 0;
        this.redirectUrl = in.readString();
    }

    public boolean isComplete() {
        return isComplete;
    }

    public void setComplete(boolean complete) {
        isComplete = complete;
    }

    public int getFailNum() {
        return failNum;
    }

    public void setFailNum(int failNum) {
        this.failNum = failNum;
    }

    public long getSpeed() {
        return speed;
    }

    public void setSpeed(long speed) {
        this.speed = speed;
    }

    public String getStr() {
        return str;
    }

    public void setStr(String str) {
        this.str = str;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public long getCurrentProgress() {
        return currentProgress;
    }

    public void setCurrentProgress(long currentProgress) {
        this.currentProgress = currentProgress;
    }

    public long getCompleteTime() {
        return completeTime;
    }

    public void setCompleteTime(long completeTime) {
        this.completeTime = completeTime;
    }

    public int getPercent() {
        return percent;
    }

    public void setPercent(int percent) {
        this.percent = percent;
    }

    /**
     * 实体唯一标识符
     */
    public abstract String getKey();


    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public boolean isRedirect() {
        return isRedirect;
    }

    public void setRedirect(boolean redirect) {
        isRedirect = redirect;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public void setRedirectUrl(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(this.speed);
        dest.writeInt(this.failNum);
        dest.writeString(this.str);
        dest.writeLong(this.fileSize);
        dest.writeInt(this.state);
        dest.writeLong(this.currentProgress);
        dest.writeLong(this.completeTime);
        dest.writeInt(this.percent);
        dest.writeByte(this.isComplete ? (byte) 1 : (byte) 0);
        dest.writeString(this.url);
        dest.writeString(this.fileName);
        dest.writeByte(this.isRedirect ? (byte) 1 : (byte) 0);
        dest.writeString(this.redirectUrl);
    }
}
