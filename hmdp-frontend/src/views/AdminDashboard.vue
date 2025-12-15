<template>
  <div class="page">
    <section class="hero">
      <div class="hero-left">
        <div class="hero-title">系统管理</div>
        <div class="hero-sub">运营巡检 · UV 统计 · 店铺/券池/社区内容管理</div>
      </div>
      <div class="hero-actions">
        <van-button size="small" type="primary" @click="collectUv">采集一条 UV</van-button>
        <van-button size="small" plain type="primary" @click="loadUv">刷新 UV</van-button>
      </div>
    </section>

    <section class="grid-3">
      <div class="stat-card">
        <div class="stat-label">近 1 天 UV</div>
        <div class="stat-value">{{ uv1 }}</div>
        <div class="stat-sub">Redis HyperLogLog</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">近 3 天 UV</div>
        <div class="stat-value">{{ uv3 }}</div>
        <div class="stat-sub">接口：`GET /user/uv?days=3`</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">近 7 天 UV</div>
        <div class="stat-value">{{ uv7 }}</div>
        <div class="stat-sub">接口：`GET /user/uv?days=7`</div>
      </div>
    </section>

    <section class="grid-2">
      <div class="card">
        <div class="section-head">
          <div>
            <div class="title">运营入口</div>
            <div class="muted">把后台常用功能集中在一处，方便点点点巡检</div>
          </div>
        </div>
        <div class="quick-grid">
          <div class="quick-item" @click="to('/admin/shops')">
            <div class="qi-title">店铺巡检</div>
            <div class="qi-sub">创建/编辑/图片上传</div>
          </div>
          <div class="quick-item" @click="to('/admin/vouchers')">
            <div class="qi-title">券池管理</div>
            <div class="qi-sub">普通券/秒杀券</div>
          </div>
          <div class="quick-item" @click="to('/admin/blogs')">
            <div class="qi-title">社区内容</div>
            <div class="qi-sub">热榜巡查/素材上传</div>
          </div>
        </div>
        <div class="log">{{ log }}</div>
      </div>

      <div class="card">
        <div class="section-head">
          <div>
            <div class="title">运行说明</div>
            <div class="muted">常见数据链路与日志位置（便于排查）</div>
          </div>
        </div>
        <van-cell-group inset>
          <van-cell title="店铺缓存淘汰" value="PUT /shop 会清理 shop:cache:id" />
          <van-cell title="秒杀预热" value="POST /voucher/seckill 预热 Redis 库存" />
          <van-cell title="关注流推送" value="POST /blog 推送到 feed:{userId}" />
          <van-cell title="日志目录" value="logs/*.log" />
        </van-cell-group>
      </div>
    </section>
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
const uv7 = ref(0);
const log = ref('准备就绪');

function to(path) {
  router.push(path);
}

async function collectUv() {
  try {
    await request('/user/uv', { method: 'POST', token: session.token });
    log.value = '已写入一条 UV 记录';
    await loadUv();
  } catch (e) {
    log.value = e?.message || '写入失败';
  }
}

async function loadUv() {
  try {
    uv1.value = await request('/user/uv?days=1', { token: session.token });
    uv3.value = await request('/user/uv?days=3', { token: session.token });
    uv7.value = await request('/user/uv?days=7', { token: session.token });
    log.value = 'UV 已刷新';
  } catch (e) {
    log.value = e?.message || '刷新失败';
  }
}

onMounted(() => {
  loadUv();
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
  background: linear-gradient(135deg, #111827 0%, #334155 100%);
  color: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.22);
}

.hero-title {
  font-size: 18px;
  font-weight: 900;
}

.hero-sub {
  margin-top: 6px;
  font-size: 13px;
  opacity: 0.9;
}

.hero-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.grid-3 {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.stat-card {
  border-radius: 16px;
  padding: 16px;
  background: #fff;
  border: 1px solid rgba(0, 0, 0, 0.05);
  box-shadow: 0 10px 22px rgba(0, 0, 0, 0.04);
}

.stat-label {
  font-size: 12px;
  color: #6b7280;
  font-weight: 700;
}

.stat-value {
  margin-top: 6px;
  font-size: 26px;
  font-weight: 900;
  color: #111827;
  letter-spacing: -0.2px;
}

.stat-sub {
  margin-top: 4px;
  font-size: 12px;
  color: #9ca3af;
}

.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.card {
  padding: 14px;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  padding: 4px 2px 2px;
}

.quick-item {
  border-radius: 16px;
  padding: 14px;
  cursor: pointer;
  background: linear-gradient(135deg, #f9fafb 0%, #ffffff 100%);
  border: 1px solid rgba(0, 0, 0, 0.05);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.quick-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 26px rgba(0, 0, 0, 0.06);
}

.qi-title {
  font-weight: 900;
  color: #111827;
}

.qi-sub {
  margin-top: 6px;
  font-size: 12px;
  color: #6b7280;
}

@media (max-width: 980px) {
  .grid-2 {
    grid-template-columns: 1fr;
  }
  .grid-3 {
    grid-template-columns: 1fr;
  }
  .quick-grid {
    grid-template-columns: 1fr;
  }
}
</style>
