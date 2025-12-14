package com.hmdp.utils;

public class SystemConstants {
    /**
     * 图片上传目录（默认：运行目录下的 uploads/imgs）
     * <p>
     * 可通过 JVM 参数覆盖：-Dhmdp.image-upload-dir=/abs/path/to/imgs
     */
    public static final String IMAGE_UPLOAD_DIR =
            System.getProperty("hmdp.image-upload-dir", System.getProperty("user.dir") + "/uploads/imgs");
    public static final String USER_NICK_NAME_PREFIX = "user_";
    public static final int DEFAULT_PAGE_SIZE = 5;
    public static final int MAX_PAGE_SIZE = 10;
}
