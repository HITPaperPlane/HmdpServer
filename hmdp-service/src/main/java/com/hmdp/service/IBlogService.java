package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    Result queryById(Integer id);

    Result queryHotBlog(Integer current);

    Result likeBlog(Long id);

    Result queryBlogLikes(Integer id);

    Result saveBlog(Blog blog);

    Result queryBlogOfFollow(Long max, Integer offset);

    /**
     * 关注流查询（支持兜底修复）
     *
     * @param max     滚动查询的 lastId（score 上界，毫秒时间戳）
     * @param offset  相同 score 的偏移
     * @param refresh 下拉刷新（Smart Pull：只做增量一致性检查）
     * @param force   强制回源回填（高成本：允许重建/回填）
     */
    Result queryBlogOfFollow(Long max, Integer offset, Boolean refresh, Boolean force);

    /**
     * 按店铺查询探店笔记（用于店铺详情页展示）
     */
    Result queryBlogOfShop(Long shopId, Integer current);
}
