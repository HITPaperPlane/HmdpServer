package com.hmdp.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.order.entity.MessageOutbox;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageOutboxMapper extends BaseMapper<MessageOutbox> {
}

