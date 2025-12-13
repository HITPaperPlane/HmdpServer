<template>
  <div class="grid">
    <div class="card">
      <h3 class="title">发布探店笔记</h3>
      <div class="grid" style="grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));">
        <div>
          <label>标题</label>
          <input v-model="blogForm.title" placeholder="新品上线 / 门店活动" />
        </div>
        <div>
          <label>关联店铺ID（可选）</label>
          <input v-model="blogForm.shopId" placeholder="关联你的店铺" />
        </div>
      </div>
      <label>文字内容</label>
      <textarea v-model="blogForm.content" placeholder="描述亮点、优惠信息、营业时间等"></textarea>
      <div class="row">
        <input type="file" @change="onFile" />
        <button class="secondary" @click="clearImages">清空图片</button>
      </div>
      <div class="muted">图片文件会上传到 `/upload/blog` 返回路径，提交时会用逗号拼接。</div>
      <div class="row" style="flex-wrap: wrap;">
        <span class="pill" v-for="(img, idx) in blogForm.images" :key="idx">{{ img }}</span>
      </div>
      <button @click="publish">发布笔记</button>
      <div class="log">{{ log }}</div>
    </div>
    <div class="card">
      <h4 class="title">我发布的笔记</h4>
      <button class="secondary" @click="loadMine">刷新</button>
      <ul class="list">
        <li v-for="b in mine" :key="b.id">
          <div class="flex-between">
            <div>
              <strong>{{ b.title }}</strong>
              <div class="muted">ID: {{ b.id }} | 店铺: {{ b.shopId || '未关联' }}</div>
            </div>
            <span class="pill">点赞 {{ b.liked }}</span>
          </div>
          <div class="muted">{{ b.content?.slice(0, 80) }}</div>
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { request, upload } from '../api/http';
import { useSessionStore } from '../stores/session';

const session = useSessionStore();
const blogForm = reactive({
  title: '',
  content: '',
  shopId: '',
  images: []
});
const mine = ref([]);
const log = ref('准备就绪');

function clearImages() {
  blogForm.images = [];
}

async function onFile(e) {
  const file = e.target.files?.[0];
  if (!file) return;
  try {
    const path = await upload('/upload/blog', file, session.token);
    blogForm.images.push(path);
    log.value = `上传成功：${path}`;
  } catch (err) {
    log.value = err.message;
  }
}

async function publish() {
  try {
    const payload = {
      ...blogForm,
      images: blogForm.images.join(',')
    };
    const id = await request('/blog', { method: 'POST', body: payload, token: session.token });
    log.value = `发布成功，笔记ID：${id}`;
    loadMine();
  } catch (e) {
    log.value = e.message;
  }
}

async function loadMine() {
  try {
    mine.value = await request('/blog/of/me?current=1', { token: session.token });
  } catch (e) {
    log.value = e.message;
  }
}

if (session.role !== 'MERCHANT') {
  session.role = 'MERCHANT';
}
loadMine();
</script>
