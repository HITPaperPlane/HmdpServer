<template>
  <div class="grid">
    <div class="card" style="grid-column: 1 / -1;">
      <div class="flex-between">
        <div>
          <div class="muted">LBS · 优惠券 · 秒杀</div>
          <h2 class="title" style="margin:4px 0;">附近好店，直接抢券</h2>
        </div>
        <button class="secondary" @click="goLogin">{{ session.token ? '切换账号' : '登录' }}</button>
      </div>
      <div class="row" style="margin-top:10px;">
        <input v-model="search" placeholder="搜索店铺名称" />
        <button class="secondary" @click="searchByName">搜索</button>
        <button class="secondary" @click="geoSearch">附近</button>
      </div>
      <div class="row" style="margin-top:10px;">
        <div class="pill" v-for="(t, idx) in types" :key="t.id" :style="{ background: idx===typeIndex ? 'rgba(14,165,233,0.16)' : '' }" @click="changeType(idx)">
          {{ t.name }}
        </div>
      </div>
    </div>

    <div class="card" v-for="shop in shops" :key="shop.id">
      <div class="flex-between">
        <div>
          <div class="title">{{ shop.name }}</div>
          <div class="muted">{{ shop.address }}</div>
          <div class="muted">类型 {{ shop.typeId }} ｜ 评分 {{ shop.comments || 0 }}</div>
        </div>
        <button class="secondary" @click="loadVouchers(shop)">查看券</button>
      </div>
      <ul class="list" v-if="activeShop && activeShop.id === shop.id">
        <li v-for="v in vouchers" :key="v.id">
          <div class="flex-between">
            <div>
              <span class="pill">券 {{ v.id }}</span> {{ v.title }} ｜ 面额 {{ v.actualValue }} / 支付 {{ v.payValue }}
              <div class="muted">限购 {{ v.limitType }} / {{ v.userLimit || 1 }}</div>
            </div>
            <button @click="seckill(v.id)">秒杀</button>
          </div>
        </li>
      </ul>
      <div v-if="activeShop && activeShop.id === shop.id && vouchers.length === 0" class="muted">暂无券</div>
      <div class="log" v-if="logMsg && activeShop && activeShop.id === shop.id">{{ logMsg }}</div>
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
const types = ref([]);
const typeIndex = ref(0);
const shops = ref([]);
const search = ref('');
const activeShop = ref(null);
const vouchers = ref([]);
const logMsg = ref('');

onMounted(async () => {
  session.role = 'USER';
  await loadTypes();
  await loadHotShops();
});

async function loadTypes() {
  try {
    types.value = await request('/shop-type/list');
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
  try {
    shops.value = await request(`/shop/of/name?name=${encodeURIComponent(search.value)}&current=1`);
  } catch (e) {
    console.error(e);
  }
}

async function geoSearch() {
  if (!types.value.length) return;
  const typeId = types.value[typeIndex.value]?.id;
  navigator.geolocation.getCurrentPosition(
    async pos => {
      const { longitude, latitude } = pos.coords;
      shops.value = await request(`/shop/of/type?typeId=${typeId}&current=1&x=${longitude}&y=${latitude}`);
    },
    () => {
      alert('无法获取定位');
    }
  );
}

async function loadVouchers(shop) {
  activeShop.value = shop;
  vouchers.value = [];
  logMsg.value = '加载中...';
  try {
    vouchers.value = await request(`/voucher/list/${shop.id}`);
    logMsg.value = `券列表 ${vouchers.value.length} 条`;
  } catch (e) {
    logMsg.value = e.message;
  }
}

async function seckill(id) {
  if (!session.token) {
    alert('请先登录');
    return;
  }
  try {
    const orderId = await request(`/voucher-order/seckill/${id}`, { method: 'POST', token: session.token });
    logMsg.value = `排队中，订单号 ${orderId}`;
  } catch (e) {
    logMsg.value = e.message;
  }
}

function goLogin() {
  router.push('/login');
}
</script>
