<template>
  <div class="home-wrapper">
    <section class="hero-section">
      <div class="hero-content">
        <h1>发现城市好生活</h1>
        <p>探索附近的宝藏店铺，分享你的真实体验</p>
        <div class="search-box">
          <input v-model="search" placeholder="搜索美食、咖啡、好店..." @keyup.enter="searchByName" />
          <button @click="searchByName">搜索</button>
        </div>
      </div>
    </section>

    <div class="main-container">

      <section class="category-section">
        <h3 class="section-title">热门分类</h3>
        <div class="category-list">
          <div
              class="cat-item"
              v-for="(t, idx) in types"
              :key="t.id"
              :class="{ active: idx === typeIndex }"
              @click="changeType(idx)"
          >
            <div class="cat-icon">{{ t.icon || '🛍️' }}</div>
            <span>{{ t.name }}</span>
          </div>
        </div>
      </section>

      <section class="shop-section">
        <div class="section-header">
          <h3 class="section-title">推荐好店</h3>
          <div class="actions">
            <button class="text-btn" @click="geoSearch">📍 查找附近</button>
          </div>
        </div>

        <div class="shop-grid">
          <div class="shop-card" v-for="shop in shops" :key="shop.id">
            <div class="shop-img" :style="{ backgroundImage: `url(${shop.images || 'https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?ixlib=rb-1.2.1&auto=format&fit=crop&w=500&q=60'})` }"></div>
            <div class="shop-info">
              <h4 class="shop-name">{{ shop.name }}</h4>
              <div class="shop-meta">
                <span class="score">⭐ {{ (shop.score / 10).toFixed(1) }}</span>
                <span class="price">¥{{ shop.avgPrice || '-' }}/人</span>
              </div>
              <div class="shop-address">{{ shop.address || '暂无地址信息' }}</div>
              <div class="shop-tags">
                <span class="tag">{{ shop.area || '商圈' }}</span>
                <span class="tag blue">{{ shop.comments }} 评价</span>
              </div>
              <button class="action-btn" @click="loadVouchers(shop)">抢优惠</button>
            </div>

            <div class="voucher-panel" v-if="activeShop?.id === shop.id">
              <div v-if="loadingVoucher" class="loading-text">加载中...</div>
              <ul v-else-if="vouchers.length > 0">
                <li v-for="v in vouchers" :key="v.id" class="voucher-item">
                  <div class="v-left">
                    <div class="v-val">{{ v.actualValue }}元</div>
                    <div class="v-cond">满{{ v.payValue }}可用</div>
                  </div>
                  <div class="v-right">
                    <div class="v-title">{{ v.title }}</div>
                    <button class="seckill-btn" @click="seckill(v.id)">
                      秒杀
                    </button>
                  </div>
                </li>
              </ul>
              <div v-else class="empty-text">暂无优惠券</div>
              <div v-if="logMsg" class="log-text">{{ logMsg }}</div>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { request } from '../api/http';
import { useSessionStore } from '../stores/session';

const session = useSessionStore();
const types = ref([]);
const typeIndex = ref(0);
const shops = ref([]);
const search = ref('');
const activeShop = ref(null);
const vouchers = ref([]);
const loadingVoucher = ref(false);
const logMsg = ref('');

onMounted(async () => {
  await loadTypes();
  await loadHotShops();
});

async function loadTypes() {
  try {
    const list = await request('/shop-type/list');
    // 给分类加点emoji图标（如果是纯文字接口）
    const icons = ['🍔','🏨','🎟️','💇','🎤','💆','🏋️','🏙️','🍹','🍱'];
    types.value = list.map((item, i) => ({ ...item, icon: icons[i % icons.length] }));
  } catch (e) {
    console.error(e);
  }
}

async function loadHotShops() {
  if (!types.value.length) return;
  const typeId = types.value[typeIndex.value]?.id;
  try {
    shops.value = await request(`/shop/of/type?typeId=${typeId}&current=1`);
  } catch (e) {
    console.error(e);
  }
}

async function changeType(idx) {
  typeIndex.value = idx;
  loadHotShops();
}

async function searchByName() {
  if(!search.value) return loadHotShops();
  try {
    shops.value = await request(`/shop/of/name?name=${encodeURIComponent(search.value)}&current=1`);
  } catch (e) {
    console.error(e);
  }
}

async function geoSearch() {
  if (!types.value.length) return;
  const typeId = types.value[typeIndex.value]?.id;
  if(!navigator.geolocation) return alert('浏览器不支持定位');

  navigator.geolocation.getCurrentPosition(
      async pos => {
        const { longitude, latitude } = pos.coords;
        shops.value = await request(`/shop/of/type?typeId=${typeId}&current=1&x=${longitude}&y=${latitude}`);
      },
      () => alert('无法获取定位，请确保开启权限')
  );
}

async function loadVouchers(shop) {
  if (activeShop.value?.id === shop.id) {
    activeShop.value = null; // 收起
    return;
  }
  activeShop.value = shop;
  vouchers.value = [];
  loadingVoucher.value = true;
  logMsg.value = '';
  try {
    vouchers.value = await request(`/voucher/list/${shop.id}`);
  } catch (e) {
    logMsg.value = e.message;
  } finally {
    loadingVoucher.value = false;
  }
}

async function seckill(id) {
  if (!session.token) {
    alert('请先登录后抢购！点击右上角登录');
    return;
  }
  try {
    const orderId = await request(`/voucher-order/seckill/${id}`, { method: 'POST', token: session.token });
    logMsg.value = `抢购成功！订单号: ${orderId}，请前往订单页支付`;
  } catch (e) {
    logMsg.value = '抢购失败：' + e.message;
  }
}
</script>

<style scoped>
.home-wrapper { background: #f9f9f9; min-height: 100vh; padding-bottom: 40px; }

/* Hero Section */
.hero-section {
  background: linear-gradient(135deg, #2b32b2 0%, #1488cc 100%);
  color: white; padding: 60px 20px; text-align: center;
}
.hero-content h1 { font-size: 36px; margin-bottom: 10px; font-weight: 800; }
.hero-content p { font-size: 16px; opacity: 0.9; margin-bottom: 30px; }
.search-box {
  max-width: 600px; margin: 0 auto; display: flex; background: white; padding: 5px; border-radius: 50px;
  box-shadow: 0 10px 25px rgba(0,0,0,0.1);
}
.search-box input {
  flex: 1; border: none; padding: 12px 20px; font-size: 16px; outline: none; border-radius: 50px 0 0 50px;
}
.search-box button {
  background: #f63; color: white; border: none; padding: 0 30px; border-radius: 50px; font-weight: bold; cursor: pointer; transition: background 0.2s;
}
.search-box button:hover { background: #e55; }

.main-container { max-width: 1200px; margin: -30px auto 0; padding: 0 20px; position: relative; z-index: 2; }

/* Categories */
.category-section { background: white; border-radius: 12px; padding: 20px; box-shadow: 0 4px 12px rgba(0,0,0,0.05); margin-bottom: 24px; }
.section-title { font-size: 18px; font-weight: 700; color: #333; margin-bottom: 16px; border-left: 4px solid #f63; padding-left: 10px; }
.category-list { display: flex; gap: 10px; overflow-x: auto; padding-bottom: 10px; }
.cat-item {
  display: flex; flex-direction: column; align-items: center; gap: 8px; min-width: 80px; cursor: pointer; padding: 10px; border-radius: 8px; transition: background 0.2s;
}
.cat-item:hover { background: #f5f5f5; }
.cat-item.active { background: #fff0e6; }
.cat-item.active span { color: #f63; font-weight: bold; }
.cat-icon { font-size: 24px; background: #f0f2f5; width: 48px; height: 48px; display: flex; align-items: center; justify-content: center; border-radius: 50%; }

/* Shops */
.shop-section .section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.text-btn { background: none; border: none; color: #666; cursor: pointer; font-size: 14px; }
.text-btn:hover { color: #f63; }

.shop-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 20px; }
.shop-card { background: white; border-radius: 12px; overflow: hidden; box-shadow: 0 2px 8px rgba(0,0,0,0.06); transition: transform 0.2s; }
.shop-card:hover { transform: translateY(-4px); box-shadow: 0 8px 16px rgba(0,0,0,0.1); }
.shop-img { height: 160px; background-size: cover; background-position: center; }
.shop-info { padding: 16px; }
.shop-name { font-size: 17px; font-weight: bold; color: #333; margin-bottom: 8px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.shop-meta { display: flex; justify-content: space-between; font-size: 13px; color: #666; margin-bottom: 8px; }
.score { color: #f63; font-weight: bold; }
.shop-address { font-size: 12px; color: #999; margin-bottom: 12px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.shop-tags { display: flex; gap: 6px; margin-bottom: 12px; }
.tag { font-size: 10px; padding: 2px 6px; border-radius: 4px; background: #f5f5f5; color: #666; }
.tag.blue { background: #e6f7ff; color: #1890ff; }
.action-btn { width: 100%; padding: 8px; background: #f0f2f5; border: none; border-radius: 6px; color: #333; font-weight: 600; cursor: pointer; }
.action-btn:hover { background: #e4e6eb; }

/* Vouchers */
.voucher-panel { background: #fffbf0; padding: 12px; border-top: 1px dashed #e8e8e8; }
.loading-text, .empty-text, .log-text { font-size: 12px; color: #999; text-align: center; padding: 10px 0; }
.log-text { color: #f63; }
.voucher-item { display: flex; background: white; border-radius: 6px; margin-bottom: 8px; overflow: hidden; border: 1px solid #ffe8cc; }
.v-left { background: #fff7e6; padding: 10px; display: flex; flex-direction: column; justify-content: center; align-items: center; width: 80px; border-right: 1px dashed #ffd591; }
.v-val { font-size: 18px; font-weight: bold; color: #f63; }
.v-cond { font-size: 10px; color: #fa8c16; }
.v-right { flex: 1; padding: 10px; display: flex; justify-content: space-between; align-items: center; }
.v-title { font-size: 13px; color: #333; font-weight: 500; }
.seckill-btn { padding: 4px 12px; background: #f63; color: white; border: none; border-radius: 12px; font-size: 12px; cursor: pointer; }
</style>