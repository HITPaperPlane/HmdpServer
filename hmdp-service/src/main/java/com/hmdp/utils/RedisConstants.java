package com.hmdp.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 30L;
    // 统计窗口最长为5分钟，设置6分钟过期足以覆盖业务需求
    public static final Long SENDCODE_SENDTIME_TTL = 6L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";
    public static final String CACHE_SHOP_LIST_KEY = "cache:shop:list:";
    public static final Long CACHE_SHOP_LIST_TTL = 30L; // minutes, short-lived list cache for type pages

    public static final String CACHE_SHOP_TYPE_KEY = "cache:shoptype:";
    public static final Long CACHE_SHOP_TYPE_TTL = 1440L; // 24小时(分钟)

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    // use hash-tag {seckill} to keep lua keys in one slot on cluster
    public static final String SECKILL_STOCK_KEY = "seckill:{seckill}:stock:";
    public static final String SECKILL_OUTBOX_KEY = "seckill:{seckill}:outbox";
    public static final String SECKILL_LIMIT_SET_KEY = "seckill:{seckill}:order:";
    public static final String SECKILL_USER_COUNT_KEY = "seckill:{seckill}:usercount:";
    public static final String SECKILL_REQUEST_KEY = "seckill:{seckill}:req:";
    public static final String SECKILL_STATUS_KEY = "seckill:{seckill}:status:";
    public static final String SECKILL_REQ_TOKEN_KEY = "seckill:{seckill}:token:";
    public static final long SECKILL_REQ_TOKEN_TTL_MINUTES = 10L;

    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String FEED_OUTBOX_KEY = "feed:{feed}:outbox";
    public static final String FEED_SPLIT_LOCK_KEY = "feed:{feed}:split:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
    public static final String SENDCODE_SENDTIME_KEY ="sms:sendtime:";

    public static final String ONE_LEVERLIMIT_KEY ="limit:onelevel:";

    public static final String TWO_LEVERLIMIT_KEY ="limit:twolevel:";

    // use hash-tag to allow cross-key HyperLogLog ops in cluster
    public static final String UV_KEY ="uv:{site}:";

}
