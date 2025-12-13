<template>
  <div class="grid">
    <div class="card">
      <h3 class="title">实时指标</h3>
      <div class="row">
        <button class="secondary" @click="collectUv">采集一条 UV（HyperLogLog）</button>
        <button class="secondary" @click="loadUv">刷新 UV</button>
      </div>
      <div class="grid" style="grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));">
        <div class="card">
          <div class="title">近 1 天 UV</div>
          <div class="badge">{{ uv1 }}</div>
        </div>
        <div class="card">
          <div class="title">近 3 天 UV</div>
          <div class="badge">{{ uv3 }}</div>
        </div>
      </div>
      <div class="muted">数据来源：`/user/uv` 读取 Redis HyperLogLog，POST 会写入。</div>
    </div>
    <div class="card">
      <h4 class="title">运营检查清单</h4>
      <ul class="list">
        <li>店铺缓存：修改店铺后，接口 `/shop` PUT 会清理 `shop:cache:id`。</li>
        <li>券与秒杀：`/voucher/seckill` 会预热 Redis 库存键 `seckill:{seckill}:stock:&lt;id&gt;`。</li>
        <li>关注/笔记流：发布笔记 `/blog` 会推送粉丝 feed ZSet，关注流用 `/blog/of/follow`。</li>
        <li>日志路径：`logs/hmdp-service.log`、`logs/order-service.log`、`logs/relay-service.log`。</li>
      </ul>
      <div class="row">
        <button class="secondary" @click="to('/admin/shops')">店铺巡检</button>
        <button class="secondary" @click="to('/admin/vouchers')">券池管理</button>
        <button class="secondary" @click="to('/admin/blogs')">笔记巡查</button>
      </div>
      <div class="log">{{ log }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { request } from '../api/http';
import { useSessionStore } from '../stores/session';

const session = useSessionStore();
const router = useRouter();
const uv1 = ref(0);
const uv3 = ref(0);
const log = ref('等待操作');

function to(path) {
  router.push(path);
}

async function collectUv() {
  try {
    await request('/user/uv', { method: 'POST', token: session.token });
    log.value = '已写入一条 UV 记录';
    loadUv();
  } catch (e) {
    log.value = e.message;
  }
}

async function loadUv() {
  try {
    uv1.value = await request('/user/uv?days=1', { token: session.token });
    uv3.value = await request('/user/uv?days=3', { token: session.token });
    log.value = 'UV 已刷新';
  } catch (e) {
    log.value = e.message;
  }
}

onMounted(() => {
  if (session.role !== 'ADMIN') session.role = 'ADMIN';
  loadUv();
});
</script>
