<template>
  <div class="page">
    <section class="hero">
      <div class="hero-left">
        <div class="hero-title">我的订单</div>
        <div class="hero-sub">秒杀下单为异步落库，可在此查看最终结果与订单详情</div>
      </div>
      <div class="hero-actions">
        <van-button size="small" plain type="primary" :loading="loading && page === 1" @click="refresh">刷新</van-button>
      </div>
    </section>

    <section class="card">
      <div v-if="!loading && orders.length === 0" style="padding: 18px 0;">
        <van-empty description="暂无订单" />
      </div>

      <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="loadMore">
        <div class="order-grid">
          <div v-for="o in orders" :key="o.id" class="order-card">
            <div class="order-head">
              <div class="order-title van-ellipsis">{{ o.voucherTitle || `券 ${o.voucherId}` }}</div>
              <van-tag size="mini" plain :type="limitTagType(o.limitType)">{{ limitLabel(o) }}</van-tag>
            </div>

            <div class="order-sub">
              <span class="shop-link" @click="openShop(o.shopId)">{{ o.shopName || `店铺 ${o.shopId || '-'}` }}</span>
              <span class="dot">·</span>
              <span class="time">{{ o.createTime || '' }}</span>
            </div>

            <div class="order-meta">
              <span class="pill">数量 {{ o.count || 1 }}</span>
              <span class="pill">状态 {{ statusLabel(o.status) }}</span>
              <span class="pill">支付 {{ payLabel(o.payType) }}</span>
            </div>

            <div class="order-actions">
              <van-button size="small" plain type="primary" :disabled="!o.shopId" @click="openShop(o.shopId)">去店铺</van-button>
              <van-button size="small" plain type="default" :disabled="!o.requestId" @click="copyReq(o.requestId)">复制 ReqId</van-button>
            </div>
          </div>
        </div>
      </van-list>

      <div v-if="error" class="error">{{ error }}</div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { request } from '../api/http';
import { useSessionStore } from '../stores/session';

const router = useRouter();
const session = useSessionStore();

const orders = ref([]);
const page = ref(1);
const size = ref(10);
const loading = ref(false);
const finished = ref(false);
const inFlight = ref(false);
const error = ref('');

function limitLabel(o) {
  const t = Number(o.limitType || 1);
  if (t === 1) return '一人一单';
  if (t === 3) return `累计限购${o.userLimit ? `(${o.userLimit})` : ''}`;
  return '一人多单';
}

function limitTagType(limitType) {
  const t = Number(limitType || 1);
  if (t === 1) return 'primary';
  if (t === 2) return 'success';
  return 'warning';
}

function statusLabel(status) {
  const s = Number(status || 0);
  const map = {
    1: '未支付',
    2: '已支付',
    3: '已核销',
    4: '已取消',
    5: '退款中',
    6: '已退款'
  };
  return map[s] || String(status || '-');
}

function payLabel(payType) {
  const p = Number(payType || 0);
  const map = { 1: '余额', 2: '支付宝', 3: '微信' };
  return map[p] || String(payType || '-');
}

function openShop(shopId) {
  if (!shopId) return;
  router.push(`/shops/${shopId}`);
}

async function copyReq(reqId) {
  if (!reqId) return;
  try {
    await navigator.clipboard.writeText(reqId);
  } catch {
    const el = document.createElement('textarea');
    el.value = reqId;
    el.style.position = 'fixed';
    el.style.left = '-9999px';
    document.body.appendChild(el);
    el.select();
    document.execCommand('copy');
    document.body.removeChild(el);
  }
}

function reset() {
  orders.value = [];
  page.value = 1;
  finished.value = false;
  error.value = '';
}

async function loadMore() {
  if (!session.token || finished.value || inFlight.value) return;
  inFlight.value = true;
  loading.value = true;
  try {
    const rows = await request(`/voucher-order/my/detail?current=${page.value}&size=${size.value}`, { token: session.token });
    const arr = Array.isArray(rows) ? rows : [];
    if (arr.length === 0) {
      finished.value = true;
      return;
    }
    orders.value.push(...arr);
    page.value += 1;
    if (arr.length < size.value) finished.value = true;
  } catch (e) {
    finished.value = true;
    error.value = e?.message || String(e);
  } finally {
    loading.value = false;
    inFlight.value = false;
  }
}

function refresh() {
  reset();
  loadMore();
}

onMounted(refresh);
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

.order-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 14px;
  padding: 14px 0;
}

.order-card {
  border-radius: 16px;
  border: 1px solid rgba(0, 0, 0, 0.05);
  background: #fff;
  padding: 14px;
  box-shadow: 0 10px 22px rgba(0, 0, 0, 0.04);
}

.order-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.order-title {
  font-weight: 900;
  color: #111;
  font-size: 15px;
  min-width: 0;
}

.order-sub {
  margin-top: 10px;
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.6);
}

.shop-link {
  cursor: pointer;
  color: #1677ff;
  font-weight: 600;
}

.dot {
  opacity: 0.6;
}

.order-meta {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.pill {
  background: rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.7);
}

.order-actions {
  margin-top: 12px;
  display: flex;
  gap: 10px;
}

.error {
  margin-top: 10px;
  color: #d4380d;
  font-size: 12px;
}
</style>

