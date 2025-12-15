package com.hmdp.feed.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class FeedBatchTask {
    private Long blogId;
    private Long authorId;
    private Long offset;
    private Integer limit;
    private Long timestamp;
}
