<template>
  <div class="page">
    <div class="card hero">
      <div class="hero-main">
        <div class="hero-title-row">
          <div class="hero-title">{{ shop.name || '店铺详情' }}</div>
          <van-tag type="primary" plain size="small">ID {{ shop.id }}</van-tag>
        </div>
        <div class="hero-sub">
          <span class="pill">{{ typeName || `类型 ${shop.typeId || '-'}` }}</span>
          <span class="pill" v-if="shop.area">{{ shop.area }}</span>
          <span class="pill" v-if="shop.openHours">营业 {{ shop.openHours }}</span>
        </div>
        <div class="hero-metrics">
          <div class="metric">
            <div class="metric-label">评分</div>
            <div class="metric-value">{{ scoreText }}</div>
          </div>
          <div class="metric">
            <div class="metric-label">人均</div>
            <div class="metric-value">¥{{ shop.avgPrice || 0 }}</div>
          </div>
          <div class="metric">
            <div class="metric-label">销量</div>
            <div class="metric-value">{{ shop.sold || 0 }}</div>
          </div>
          <div class="metric">
            <div class="metric-label">评价</div>
            <div class="metric-value">{{ shop.comments || 0 }}</div>
          </div>
        </div>
        <div class="hero-addr">
          <div class="addr-title">地址</div>
          <div class="addr-text">{{ shop.address || '暂无地址' }}</div>
        </div>
      </div>

      <div class="hero-media" v-if="images.length">
        <van-swipe :autoplay="3500" indicator-color="white" class="swipe">
          <van-swipe-item v-for="(img, idx) in images" :key="idx">
            <van-image :src="img" fit="cover" class="swipe-img" />
          </van-swipe-item>
        </van-swipe>
      </div>
      <div class="hero-media empty" v-else>
        <div class="empty-cover">暂无图片</div>
      </div>
    </div>

    <div class="grid-2">
      <div class="card">
        <div class="section-head">
          <div>
            <div class="title">优惠券</div>
            <div class="muted">接口：`GET /voucher/list/{shopId}`，秒杀：`POST /voucher-order/seckill/{voucherId}`</div>
          </div>
          <van-button size="small" plain type="primary" @click="loadVouchers">刷新</van-button>
        </div>

        <div v-if="vouchers.length === 0 && !loading.vouchers" style="padding: 10px 0;">
          <van-empty description="暂无优惠券" />
        </div>

        <div class="voucher-list">
          <div class="voucher-card" v-for="v in vouchers" :key="v.id">
            <div class="voucher-top">
              <div class="voucher-title">{{ v.title }}</div>
              <van-tag :type="v.beginTime ? 'danger' : 'primary'" plain size="mini">
                {{ v.beginTime ? '秒杀券' : '普通券' }}
              </van-tag>
            </div>
            <div class="voucher-sub muted">{{ v.subTitle }}</div>
            <div class="voucher-price">
              <span class="pay">¥{{ v.payValue }}</span>
              <span class="arrow">→</span>
              <span class="actual">抵 ¥{{ v.actualValue }}</span>
            </div>
            <div class="voucher-rule muted">{{ v.rules }}</div>

            <div class="voucher-window muted" v-if="v.beginTime">
              秒杀窗口：{{ v.beginTime }} ~ {{ v.endTime }}
            </div>
            <div class="voucher-rule muted">
              <van-tag size="mini" plain type="primary">{{ renderLimitTag(v) }}</van-tag>
              <span v-if="v.userLimit && v.limitType === 3" class="inline-note">累计限购 {{ v.userLimit }} 件</span>
            </div>

            <div class="voucher-actions">
              <div v-if="v.limitType !== 1" class="qty-box">
                <span class="muted">本次抢购数量</span>
                <van-stepper
                    v-model="purchaseCount[v.id]"
                    min="1"
                    integer
                    :max="maxCount(v)"
                    @change="onCountChange(v.id)"
                />
              </div>
              <van-button
                  v-if="v.beginTime"
                  size="small"
                  type="danger"
                  :loading="getSeckillState(v.id).status === 'PENDING'"
                  :disabled="isSeckillDisabled(v.id)"
                  @click="seckill(v.id)"
              >
                {{ renderSeckillText(v.id) }}
              </van-button>
              <van-button v-else size="small" plain type="primary" disabled>普通券仅展示</van-button>
            </div>
          </div>
        </div>
        <div class="log">{{ log }}</div>
      </div>

      <div class="card">
        <div class="section-head">
          <div>
            <div class="title">快速操作</div>
            <div class="muted">打开探店笔记，或回到店铺列表继续浏览</div>
          </div>
        </div>
        <div class="quick-actions">
          <van-button block type="primary" @click="toBlogs">去看笔记</van-button>
          <van-button block plain type="primary" @click="toHome">返回店铺发现</van-button>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="section-head">
        <div>
          <div class="title">探店笔记</div>
          <div class="muted">接口：`GET /blog/of/shop?shopId=...`（展示该店铺相关笔记）</div>
        </div>
        <van-button size="small" plain type="primary" @click="refreshBlogs" :disabled="!shop.id">刷新</van-button>
      </div>

      <div v-if="blogs.list.length === 0 && !blogs.loading" style="padding: 10px 0;">
        <van-empty description="暂无探店笔记" />
      </div>

      <van-list v-model:loading="blogs.loading" :finished="blogs.finished" finished-text="没有更多了" @load="loadBlogs">
        <div class="blog-grid">
          <article v-for="b in blogs.list" :key="b.id" class="blog-card">
            <div class="blog-head">
              <div class="author">
                <van-image round width="34" height="34" :src="b._authorAvatar || defaultAvatar" />
                <div class="author-meta">
                  <div class="author-name">{{ b.name || '匿名用户' }}</div>
                  <div class="muted">ID {{ b.id }} · {{ b.createTime || '' }}</div>
                </div>
              </div>
              <van-button size="small" plain type="primary" @click="toBlog(b.id)">详情</van-button>
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
                  @click="toBlog(b.id)"
              />
              <div v-if="b._imgs.length > 3" class="more">+{{ b._imgs.length - 3 }}</div>
            </div>

            <div class="blog-snippet" v-html="b._snippet"></div>

            <div class="blog-actions">
              <van-button size="small" plain type="primary" :disabled="!session.token" @click="toggleLike(b)">
                {{ b.isLike ? '已赞' : '点赞' }} {{ b.liked || 0 }}
              </van-button>
            </div>
          </article>
        </div>
      </van-list>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, onBeforeUnmount } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { request } from '../api/http';
import { resolveImg, splitImages } from '../utils/media';
import { sanitizeHtml } from '../utils/sanitize';
import { useSessionStore } from '../stores/session';
import defaultAvatar from '../assets/default-avatar.svg';
import { showConfirmDialog, showToast } from 'vant';

const route = useRoute();
const router = useRouter();
const session = useSessionStore();

const shop = reactive({});
const images = ref([]);
const vouchers = ref([]);
const types = ref([]);
const log = ref('准备就绪');
const seckillStates = reactive({});
const purchaseCount = reactive({});
const loading = reactive({ vouchers: false });
const blogs = reactive({ list: [], page: 1, loading: false, finished: false, inFlight: false });

const REQ_STORE_PREFIX = 'hmdp:seckill:req:';
const REQ_TTL_MS = 30 * 60 * 1000;

function reqStorageKey(voucherId) {
  return `${REQ_STORE_PREFIX}${voucherId}`;
}

function loadStoredReq(voucherId) {
  try {
    const raw = sessionStorage.getItem(reqStorageKey(voucherId));
    if (!raw) return null;
    const obj = JSON.parse(raw);
    if (!obj?.reqId || !obj?.ts) return null;
    if (Date.now() - Number(obj.ts) > REQ_TTL_MS) {
      sessionStorage.removeItem(reqStorageKey(voucherId));
      return null;
    }
    return obj;
  } catch {
    try {
      sessionStorage.removeItem(reqStorageKey(voucherId));
    } catch {
      // ignore
    }
    return null;
  }
}

function saveStoredReq(voucherId, reqId, count) {
  try {
    sessionStorage.setItem(reqStorageKey(voucherId), JSON.stringify({ reqId, count, ts: Date.now() }));
  } catch {
    // ignore
  }
}

function clearStoredReq(voucherId) {
  try {
    sessionStorage.removeItem(reqStorageKey(voucherId));
  } catch {
    // ignore
  }
}

const scoreText = computed(() => `${((shop.score || 0) / 10).toFixed(1)}`);
const typeName = computed(() => types.value.find(t => t.id === shop.typeId)?.name || '');

async function loadTypes() {
  try {
    const list = await request('/shop-type/list');
    types.value = Array.isArray(list) ? list : [];
  } catch {
    types.value = [];
  }
}

async function loadShop() {
  const id = route.params.id;
  const data = await request(`/shop/${id}`);
  Object.assign(shop, data || {});
  images.value = splitImages(shop.images);
}

function normalizeBlog(b) {
  const imgs = splitImages(b.images);
  const authorAvatar = resolveImg(b.icon);
  const snippet = sanitizeHtml(b.content || '');
  return { ...b, _imgs: imgs, _authorAvatar: authorAvatar, _snippet: snippet };
}

async function loadBlogs() {
  if (!shop.id) {
    blogs.loading = false;
    return;
  }
  if (blogs.finished || blogs.inFlight) return;
  blogs.inFlight = true;
  blogs.loading = true;
  try {
    const list = await request(`/blog/of/shop?shopId=${shop.id}&current=${blogs.page}`, { token: session.token || undefined });
    const rows = Array.isArray(list) ? list : [];
    if (rows.length === 0) {
      blogs.finished = true;
      return;
    }
    blogs.list.push(...rows.map(normalizeBlog));
    blogs.page += 1;
    if (rows.length < 10) blogs.finished = true;
  } catch (e) {
    blogs.finished = true;
    log.value = e?.message || '加载笔记失败';
  } finally {
    blogs.loading = false;
    blogs.inFlight = false;
  }
}

function refreshBlogs() {
  blogs.list = [];
  blogs.page = 1;
  blogs.finished = false;
  blogs.loading = false;
  blogs.inFlight = false;
  loadBlogs();
}

async function loadVouchers() {
  loading.vouchers = true;
  try {
    const list = await request(`/voucher/list/${shop.id}`);
    vouchers.value = Array.isArray(list) ? list : [];
    vouchers.value.forEach(v => {
      if (!purchaseCount[v.id]) {
        purchaseCount[v.id] = 1;
      }
    });
    // 恢复“一人多单/累计限购”的待确认 reqId（避免刷新丢失）
    vouchers.value.forEach(v => {
      if (!v?.beginTime) return;
      const limitType = v.limitType || 1;
      if (limitType === 1) return;
      const stored = loadStoredReq(v.id);
      if (!stored) return;
      if (stored.count && Number(stored.count) > 0) {
        purchaseCount[v.id] = Number(stored.count);
      }
      const state = getSeckillState(v.id);
      state.reqId = stored.reqId;
    });
    log.value = '券列表已刷新';
  } catch (e) {
    log.value = e?.message || '加载失败';
  } finally {
    loading.vouchers = false;
  }
}

function getSeckillState(voucherId) {
  if (!seckillStates[voucherId]) {
    seckillStates[voucherId] = { status: 'IDLE', reqId: '', startedAt: 0, timer: null, reason: '' };
  }
  return seckillStates[voucherId];
}

function renderSeckillText(voucherId) {
  const state = getSeckillState(voucherId);
  switch (state.status) {
    case 'PENDING':
      return '正在排队...';
    case 'SUCCESS':
      return '抢购成功';
    case 'FAILED':
      if (state.reason === 'LIMIT') return '限购已满';
      if (state.reason === 'STOCK') return '库存不足';
      return '抢购失败';
    case 'TIMEOUT':
      return '超时，点此重试';
    default:
      return '立即秒杀';
  }
}

function renderLimitTag(voucher) {
  const type = voucher.limitType || 1;
  if (type === 1) return '一人一单';
  if (type === 3) return '累计限购';
  return '一人多单';
}

function maxCount(voucher) {
  if (voucher.limitType === 3 && voucher.userLimit) {
    return Number(voucher.userLimit) || 1;
  }
  return Math.max(1, voucher.stock || 10);
}

function isSeckillDisabled(voucherId) {
  const state = getSeckillState(voucherId);
  if (state.status === 'PENDING') return true;
  if (state.status === 'FAILED' && state.reason === 'STOCK') return true;
  return false;
}

function resetSeckillState(state, keepReq = false) {
  if (state.timer) clearTimeout(state.timer);
  state.timer = null;
  state.startedAt = 0;
  state.reason = '';
  state.status = 'IDLE';
  if (!keepReq) state.reqId = '';
}

function currentCount(voucherId) {
  const val = purchaseCount[voucherId];
  if (!val || val < 1) {
    purchaseCount[voucherId] = 1;
    return 1;
  }
  return val;
}

function onCountChange(voucherId) {
  purchaseCount[voucherId] = currentCount(voucherId);
  // 切换数量后属于新的购买意图：清理旧 reqId，避免“同一 reqId 不同 count”导致幂等语义混乱
  clearStoredReq(voucherId);
  resetSeckillState(getSeckillState(voucherId), false);
}

function nextReqId(voucherId) {
  return `REQ_${voucherId}_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`;
}

async function startPay(orderId) {
  if (!session.token || !orderId) return;
  try {
    const returnUrl = `${window.location.origin}/pay/result`;
    const resp = await request(`/voucher-order/pay-url?orderId=${orderId}&returnUrl=${encodeURIComponent(returnUrl)}`, { token: session.token });
    const payUrl = resp?.payUrl || resp;
    if (!payUrl) throw new Error('支付链接为空');
    const win = window.open(payUrl, '_blank');
    if (!win) window.location.href = payUrl;
    showToast('已打开支付页面');
  } catch (e) {
    showToast(e?.message || '获取支付链接失败');
  }
}

async function promptPay(orderId) {
  if (!orderId) return;
  try {
    await showConfirmDialog({
      title: '抢购成功',
      message: `订单号 ${orderId}，是否立即支付？`,
      confirmButtonText: '去支付',
      cancelButtonText: '稍后'
    });
    await startPay(orderId);
  } catch {
    // cancelled
  }
}

async function seckill(voucherId) {
  if (!session.token) {
    log.value = '请先登录再秒杀';
    router.push('/login');
    return;
  }
  const state = getSeckillState(voucherId);
  if (state.status === 'PENDING') {
    return;
  }
  const voucher = vouchers.value.find(v => v.id === voucherId) || {};
  const limitType = voucher.limitType || 1;
  const buyCount = currentCount(voucherId);
  if (limitType === 3 && voucher.userLimit && buyCount > voucher.userLimit) {
    log.value = `最多可抢 ${voucher.userLimit} 件`;
    return;
  }
  // 非一人一单需要预先申请 reqId
  if (limitType !== 1 && !state.reqId) {
    const stored = loadStoredReq(voucherId);
    if (stored?.reqId) {
      state.reqId = stored.reqId;
      if (stored.count && Number(stored.count) !== buyCount) {
        state.reqId = '';
        clearStoredReq(voucherId);
      }
    }
  }
  if (limitType !== 1 && !state.reqId) {
    try {
      state.reqId = await request(`/voucher-order/req/${voucherId}?count=${buyCount}`, { token: session.token });
      saveStoredReq(voucherId, state.reqId, buyCount);
    } catch (e) {
      log.value = e?.message || '获取请求号失败';
      return;
    }
  }
  if (limitType === 1 && !state.reqId) {
    // 后端会按规则生成，这里仅用于显示状态
    state.reqId = nextReqId(voucherId);
  }
  try {
    const respId = await request(`/voucher-order/seckill/${voucherId}?reqId=${encodeURIComponent(state.reqId)}&count=${buyCount}`, { method: 'POST', token: session.token });
    if (respId) {
      state.reqId = respId;
      if (limitType !== 1) {
        saveStoredReq(voucherId, respId, buyCount);
      }
    }
    state.status = 'PENDING';
    state.startedAt = Date.now();
    log.value = `已提交秒杀，ReqId=${state.reqId}，数量=${buyCount}`;
    pollStatus(voucherId);
  } catch (e) {
    const msg = e?.message || '秒杀失败';
    state.status = 'FAILED';
    state.reason = msg.includes('库存') ? 'STOCK' : (msg.includes('限购') ? 'LIMIT' : 'ERROR');
    log.value = msg;
  }
}

async function pollStatus(voucherId) {
  const state = getSeckillState(voucherId);
  if (!state.reqId) return;
  const voucher = vouchers.value.find(v => v.id === voucherId) || {};
  const limitType = voucher.limitType || 1;
  try {
    const resp = await request(`/voucher-order/status?reqId=${encodeURIComponent(state.reqId)}`, { token: session.token });
    const status = (resp?.status || '').toUpperCase();
    if (status === 'SUCCESS') {
      state.status = 'SUCCESS';
      const orderId = resp?.orderId;
      log.value = orderId ? `抢购成功，订单号 ${orderId}` : '抢购成功';
      if (limitType !== 1) clearStoredReq(voucherId);
      if (orderId) promptPay(orderId);
      state.timer = setTimeout(() => resetSeckillState(state), 1500);
      return;
    }
    if (status === 'FAILED') {
      state.status = 'FAILED';
      state.reason = resp?.reason || '';
      if (!state.reason && resp?.voucherId && resp?.orderId == null) {
        state.reason = 'STOCK';
      }
      log.value = resp?.reason ? `失败：${resp.reason}` : '秒杀失败';
      if (limitType !== 1) clearStoredReq(voucherId);
      state.timer = setTimeout(() => resetSeckillState(state), 1500);
      return;
    }
    const elapsed = Date.now() - state.startedAt;
    if (status === 'NOT_FOUND' && elapsed > 5000) {
      state.status = 'TIMEOUT';
      log.value = '请求超时，可重试（会复用同一ID）';
      return;
    }
    state.timer = setTimeout(() => pollStatus(voucherId), 1000);
  } catch (e) {
    const elapsed = Date.now() - state.startedAt;
    if (elapsed > 5000) {
      state.status = 'TIMEOUT';
      log.value = '请求超时，可重试';
      return;
    }
    state.timer = setTimeout(() => pollStatus(voucherId), 1200);
  }
}

function toBlogs() {
  router.push('/blogs');
}

function toBlog(id) {
  router.push(`/blogs/${id}`);
}

function toHome() {
  router.push('/');
}

async function toggleLike(b) {
  if (!session.token) return;
  try {
    await request(`/blog/like/${b.id}`, { method: 'PUT', token: session.token });
    const delta = b.isLike ? -1 : 1;
    b.isLike = !b.isLike;
    b.liked = (b.liked || 0) + delta;
  } catch (e) {
    log.value = e?.message || '操作失败';
  }
}

onMounted(async () => {
  await loadTypes();
  await loadShop();
  await loadVouchers();
  await loadBlogs();
});

onBeforeUnmount(() => {
  Object.values(seckillStates).forEach((s) => {
    if (s.timer) clearTimeout(s.timer);
  });
});
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.hero {
  padding: 0;
  overflow: hidden;
  display: grid;
  grid-template-columns: 1fr 460px;
  min-height: 320px;
}

.hero-main {
  padding: 22px;
}

.hero-title-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.hero-title {
  font-size: 22px;
  font-weight: 900;
  letter-spacing: -0.2px;
}

.hero-sub {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.hero-metrics {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}

.metric {
  border-radius: 14px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  padding: 12px 12px;
  background: linear-gradient(180deg, #ffffff, #fafafa);
}

.metric-label {
  font-size: 12px;
  color: #999;
}

.metric-value {
  margin-top: 6px;
  font-size: 18px;
  font-weight: 900;
  color: #222;
}

.hero-addr {
  margin-top: 16px;
  border-top: 1px dashed rgba(0, 0, 0, 0.08);
  padding-top: 14px;
}

.addr-title {
  font-size: 12px;
  color: #999;
}

.addr-text {
  margin-top: 6px;
  font-size: 13px;
  color: #333;
  line-height: 1.6;
}

.hero-media {
  background: linear-gradient(135deg, #ff6b6b 0%, #ff8e53 100%);
  display: flex;
  align-items: stretch;
  justify-content: stretch;
}

.hero-media.empty {
  padding: 16px;
}

.empty-cover {
  width: 100%;
  border-radius: 18px;
  border: 1px dashed rgba(255, 255, 255, 0.45);
  color: rgba(255, 255, 255, 0.9);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
}

.swipe {
  width: 100%;
  height: 100%;
}

.swipe-img {
  width: 100%;
  height: 100%;
}

.grid-2 {
  display: grid;
  grid-template-columns: 1fr 360px;
  gap: 18px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.voucher-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 12px;
}

.voucher-card {
  border: 1px solid rgba(0, 0, 0, 0.05);
  border-radius: 14px;
  padding: 14px;
  background: #fff;
  box-shadow: 0 10px 22px rgba(0, 0, 0, 0.04);
}

.voucher-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.voucher-title {
  font-weight: 900;
  color: #222;
  font-size: 14px;
}

.voucher-sub {
  margin-top: 4px;
}

.voucher-price {
  margin-top: 10px;
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.voucher-price .pay {
  font-size: 18px;
  font-weight: 900;
  color: #ff6b6b;
}

.voucher-price .arrow {
  color: #bbb;
}

.voucher-price .actual {
  font-size: 13px;
  font-weight: 700;
  color: #222;
}

.voucher-rule {
  margin-top: 8px;
  line-height: 1.6;
}
.voucher-rule .inline-note {
  margin-left: 8px;
}

.voucher-window {
  margin-top: 8px;
}

.voucher-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 10px;
}

.qty-box {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #666;
}

.quick-actions {
  display: grid;
  gap: 10px;
}

.blog-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 14px;
  padding: 14px 0 2px;
}

.blog-card {
  border-radius: 16px;
  border: 1px solid rgba(0, 0, 0, 0.05);
  background: #fff;
  padding: 14px;
  box-shadow: 0 10px 22px rgba(0, 0, 0, 0.04);
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
  font-weight: 900;
  color: #222;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.blog-title {
  margin-top: 12px;
  font-size: 15px;
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
  position: relative;
}

.blog-actions {
  margin-top: 12px;
  display: flex;
  gap: 10px;
}

@media (max-width: 980px) {
  .hero {
    grid-template-columns: 1fr;
  }
  .grid-2 {
    grid-template-columns: 1fr;
  }
  .hero-metrics {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
