///*
// * Copyright (C) 2016 AriaLyy(https://github.com/AriaLyy/Aria)
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *      http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//package com.arialyy.annotations;
//
//import java.lang.annotation.ElementType;
//import java.lang.annotation.Retention;
//import java.lang.annotation.RetentionPolicy;
//import java.lang.annotation.Target;
//
///**
// * Created by Aria.Lao on 2017/6/6.
// * Aria下载事件被注解的方法中，参数仅能有一个，参数类型为{@link com.arialyy.aria.core.download.DownloadGroupTask}
// * <pre>
// *   <code>
// *      {@literal @}DownloadGroup.onPre(groupName)
// *       protected void onPre(DownloadGroupTask task) {
// *       }
// *   </code>
// * </pre>
// * {@literal @}Download.onPre("myGroupName")，如果你的注解中增加了url描述，
// * 则表示，所有下载任务中，只有下载地址为"myGroupName"的任务才能回调该注解的方法。
// *
// *
// * 如果希望获取子任务的状态，可以使用onSub..类的注解
// * <pre>
// *   <code>
// *      {@literal @}DownloadGroup.onSubPre(groupName)
// *        protected void onPre(DownloadGroupTask task, String url) {
// *      }
// *   </code>
// * </pre>
// */
//@Retention(RetentionPolicy.CLASS) @Target(ElementType.METHOD) public @interface DownloadGroup {
//  /**
//   * 如果你在方法中添加{@code @DownloadGroup.onPre}注解，在预处理完成时，Aria会调用该方法
//   */
//  @Retention(RetentionPolicy.CLASS) @Target(ElementType.METHOD) @interface onPre {
//    String[] value() default { AriaConstance.NO_URL };
//  }
//
//  /**
//   * 如果你在方法中添加{@code @DownloadGroup.onTaskPre}注解，在任务预处理完成时，Aria会调用该方法
//   */
//  @Retention(RetentionPolicy.CLASS) @Target(ElementType.METHOD) @interface onTaskPre {
//    String[] value() default { AriaConstance.NO_URL };
//  }
//
//  /**
//   * 如果你在方法中添加{@code @DownloadGroup.onTaskResume}注解，在任务恢复下载时，Aria会调用该方法
//   */
//  @Retention(RetentionPolicy.CLASS) @Target(ElementType.METHOD) @interface onTaskResume {
//    String[] value() default { AriaConstance.NO_URL };
//  }
//
//  /**
//   * 如果你在方法中添加{@code @DownloadGroup.onTaskStart}注解，在任务开始下载时，Aria会调用该方法
//   */
//  @Retention(RetentionPolicy.CLASS) @Target(ElementType.METHOD) @interface onTaskStart {
//    String[] value() default { AriaConstance.NO_URL };
//  }
//
//  /**
//   * 如果你在方法中添加{@code @DownloadGroup.onTaskStop}注解，在任务停止时，Aria会调用该方法
//   */
//  @Retention(RetentionPolicy.CLASS) @Target(ElementType.METHOD) @interface onTaskStop {
//    String[] value() default { AriaConstance.NO_URL };
//  }
//
//  /**
//   * 如果你在方法中添加{@code @DownloadGroup.onTaskCancel}l注解，在任务取消时，Aria会调用该方法
//   */
//  @Retention(RetentionPolicy.CLASS) @Target(ElementType.METHOD) @interface onTaskCancel {
//    String[] value() default { AriaConstance.NO_URL };
//  }
//
//  /**
//   * 如果你在方法中添加{@code @DownloadGroup.onTaskFail)注解，在任务预失败时，Aria会调用该方法
//   */
//  @Retention(RetentionPolicy.CLASS) @Target(ElementType.METHOD) @interface onTaskFail {
//    String[] value() default { AriaConstance.NO_URL };
//  }
//
//  /**
//   * 如果你在方法中添加{@code @DownloadGroup.onTaskComplete}注解，在任务完成时，Aria会调用该方法
//   */
//  @Retention(RetentionPolicy.CLASS) @Target(ElementType.METHOD) @interface onTaskComplete {
//    String[] value() default { AriaConstance.NO_URL };
//  }
//
//  /**
//   * 如果你在方法中添加{@code @DownloadGroup.onTaskRunning}注解，在任务正在下载，Aria会调用该方法
//   */
//  @Retention(RetentionPolicy.CLASS) @Target(ElementType.METHOD) @interface onTaskRunning {
//    String[] value() default { AriaConstance.NO_URL };
//  }
//
//  /**
//   * 任务组子任务预处理的注解
//   */
//  @Retention(RetentionPolicy.CLASS) @Target(ElementType.METHOD) @interface onSubTaskPre {
//    String[] value() default { AriaConstance.NO_URL };
//  }
//
//  /**
//   * 任务组子任务开始的注解
//   */
//  @Retention(RetentionPolicy.CLASS) @Target(ElementType.METHOD) @interface onSubTaskStart {
//    String[] value() default { AriaConstance.NO_URL };
//  }
//
//  /**
//   * 任务组子任务停止的注解
//   */
//  @Retention(RetentionPolicy.CLASS) @Target(ElementType.METHOD) @interface onSubTaskStop {
//    String[] value() default { AriaConstance.NO_URL };
//  }
//
//  ///**
//  // * 任务组子任务删除的注解
//  // */
//  //@Retention(RetentionPolicy.CLASS) @Target(ElementType.METHOD) @interface onSubTaskCancel {
//  //  String[] value() default { AriaConstance.NO_URL };
//  //}
//
//  /**
//   * 任务组子任务失败的注解
//   */
//  @Retention(RetentionPolicy.CLASS) @Target(ElementType.METHOD) @interface onSubTaskFail {
//    String[] value() default { AriaConstance.NO_URL };
//  }
//
//  /**
//   * 任务组子任务完成的注解
//   */
//  @Retention(RetentionPolicy.CLASS) @Target(ElementType.METHOD) @interface onSubTaskComplete {
//    String[] value() default { AriaConstance.NO_URL };
//  }
//
//  /**
//   * 任务组子任务正在执行的注解
//   */
//  @Retention(RetentionPolicy.CLASS) @Target(ElementType.METHOD) @interface onSubTaskRunning {
//    String[] value() default { AriaConstance.NO_URL };
//  }
//}
