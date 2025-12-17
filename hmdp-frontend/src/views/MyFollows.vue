<template>
  <div class="page">
    <section class="hero">
      <div class="hero-left">
        <div class="hero-title">我的关注</div>
        <div class="hero-sub">查看你关注的用户，进入主页浏览 TA 的笔记</div>
      </div>
      <div class="hero-actions">
        <van-button size="small" plain type="primary" :loading="loading" @click="load">刷新</van-button>
      </div>
    </section>

    <section class="card">
      <van-search
          v-model="keyword"
          shape="round"
          placeholder="搜索昵称/简介"
          clearable
      />

      <div v-if="!loading && filtered.length === 0" style="padding: 18px 0;">
        <van-empty description="暂无关注" />
      </div>

      <div class="user-list">
        <div
            v-for="u in filtered"
            :key="u.userId"
            class="user-card"
            @click="openUser(u.userId)"
        >
          <van-image round width="44" height="44" :src="resolveImg(u.icon) || defaultAvatar" />
          <div class="meta">
            <div class="name-row">
              <div class="name van-ellipsis">{{ u.nickName || '未命名用户' }}</div>
              <van-tag size="mini" plain :type="roleTagType(u.role)">{{ roleLabel(u.role) }}</van-tag>
            </div>
            <div class="muted van-ellipsis">{{ u.introduce || '暂无简介' }}</div>
          </div>
          <div class="actions" @click.stop>
            <van-button size="small" plain type="primary" @click="openUser(u.userId)">主页</van-button>
            <van-button size="small" plain type="danger" :loading="unfollowLoading[u.userId]" @click="unfollow(u.userId)">取消</van-button>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { request } from '../api/http';
import { resolveImg } from '../utils/media';
import { useSessionStore } from '../stores/session';
import defaultAvatar from '../assets/default-avatar.svg';

const router = useRouter();
const session = useSessionStore();

const loading = ref(false);
const keyword = ref('');
const list = ref([]);
const unfollowLoading = reactive({});

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase();
  if (!kw) return list.value;
  return list.value.filter((u) => {
    const name = (u.nickName || '').toLowerCase();
    const intro = (u.introduce || '').toLowerCase();
    return name.includes(kw) || intro.includes(kw);
  });
});

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

function openUser(id) {
  router.push(`/users/${id}`);
}

async function load() {
  if (!session.token) return;
  loading.value = true;
  try {
    const rows = await request('/follow/of/me', { token: session.token });
    list.value = Array.isArray(rows) ? rows : [];
  } finally {
    loading.value = false;
  }
}

async function unfollow(userId) {
  if (!session.token) return;
  unfollowLoading[userId] = true;
  try {
    await request(`/follow/${userId}/false`, { method: 'PUT', token: session.token });
    list.value = list.value.filter((x) => x.userId !== userId);
  } catch {
    // ignore
  } finally {
    unfollowLoading[userId] = false;
  }
}

onMounted(load);
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

.hero-title {
  font-size: 20px;
  font-weight: 900;
}

.hero-sub {
  margin-top: 6px;
  font-size: 13px;
  opacity: 0.92;
}

.card {
  padding: 14px;
}

.user-list {
  display: grid;
  gap: 10px;
  padding-top: 10px;
}

.user-card {
  display: grid;
  grid-template-columns: 52px 1fr auto;
  align-items: center;
  gap: 12px;
  padding: 12px 12px;
  border-radius: 14px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  background: #fff;
  cursor: pointer;
}

.meta {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.name-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-width: 0;
}

.name {
  font-weight: 800;
  color: #222;
  font-size: 14px;
  min-width: 0;
}

.muted {
  color: #777;
  font-size: 12px;
}

.actions {
  display: flex;
  gap: 8px;
}
</style>

