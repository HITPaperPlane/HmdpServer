package com.hmdp.feed.constants;

public final class RedisKeys {
    private RedisKeys() {}

    public static final String FEED_KEY = "feed:";
    public static final String FEED_SPLIT_LOCK_KEY = "feed:{feed}:split:";
}
