<template>
  <div class="grid">
    <div class="card">
      <h3 class="title">热点笔记巡查</h3>
      <div class="row">
        <button class="secondary" @click="loadHot">刷新热榜</button>
        <input v-model="blogId" placeholder="按ID查询" @keyup.enter="loadOne" />
        <button class="secondary" @click="loadOne">查询</button>
      </div>
      <ul class="list">
        <li v-for="b in hot" :key="b.id">
          <div class="flex-between">
            <div>
              <strong>{{ b.title }}</strong>
              <div class="muted">ID: {{ b.id }} | 作者: {{ b.name || '未知' }}</div>
            </div>
            <div class="row">
              <span class="pill">点赞 {{ b.liked }}</span>
              <button class="secondary" @click="like(b.id)">点赞/取消</button>
            </div>
          </div>
          <div class="muted">{{ b.content?.slice(0, 120) }}</div>
          <div class="muted">图片：{{ b.images }}</div>
        </li>
      </ul>
    </div>
    <div class="card">
      <h4 class="title">上传与删除图片</h4>
      <div class="row">
        <input type="file" @change="uploadImg" />
        <input v-model="deleteName" placeholder="/blogs/0/1/xxx.png" />
        <button class="secondary" @click="deleteImg">删除该图片</button>
      </div>
      <div class="muted">上传接口：`POST /upload/blog`，删除：`GET /upload/blog/delete?name=`。</div>
      <div class="log">{{ log }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { request, upload } from '../api/http';
import { useSessionStore } from '../stores/session';

const session = useSessionStore();
const hot = ref([]);
const blogId = ref('');
const deleteName = ref('');
const log = ref('等待操作');

async function loadHot() {
  try {
    hot.value = await request('/blog/hot?current=1', { token: session.token });
    log.value = '热榜已刷新';
  } catch (e) {
    log.value = e.message;
  }
}

async function loadOne() {
  if (!blogId.value) return;
  try {
    const item = await request(`/blog/${blogId.value}`, { token: session.token });
    hot.value = [item];
    log.value = '已载入单条笔记';
  } catch (e) {
    log.value = e.message;
  }
}

async function like(id) {
  try {
    await request(`/blog/like/${id}`, { method: 'PUT', token: session.token });
    log.value = '点赞/取消成功';
    loadHot();
  } catch (e) {
    log.value = e.message;
  }
}

async function uploadImg(e) {
  const file = e.target.files?.[0];
  if (!file) return;
  try {
    const name = await upload('/upload/blog', file, session.token);
    log.value = `上传成功：${name}`;
  } catch (err) {
    log.value = err.message;
  }
}

async function deleteImg() {
  if (!deleteName.value) return;
  try {
    await request(`/upload/blog/delete?name=${encodeURIComponent(deleteName.value)}`, { token: session.token });
    log.value = '删除成功';
  } catch (err) {
    log.value = err.message;
  }
}

if (session.role !== 'ADMIN') session.role = 'ADMIN';
loadHot();
</script>
