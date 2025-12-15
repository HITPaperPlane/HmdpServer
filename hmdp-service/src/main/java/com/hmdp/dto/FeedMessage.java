package com.hmdp.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class FeedMessage {
    private Long blogId;
    private Long authorId;
    private Long timestamp;
}
