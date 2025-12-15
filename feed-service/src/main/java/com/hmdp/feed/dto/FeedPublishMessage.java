package com.hmdp.feed.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class FeedPublishMessage {
    private Long blogId;
    private Long authorId;
    private Long timestamp;
}
