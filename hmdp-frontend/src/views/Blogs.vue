<template>
  <div class="grid">
    <div class="card" style="grid-column:1/-1;">
      <div class="flex-between">
        <h3 class="title">笔记</h3>
        <div class="row">
          <button class="secondary" :class="{ active: tab==='hot' }" @click="setTab('hot')">热榜</button>
          <button class="secondary" :class="{ active: tab==='feed' }" @click="setTab('feed')">关注流</button>
        </div>
      </div>
      <div class="row" style="margin-top:10px;">
        <input v-model.number="publish.shopId" placeholder="关联店铺ID（可选）" />
        <input v-model="publish.title" placeholder="标题" />
      </div>
      <textarea v-model="publish.content" placeholder="写点什么..."></textarea>
      <input v-model="publish.images" placeholder="图片URL，逗号分隔" />
      <button @click="post">发布笔记</button>
      <div class="muted">上传图片可用后端 /upload/blog，返回文件名填入 images。</div>
    </div>

    <div class="card" v-for="b in blogs" :key="b.id">
      <div class="title">{{ b.title }}</div>
      <div class="muted">by {{ b.name || b.userId }} · 店铺 {{ b.shopId }}</div>
      <div class="muted">发布时间 {{ b.createTime }}</div>
      <div style="margin:8px 0;">{{ b.content }}</div>
      <div class="row">
        <button class="secondary" @click="like(b)">点赞 {{ b.liked || 0 }}</button>
        <button class="secondary" @click="follow(b)">{{ b.following ? '已关注' : '关注Ta' }}</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue';
import { request } from '../api/http';
import { useSessionStore } from '../stores/session';

const session = useSessionStore();
const token = computed(() => session.token);
const blogs = ref([]);
const tab = ref('hot');
const publish = reactive({ shopId: '', title: '', images: '', content: '' });

onMounted(() => {
  session.role = 'USER';
  loadHot();
});

function setTab(key) {
  tab.value = key;
  key === 'hot' ? loadHot() : loadFeed();
}

async function loadHot() {
  blogs.value = await request('/blog/hot');
}

async function loadFeed() {
  if (!token.value) return alert('请先登录');
  const params = new URLSearchParams({ lastId: Date.now(), offset: 0 });
  const data = await request(`/blog/of/follow?${params.toString()}`, { token: token.value });
  blogs.value = data?.list || [];
}

async function post() {
  if (!token.value) return alert('请先登录');
  const body = { ...publish, shopId: publish.shopId ? Number(publish.shopId) : undefined };
  const id = await request('/blog', { method: 'POST', body, token: token.value });
  alert(`发布成功 ${id}`);
  publish.title = publish.content = publish.images = '';
  loadHot();
}

async function like(b) {
  if (!token.value) return alert('请先登录');
  await request(`/blog/like/${b.id}`, { method: 'PUT', token: token.value });
  b.liked = (b.liked || 0) + 1;
}

async function follow(b) {
  if (!token.value) return alert('请先登录');
  if (!b.userId) return;
  await request(`/follow/${b.userId}/true`, { method: 'PUT', token: token.value });
  b.following = true;
}
</script>
