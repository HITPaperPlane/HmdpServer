<template>
  <div class="page">
    <section class="hero">
      <div class="hero-left">
        <div class="hero-title">发现附近好店</div>
        <div class="hero-sub">按类型筛选、搜索店铺、查看优惠券与探店笔记</div>

        <div class="hero-search">
          <van-search
              v-model="keyword"
              shape="round"
              placeholder="搜索店铺名称（支持模糊）"
              @search="doSearch"
              @clear="clearSearch"
              clearable
          />
        </div>

        <div class="type-strip">
          <button
              v-for="t in types"
              :key="t.id"
              class="type-chip"
              :class="{ active: mode === 'type' && activeTypeId === t.id }"
              @click="selectType(t.id)"
          >
            <span class="dot" />
            {{ t.name }}
          </button>
        </div>
      </div>

      <div class="hero-right">
        <div class="metric">
          <div class="metric-label">当前模式</div>
          <div class="metric-value">{{ modeLabel }}</div>
        </div>
        <div class="metric">
          <div class="metric-label">已加载店铺</div>
          <div class="metric-value">{{ shops.length }}</div>
        </div>
        <div class="metric">
          <div class="metric-label">城市</div>
          <div class="metric-value">上海</div>
        </div>
      </div>
    </section>

    <section class="content">
      <div class="card list-card">
        <div class="card-head">
          <div>
            <div class="title">店铺列表</div>
            <div class="muted">
              <template v-if="mode === 'search'">关键词：{{ keyword }}</template>
              <template v-else>类型：{{ currentTypeName }}</template>
            </div>
          </div>
          <div class="row">
            <van-button size="small" plain type="primary" @click="reload">刷新</van-button>
          </div>
        </div>

        <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="loadMore">
          <div v-if="shops.length === 0 && !loading" style="padding: 20px 0;">
            <van-empty description="暂无数据" />
          </div>

          <div class="shop-grid">
            <div v-for="s in shops" :key="s.id" class="shop-card" @click="openShop(s)">
              <van-image class="shop-cover" :src="s._cover" fit="cover" radius="12px" />
              <div class="shop-body">
                <div class="shop-top">
                  <div class="shop-name">{{ s.name }}</div>
                  <van-tag type="primary" plain size="mini">ID {{ s.id }}</van-tag>
                </div>
                <div class="shop-meta">
                  <van-rate :model-value="s._score" readonly allow-half size="14" color="#FFD21E" />
                  <span class="score">{{ s._score.toFixed(1) }}</span>
                  <span class="dot-sep">·</span>
                  <span class="price">¥{{ s.avgPrice || 0 }}/人</span>
                </div>
                <div class="shop-tags">
                  <van-tag v-if="s.comments >= 100" type="danger" plain size="mini">人气</van-tag>
                  <van-tag v-if="s.sold >= 1000" type="success" plain size="mini">热卖</van-tag>
                  <span class="area">{{ s.area || '未知商圈' }}</span>
                </div>
                <div class="shop-desc">
                  <span class="addr">{{ s.address || '暂无地址' }}</span>
                  <span class="open" v-if="s.openHours">营业：{{ s.openHours }}</span>
                </div>
              </div>
            </div>
          </div>
        </van-list>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { request } from '../api/http';
import { splitImages } from '../utils/media';

const router = useRouter();

const types = ref([]);
const activeTypeId = ref(null);
const keyword = ref('');
const mode = ref('type'); // type | search

const shops = ref([]);
const current = ref(1);
const loading = ref(false);
const finished = ref(false);

const modeLabel = computed(() => (mode.value === 'search' ? '搜索' : '按类型'));
const currentTypeName = computed(() => types.value.find(t => t.id === activeTypeId.value)?.name || '-');

function normalizeShop(s) {
  const imgs = splitImages(s.images);
  const cover = imgs[0] || '';
  return { ...s, _imgs: imgs, _cover: cover, _score: ((s.score || 0) / 10) };
}

async function loadTypes() {
  const list = await request('/shop-type/list');
  types.value = Array.isArray(list) ? list : [];
  if (!activeTypeId.value && types.value.length) {
    activeTypeId.value = types.value[0].id;
  }
}

function resetList() {
  shops.value = [];
  current.value = 1;
  finished.value = false;
}

async function loadMore() {
  if (loading.value || finished.value) return;
  loading.value = true;
  try {
    let list = [];
    if (mode.value === 'search' && keyword.value.trim()) {
      list = await request(`/shop/of/name?name=${encodeURIComponent(keyword.value.trim())}&current=${current.value}`);
    } else {
      if (!activeTypeId.value) {
        finished.value = true;
        return;
      }
      list = await request(`/shop/of/type?typeId=${activeTypeId.value}&current=${current.value}`);
    }
    const rows = Array.isArray(list) ? list : [];
    if (rows.length === 0) {
      finished.value = true;
      return;
    }
    shops.value.push(...rows.map(normalizeShop));
    current.value += 1;
  } catch (e) {
    finished.value = true;
  } finally {
    loading.value = false;
  }
}

function selectType(typeId) {
  mode.value = 'type';
  keyword.value = '';
  activeTypeId.value = typeId;
  resetList();
  loadMore();
}

function doSearch() {
  if (!keyword.value.trim()) return;
  mode.value = 'search';
  resetList();
  loadMore();
}

function clearSearch() {
  if (mode.value !== 'search') return;
  mode.value = 'type';
  keyword.value = '';
  resetList();
  loadMore();
}

function reload() {
  resetList();
  loadMore();
}

function openShop(shop) {
  router.push(`/shops/${shop.id}`);
}

onMounted(async () => {
  await loadTypes();
  await loadMore();
});
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.hero {
  border-radius: 18px;
  padding: 22px;
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 18px;
  background: linear-gradient(135deg, #ff6b6b 0%, #ff8e53 100%);
  color: #fff;
  box-shadow: 0 10px 30px rgba(255, 107, 107, 0.2);
}

.hero-title {
  font-size: 22px;
  font-weight: 800;
  letter-spacing: -0.2px;
}

.hero-sub {
  margin-top: 6px;
  opacity: 0.9;
  font-size: 13px;
}

.hero-search {
  margin-top: 14px;
  background: rgba(255, 255, 255, 0.16);
  border-radius: 14px;
  padding: 6px;
}

.type-strip {
  margin-top: 14px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.type-chip {
  border: 1px solid rgba(255, 255, 255, 0.35);
  background: rgba(255, 255, 255, 0.16);
  color: #fff;
  padding: 8px 12px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.type-chip:hover {
  background: rgba(255, 255, 255, 0.22);
}

.type-chip.active {
  background: #fff;
  border-color: #fff;
  color: #ff6b6b;
}

.type-chip .dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.9);
}

.type-chip.active .dot {
  background: #ff6b6b;
}

.hero-right {
  display: grid;
  gap: 12px;
  align-content: start;
}

.metric {
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 16px;
  padding: 14px 14px;
}

.metric-label {
  font-size: 12px;
  opacity: 0.9;
}

.metric-value {
  margin-top: 6px;
  font-size: 18px;
  font-weight: 800;
}

.content {
  display: grid;
}

.list-card {
  padding: 18px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.shop-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 14px;
}

.shop-card {
  cursor: pointer;
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid rgba(0, 0, 0, 0.04);
  box-shadow: 0 10px 22px rgba(0, 0, 0, 0.04);
  transition: transform 0.15s, box-shadow 0.15s;
  background: #fff;
}

.shop-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 14px 30px rgba(0, 0, 0, 0.08);
}

.shop-cover {
  width: 100%;
  height: 160px;
  background: #f5f6f7;
}

.shop-body {
  padding: 14px 14px 16px;
}

.shop-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.shop-name {
  font-weight: 800;
  font-size: 15px;
  color: #222;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.shop-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  color: #666;
  font-size: 12px;
}

.score {
  color: #ff9c00;
  font-weight: 700;
}

.dot-sep {
  opacity: 0.6;
}

.price {
  font-weight: 600;
}

.shop-tags {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}

.area {
  margin-left: auto;
  font-size: 12px;
  color: #999;
}

.shop-desc {
  margin-top: 10px;
  display: grid;
  gap: 6px;
  color: #999;
  font-size: 12px;
}

.open {
  color: #888;
}

@media (max-width: 980px) {
  .hero {
    grid-template-columns: 1fr;
  }
}
</style>

