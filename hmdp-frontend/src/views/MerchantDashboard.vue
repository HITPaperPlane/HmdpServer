<template>
  <div class="page">
    <section class="hero">
      <div class="hero-left">
        <div class="hero-title">商家中心</div>
        <div class="hero-sub">店铺管理 · 优惠券/秒杀 · 内容营销（富文本 + 图片上传）</div>
      </div>
      <div class="hero-actions">
        <van-button size="small" type="primary" @click="go('/merchant/shops')">店铺管理</van-button>
        <van-button size="small" plain type="primary" @click="go('/merchant/vouchers')">优惠券/秒杀</van-button>
        <van-button size="small" plain type="default" @click="go('/merchant/content')">内容营销</van-button>
      </div>
    </section>

    <section class="card">
      <div class="section-head">
        <div>
          <div class="title">商户资料</div>
          <div class="muted">头像、昵称、简介请在个人中心维护，所有端统一展示</div>
        </div>
        <van-button size="small" plain type="primary" @click="go('/profile')">完善资料</van-button>
      </div>
      <div class="merchant-profile">
        <van-image round width="64px" height="64px" :src="merchantAvatar" />
        <div class="meta">
          <div class="name">{{ session.profile.nickName || '未设置昵称' }}</div>
          <div class="muted">ID {{ session.profile.id || '-' }}</div>
          <div class="muted van-ellipsis">{{ session.profile.introduce || '暂无简介' }}</div>
        </div>
      </div>
    </section>

    <section class="grid-3">
      <div class="mini-card c1">
        <div class="mini-title">开店/编辑</div>
        <div class="mini-sub">支持多图上传，图片自动托管到 `/imgs/**`</div>
        <van-button size="small" type="primary" @click="go('/merchant/shops')">去管理</van-button>
      </div>
      <div class="mini-card c2">
        <div class="mini-title">券与秒杀</div>
        <div class="mini-sub">创建秒杀券会预热 Redis 库存键</div>
        <van-button size="small" type="primary" plain @click="go('/merchant/vouchers')">去配置</van-button>
      </div>
      <div class="mini-card c3">
        <div class="mini-title">内容营销</div>
        <div class="mini-sub">发布笔记会推送粉丝收件箱 `feed:{userId}`</div>
        <van-button size="small" type="primary" plain @click="go('/merchant/content')">去发布</van-button>
      </div>
    </section>

    <section class="card">
      <div class="section-head">
        <div>
          <div class="title">快速搜索店铺</div>
          <div class="muted">不需要记住店铺ID：直接按名称关键字搜索，然后一键进入管理/发券</div>
        </div>
      </div>

      <van-search
          v-model="search.keyword"
          placeholder="输入店铺名称关键词"
          shape="round"
          @search="searchShops"
          @update:model-value="onKeywordChange"
      />

      <div v-if="search.list.length === 0 && !search.loading" style="padding: 10px 0;">
        <van-empty description="暂无结果，试试搜索「咖啡」「烧烤」「火锅」等" />
      </div>

      <van-list v-model:loading="search.loading" :finished="search.finished" finished-text="没有更多了" @load="searchShops">
        <div class="shop-grid">
          <div v-for="s in search.list" :key="s.id" class="shop-card">
            <van-image class="cover" :src="firstImg(s.images)" fit="cover" radius="14" />
            <div class="meta">
              <div class="name">{{ s.name }}</div>
              <div class="muted">ID {{ s.id }} · {{ typeName(s.typeId) }} · 评分 {{ ((s.score || 0) / 10).toFixed(1) }}</div>
              <div class="muted van-ellipsis">{{ s.address }}</div>
              <div class="actions">
                <van-button size="small" plain type="primary" @click="goShopManage(s.id)">管理店铺</van-button>
                <van-button size="small" plain type="default" @click="goVoucherManage(s.id)">发券/秒杀</van-button>
                <van-button size="small" plain type="default" @click="go(`/shops/${s.id}`)">预览C端</van-button>
              </div>
            </div>
          </div>
        </div>
      </van-list>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { request } from '../api/http';
import { resolveImg, splitImages } from '../utils/media';
import { useSessionStore } from '../stores/session';
import defaultAvatar from '../assets/default-avatar.svg';

const router = useRouter();
const session = useSessionStore();

const types = ref([]);
const search = reactive({ keyword: '', list: [], page: 1, loading: false, finished: false });
const merchantAvatar = computed(() => resolveImg(session.profile.icon) || defaultAvatar);

function go(path) {
  router.push(path);
}

function goShopManage(shopId) {
  router.push({ path: '/merchant/shops', query: { load: String(shopId) } });
}

function goVoucherManage(shopId) {
  router.push({ path: '/merchant/vouchers', query: { shopId: String(shopId) } });
}

function firstImg(images) {
  return splitImages(images)[0] || '';
}

function typeName(typeId) {
  const t = types.value.find(x => x.id === typeId);
  return t ? t.name : `类型 ${typeId}`;
}

let timer = null;
function onKeywordChange() {
  if (timer) clearTimeout(timer);
  timer = setTimeout(() => {
    search.list = [];
    search.page = 1;
    search.finished = false;
    if (search.keyword.trim()) searchShops();
  }, 250);
}

async function searchShops() {
  if (search.loading || search.finished) return;
  const kw = search.keyword.trim();
  if (!kw) return;
  search.loading = true;
  try {
    const list = await request(`/shop/of/name?name=${encodeURIComponent(kw)}&current=${search.page}`);
    const rows = Array.isArray(list) ? list : [];
    if (rows.length === 0) {
      search.finished = true;
      return;
    }
    search.list.push(...rows);
    search.page += 1;
    if (rows.length < 10) search.finished = true;
  } catch {
    search.finished = true;
  } finally {
    search.loading = false;
  }
}

onMounted(async () => {
  try {
    const list = await request('/shop-type/list');
    types.value = Array.isArray(list) ? list : [];
  } catch {
    types.value = [];
  }
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
  background: linear-gradient(135deg, #3b82f6 0%, #06b6d4 100%);
  color: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  box-shadow: 0 10px 30px rgba(6, 182, 212, 0.18);
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

.mini-card {
  border-radius: 16px;
  padding: 16px;
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.25);
  box-shadow: 0 10px 22px rgba(0, 0, 0, 0.05);
}

.mini-title {
  font-weight: 900;
  font-size: 16px;
}

.mini-sub {
  margin-top: 6px;
  font-size: 12px;
  opacity: 0.92;
  min-height: 34px;
}

.c1 { background: linear-gradient(135deg, #ff6b6b 0%, #ff8e53 100%); }
.c2 { background: linear-gradient(135deg, #a855f7 0%, #ec4899 100%); }
.c3 { background: linear-gradient(135deg, #10b981 0%, #22c55e 100%); }

.card {
  padding: 14px;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.merchant-profile {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 0 10px;
}
.merchant-profile .meta .name {
  font-weight: 900;
  color: #222;
}

.shop-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(360px, 1fr));
  gap: 12px;
  padding: 12px 0 2px;
}

.shop-card {
  display: grid;
  grid-template-columns: 140px 1fr;
  gap: 12px;
  border-radius: 18px;
  overflow: hidden;
  background: #fff;
  border: 1px solid rgba(0, 0, 0, 0.05);
  box-shadow: 0 10px 22px rgba(0, 0, 0, 0.04);
}

.cover {
  width: 140px;
  height: 120px;
  background: #f5f6f7;
}

.meta {
  padding: 12px 12px;
  min-width: 0;
}

.name {
  font-size: 15px;
  font-weight: 900;
  color: #111;
}

.actions {
  margin-top: 10px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

@media (max-width: 980px) {
  .grid-3 {
    grid-template-columns: 1fr;
  }
  .shop-card {
    grid-template-columns: 1fr;
  }
  .cover {
    width: 100%;
    height: 180px;
  }
}
</style>
