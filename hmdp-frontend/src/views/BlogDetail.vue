<template>
  <div class="page">
    <div class="card hero">
      <div class="hero-left">
        <div class="hero-title">{{ blog.title || '笔记详情' }}</div>
        <div class="hero-sub">
          <span class="pill">作者：{{ blog.name || blog.userId || '-' }}</span>
          <span class="pill">店铺：{{ blog.shopId || '-' }}</span>
          <span class="pill">{{ blog.createTime || '' }}</span>
        </div>
        <div class="hero-actions">
          <van-button size="small" plain type="primary" :disabled="!session.token" @click="toggleLike">
            {{ blog.isLike ? '已赞' : '点赞' }} {{ blog.liked || 0 }}
          </van-button>
          <van-button
              size="small"
              plain
              type="default"
              :disabled="!session.token || !blog.userId || blog.userId === session.profile.id"
              @click="toggleFollow"
          >
            {{ following ? '已关注' : '关注作者' }}
          </van-button>
          <van-button size="small" plain type="default" @click="router.back()">返回</van-button>
        </div>
      </div>

      <div class="hero-right">
        <van-image round width="64" height="64" :src="authorAvatar || defaultAvatar" />
        <div class="author-name">{{ blog.name || '匿名用户' }}</div>
      </div>
    </div>

    <div class="card" v-if="images.length">
      <div class="title">图片</div>
      <van-swipe :autoplay="3500" indicator-color="white" class="swipe">
        <van-swipe-item v-for="(img, idx) in images" :key="idx">
          <van-image :src="img" fit="cover" class="swipe-img" />
        </van-swipe-item>
      </van-swipe>
    </div>

    <div class="card">
      <div class="title">正文</div>
      <div class="content" v-html="safeHtml"></div>
    </div>

    <div class="card">
      <div class="section-head">
        <div>
          <div class="title">点赞用户</div>
          <div class="muted">接口：`GET /blog/likes/{id}`（展示最近 5 个）</div>
        </div>
        <van-button size="small" plain type="primary" @click="loadLikes">刷新</van-button>
      </div>

      <div v-if="likes.length === 0" style="padding: 8px 0;">
        <van-empty description="暂无点赞" />
      </div>
      <div v-else class="likes">
        <div class="like-user" v-for="u in likes" :key="u.id">
          <van-image round width="34" height="34" :src="resolveImg(u.icon) || defaultAvatar" />
          <div class="like-name">{{ u.nickName || u.id }}</div>
        </div>
      </div>
      <div class="log">{{ log }}</div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { request } from '../api/http';
import { resolveImg, splitImages } from '../utils/media';
import { sanitizeHtml } from '../utils/sanitize';
import { useSessionStore } from '../stores/session';
import defaultAvatar from '../assets/default-avatar.svg';

const route = useRoute();
const router = useRouter();
const session = useSessionStore();

const blog = reactive({});
const images = ref([]);
const likes = ref([]);
const following = ref(false);
const log = ref('准备就绪');

const safeHtml = computed(() => sanitizeHtml(blog.content || ''));
const authorAvatar = computed(() => resolveImg(blog.icon));

async function loadBlog() {
  const id = route.params.id;
  const data = await request(`/blog/${id}`, { token: session.token || undefined });
  Object.assign(blog, data || {});
  images.value = splitImages(blog.images);
}

async function loadLikes() {
  if (!blog.id) return;
  try {
    const list = await request(`/blog/likes/${blog.id}`, { token: session.token || undefined });
    likes.value = Array.isArray(list) ? list : [];
    log.value = '已刷新';
  } catch (e) {
    log.value = e?.message || '加载失败';
  }
}

async function loadFollowState() {
  if (!session.token || !blog.userId || blog.userId === session.profile.id) {
    following.value = false;
    return;
  }
  try {
    const isFollow = await request(`/follow/or/not/${blog.userId}`, { token: session.token });
    following.value = Boolean(isFollow);
  } catch {
    following.value = false;
  }
}

async function toggleLike() {
  if (!session.token || !blog.id) return;
  try {
    await request(`/blog/like/${blog.id}`, { method: 'PUT', token: session.token });
    const delta = blog.isLike ? -1 : 1;
    blog.isLike = !blog.isLike;
    blog.liked = (blog.liked || 0) + delta;
    loadLikes();
  } catch (e) {
    log.value = e?.message || '操作失败';
  }
}

async function toggleFollow() {
  if (!session.token || !blog.userId || blog.userId === session.profile.id) return;
  try {
    const target = following.value ? 'false' : 'true';
    await request(`/follow/${blog.userId}/${target}`, { method: 'PUT', token: session.token });
    following.value = !following.value;
  } catch (e) {
    log.value = e?.message || '操作失败';
  }
}

onMounted(async () => {
  await loadBlog();
  await loadLikes();
  await loadFollowState();
});
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.hero {
  border-radius: 18px;
  background: linear-gradient(135deg, #ff6b6b 0%, #ff8e53 100%);
  color: #fff;
  display: grid;
  grid-template-columns: 1fr 220px;
  gap: 14px;
}

.hero-left {
  padding: 18px;
}

.hero-right {
  padding: 18px;
  border-left: 1px solid rgba(255, 255, 255, 0.25);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.hero-title {
  font-size: 20px;
  font-weight: 900;
  line-height: 1.2;
}

.hero-sub {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  opacity: 0.95;
  font-size: 12px;
}

.pill {
  display: inline-flex;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.25);
}

.hero-actions {
  margin-top: 12px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.author-name {
  font-weight: 900;
  font-size: 14px;
  text-align: center;
}

.swipe {
  margin-top: 12px;
  border-radius: 16px;
  overflow: hidden;
  height: 320px;
}

.swipe-img {
  width: 100%;
  height: 320px;
  background: #f5f6f7;
}

.content {
  margin-top: 10px;
  color: #333;
  line-height: 1.9;
  font-size: 14px;
}

.content :deep(img) {
  max-width: 100%;
  border-radius: 12px;
}

.content :deep(p) {
  margin: 10px 0;
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
}

.likes {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.like-user {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 999px;
  border: 1px solid rgba(0, 0, 0, 0.05);
  background: #fff;
}

.like-name {
  font-size: 13px;
  font-weight: 700;
  color: #333;
  max-width: 110px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 980px) {
  .hero {
    grid-template-columns: 1fr;
  }
  .hero-right {
    border-left: none;
    border-top: 1px solid rgba(255, 255, 255, 0.25);
    flex-direction: row;
    justify-content: flex-start;
  }
  .swipe, .swipe-img {
    height: 220px;
  }
}
</style>

