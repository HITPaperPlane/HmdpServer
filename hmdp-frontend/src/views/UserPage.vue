<template>
  <div class="page">
    <section class="hero">
      <div class="hero-left">
        <van-image round width="56" height="56" :src="avatarUrl" class="avatar" />
        <div class="hero-meta">
          <div class="hero-title-row">
            <div class="hero-title van-ellipsis">{{ user.nickName || '用户主页' }}</div>
            <van-tag size="mini" plain :type="roleTagType(user.role)">{{ roleLabel(user.role) }}</van-tag>
          </div>
          <div class="hero-sub van-ellipsis">{{ info.introduce || '暂无简介' }}</div>
          <div class="hero-stats">
            <span class="pill">粉丝 {{ info.fans ?? 0 }}</span>
            <span class="pill">关注 {{ info.followee ?? 0 }}</span>
            <span class="pill">等级 {{ info.level ?? 0 }}</span>
          </div>
        </div>
      </div>
      <div class="hero-actions">
        <van-button size="small" plain type="default" @click="goBack">返回</van-button>
        <van-button
            v-if="showFollow"
            size="small"
            :type="following ? 'primary' : 'default'"
            plain
            :loading="followLoading"
            @click="toggleFollow"
        >
          {{ following ? '已关注' : '关注' }}
        </van-button>
      </div>
    </section>

    <section class="card">
      <div class="section-head">
        <div>
          <div class="title">TA 的笔记</div>
          <div class="muted">接口：`GET /blog/of/user?id=...`</div>
        </div>
        <van-button size="small" plain type="primary" @click="refreshBlogs" :loading="blogs.loading">刷新</van-button>
      </div>

      <div v-if="!blogs.loading && blogs.list.length === 0" style="padding: 18px 0;">
        <van-empty description="暂无笔记" />
      </div>

      <van-list v-model:loading="blogs.loading" :finished="blogs.finished" finished-text="没有更多了" @load="loadBlogs">
        <div class="blog-grid">
          <article v-for="b in blogs.list" :key="b.id" class="blog-card" @click="goBlog(b.id)">
            <div class="blog-head">
              <div class="author">
                <van-image round width="34" height="34" :src="avatarUrl" />
                <div class="author-meta">
                  <div class="author-name van-ellipsis">{{ user.nickName || '匿名用户' }}</div>
                  <div class="muted">ID {{ b.id }} · {{ b.createTime || '' }}</div>
                </div>
              </div>
              <van-button size="small" plain type="primary" @click.stop="goBlog(b.id)">详情</van-button>
            </div>

            <div class="blog-title">{{ b.title }}</div>

            <div v-if="b._imgs.length" class="img-row">
              <van-image
                  v-for="(img, idx) in b._imgs.slice(0, 3)"
                  :key="idx"
                  :src="img"
                  fit="cover"
                  radius="10"
                  class="thumb"
              />
              <div v-if="b._imgs.length > 3" class="more">+{{ b._imgs.length - 3 }}</div>
            </div>

            <div class="blog-snippet" v-html="b._snippet"></div>
          </article>
        </div>
      </van-list>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { request } from '../api/http';
import { resolveImg, splitImages } from '../utils/media';
import { sanitizeHtml } from '../utils/sanitize';
import { useSessionStore } from '../stores/session';
import defaultAvatar from '../assets/default-avatar.svg';

const route = useRoute();
const router = useRouter();
const session = useSessionStore();

const user = reactive({ id: null, nickName: '', icon: '', role: '' });
const info = reactive({ introduce: '', fans: 0, followee: 0, level: 0 });
const following = ref(false);
const followLoading = ref(false);

const blogs = reactive({ list: [], page: 1, loading: false, finished: false, inFlight: false });

const userId = computed(() => Number(route.params.id));
const avatarUrl = computed(() => resolveImg(user.icon) || defaultAvatar);
const showFollow = computed(() => Boolean(session.token) && userId.value && session.profile.id !== userId.value);

function roleLabel(role) {
  const r = (role || '').toUpperCase();
  if (r === 'ADMIN') return '管理员';
  if (r === 'MERCHANT') return '商家';
  return '普通用户';
}

function roleTagType(role) {
  const r = (role || '').toUpperCase();
  if (r === 'ADMIN') return 'danger';
  if (r === 'MERCHANT') return 'success';
  return 'primary';
}

function normalizeBlog(b) {
  const imgs = splitImages(b.images);
  const snippet = sanitizeHtml(b.content || '');
  return { ...b, _imgs: imgs, _snippet: snippet };
}

function resetBlogs() {
  blogs.list = [];
  blogs.page = 1;
  blogs.loading = false;
  blogs.finished = false;
  blogs.inFlight = false;
}

async function loadUser() {
  const id = userId.value;
  if (!id) return;
  const u = await request(`/user/${id}`, { token: session.token || undefined });
  Object.assign(user, u || {});
  const ui = await request(`/user/info/${id}`, { token: session.token || undefined });
  Object.assign(info, ui || {});

  if (showFollow.value) {
    try {
      following.value = Boolean(await request(`/follow/or/not/${id}`, { token: session.token }));
    } catch {
      following.value = false;
    }
  } else {
    following.value = false;
  }
}

async function loadBlogs() {
  if (!userId.value || blogs.finished || blogs.inFlight) return;
  blogs.inFlight = true;
  blogs.loading = true;
  try {
    const rows = await request(`/blog/of/user?id=${userId.value}&current=${blogs.page}`, { token: session.token || undefined });
    const arr = Array.isArray(rows) ? rows : [];
    if (arr.length === 0) {
      blogs.finished = true;
      return;
    }
    blogs.list.push(...arr.map(normalizeBlog));
    blogs.page += 1;
    if (arr.length < 10) blogs.finished = true;
  } catch {
    blogs.finished = true;
  } finally {
    blogs.loading = false;
    blogs.inFlight = false;
  }
}

async function toggleFollow() {
  if (!showFollow.value) return;
  followLoading.value = true;
  try {
    const target = following.value ? 'false' : 'true';
    await request(`/follow/${userId.value}/${target}`, { method: 'PUT', token: session.token });
    following.value = !following.value;
  } finally {
    followLoading.value = false;
  }
}

function refreshBlogs() {
  resetBlogs();
  loadBlogs();
}

function goBlog(id) {
  router.push(`/blogs/${id}`);
}

function goBack() {
  router.back();
}

watch(userId, async () => {
  resetBlogs();
  await loadUser();
  await loadBlogs();
});

onMounted(async () => {
  await loadUser();
  await loadBlogs();
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
  padding: 18px;
  background: linear-gradient(135deg, #ff6b6b 0%, #ff8e53 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  box-shadow: 0 10px 30px rgba(255, 107, 107, 0.2);
}

.hero-left {
  display: flex;
  gap: 12px;
  align-items: center;
  min-width: 0;
}

.hero-meta {
  min-width: 0;
  display: grid;
  gap: 6px;
}

.hero-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.hero-title {
  font-size: 20px;
  font-weight: 900;
  min-width: 0;
}

.hero-sub {
  font-size: 13px;
  opacity: 0.92;
}

.hero-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.pill {
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 12px;
}

.card {
  padding: 14px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.title {
  font-weight: 900;
  font-size: 16px;
  color: #222;
}

.muted {
  color: rgba(0, 0, 0, 0.55);
  font-size: 12px;
}

.blog-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 14px;
  padding: 14px 0;
}

.blog-card {
  border-radius: 16px;
  border: 1px solid rgba(0, 0, 0, 0.05);
  background: #fff;
  padding: 14px;
  box-shadow: 0 10px 22px rgba(0, 0, 0, 0.04);
  cursor: pointer;
}

.blog-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.author {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.author-meta {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.author-name {
  font-weight: 800;
  color: #222;
  font-size: 13px;
  min-width: 0;
}

.blog-title {
  margin-top: 12px;
  font-size: 16px;
  font-weight: 900;
  color: #111;
  line-height: 1.25;
}

.img-row {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  position: relative;
}

.thumb {
  width: 100%;
  height: 92px;
  background: #f5f6f7;
}

.more {
  position: absolute;
  right: 10px;
  bottom: 10px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  padding: 3px 8px;
  border-radius: 999px;
}

.blog-snippet {
  margin-top: 12px;
  color: #555;
  font-size: 13px;
  line-height: 1.7;
  max-height: 88px;
  overflow: hidden;
}
</style>

