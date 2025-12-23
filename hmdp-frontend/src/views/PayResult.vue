<template>
  <div class="page">
    <section class="hero">
      <div class="hero-left">
        <div class="hero-title">支付结果</div>
        <div class="hero-sub">订单号 {{ orderIdText }}</div>
      </div>
      <div class="hero-actions">
        <van-button size="small" plain type="primary" :loading="loading" @click="refresh">刷新</van-button>
      </div>
    </section>

    <section class="card">
      <div v-if="error" class="error">{{ error }}</div>
      <div v-else-if="!order && !loading" style="padding: 18px 0;">
        <van-empty description="暂无订单信息" />
      </div>
      <div v-else-if="order" class="result">
        <div class="row">
          <div class="label">状态</div>
          <div class="value">{{ statusLabel(order.status) }}</div>
        </div>
        <div class="row">
          <div class="label">支付方式</div>
          <div class="value">{{ payLabel(order.payType) }}</div>
        </div>
        <div class="row">
          <div class="label">下单时间</div>
          <div class="value">{{ order.createTime || '-' }}</div>
        </div>
        <div class="row">
          <div class="label">支付时间</div>
          <div class="value">{{ order.payTime || '-' }}</div>
        </div>
        <div class="row" v-if="order.voucherTitle">
          <div class="label">商品</div>
          <div class="value">{{ order.voucherTitle }}</div>
        </div>

        <div class="actions">
          <van-button size="small" plain type="primary" @click="goOrders">去订单页</van-button>
          <van-button size="small" plain type="default" v-if="order.shopId" @click="goShop(order.shopId)">去店铺</van-button>
        </div>

        <div v-if="Number(order.status) === 1" class="hint">
          若你已完成支付但此处仍显示未支付，可能是回调同步中，可稍等片刻后刷新。
        </div>
      </div>
      <div v-if="loading && !order" style="padding: 18px 0;">
        <van-loading size="24px" vertical>加载中…</van-loading>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { request } from '../api/http';
import { useSessionStore } from '../stores/session';

const route = useRoute();
const router = useRouter();
const session = useSessionStore();

const loading = ref(false);
const error = ref('');
const order = ref(null);

let timer = null;
let attempts = 0;
const MAX_ATTEMPTS = 10;

function pick(v) {
  return Array.isArray(v) ? v[0] : v;
}

const orderId = computed(() => {
  const raw = pick(route.query.orderId) || pick(route.query.out_trade_no) || pick(route.query.outTradeNo);
  const s = raw == null ? '' : String(raw).trim();
  return /^\d+$/.test(s) ? s : null;
});

const orderIdText = computed(() => orderId.value || '-');

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

async function loadOrder() {
  if (!session.token || !orderId.value) return;
  loading.value = true;
  error.value = '';
  try {
    order.value = await request(`/voucher-order/detail/${orderId.value}`, { token: session.token });
  } catch (e) {
    error.value = e?.message || String(e);
  } finally {
    loading.value = false;
  }
}

function schedulePoll() {
  if (timer) clearTimeout(timer);
  if (attempts >= MAX_ATTEMPTS) return;
  if (!order.value || Number(order.value.status) !== 1) return;
  attempts += 1;
  timer = setTimeout(async () => {
    await loadOrder();
    schedulePoll();
  }, 2000);
}

async function refresh() {
  attempts = 0;
  await loadOrder();
  schedulePoll();
}

function goOrders() {
  router.push('/orders');
}

function goShop(shopId) {
  router.push(`/shops/${shopId}`);
}

onMounted(refresh);
onBeforeUnmount(() => {
  if (timer) clearTimeout(timer);
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
  background: linear-gradient(135deg, #1677ff 0%, #40a9ff 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  box-shadow: 0 10px 30px rgba(22, 119, 255, 0.18);
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

.result {
  padding: 10px 0;
}

.row {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
}

.label {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.55);
}

.value {
  font-size: 13px;
  font-weight: 700;
  color: rgba(0, 0, 0, 0.82);
  text-align: right;
  word-break: break-word;
}

.actions {
  display: flex;
  gap: 10px;
  margin-top: 14px;
}

.hint {
  margin-top: 12px;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.55);
}

.error {
  margin-top: 10px;
  color: #d4380d;
  font-size: 12px;
}
</style>
