package com.hmdp.relay.constants;

public final class RedisKeys {
    private RedisKeys() {}
    public static final String SECKILL_OUTBOX_KEY = "seckill:{seckill}:outbox";
    public static final String RELAY_QUEUE_PREFIX = "relay:queue:";
    public static final String FEED_OUTBOX_KEY = "feed:{feed}:outbox";
    public static final String FEED_RELAY_QUEUE_PREFIX = "relay:feed:queue:";
    public static final String CACHE_SHOP_KEY = "cache:shop:";
    public static final String CACHE_BLOG_KEY = "cache:blog:";
}
