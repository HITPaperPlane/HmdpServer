<template>
  <div class="grid">
    <div class="card">
      <h3 class="title">经营总览</h3>
      <div class="muted">选择店铺类型快速浏览，点击下方按钮跳转到管理页。</div>
      <div class="row" style="margin: 10px 0;">
        <select v-model="selectedType" @change="loadShops">
          <option v-for="t in shopTypes" :key="t.id" :value="t.id">{{ t.name }}</option>
        </select>
        <button class="secondary" @click="to('shops')">店铺管理</button>
        <button class="secondary" @click="to('vouchers')">券与秒杀</button>
        <button class="secondary" @click="to('content')">内容/笔记</button>
      </div>
      <div class="grid">
        <div class="card" v-for="s in shops" :key="s.id">
          <div class="flex-between">
            <div>
              <div class="title">{{ s.name }}</div>
              <div class="muted">店铺ID：{{ s.id }}</div>
            </div>
            <span class="pill">评分 {{ (s.score || 0) / 10 }}</span>
          </div>
          <div class="muted">地址：{{ s.address }}</div>
          <div class="muted">人均：¥{{ s.avgPrice }}</div>
        </div>
      </div>
    </div>
    <div class="card">
      <h4 class="title">操作说明</h4>
      <ul class="list">
        <li>商家登录使用 `/merchant/code` + `/merchant/login`，token 会携带 role=MERCHANT。</li>
        <li>店铺创建/更新：`POST /shop`、`PUT /shop`，刷新缓存后立即对 C 端生效。</li>
        <li>券与秒杀：`POST /voucher`、`POST /voucher/seckill`，后者会预热 Redis 库存键。</li>
        <li>内容：`POST /blog` 推送到关注者 feed，图片可先 `POST /upload/blog`。</li>
      </ul>
      <div class="log">{{ log }}</div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { request } from '../api/http';
import { useSessionStore } from '../stores/session';

const router = useRouter();
const session = useSessionStore();
const shopTypes = ref([]);
const shops = ref([]);
const selectedType = ref('');
const log = ref('等待加载...');

function to(path) {
  router.push(`/merchant/${path}`);
}

async function loadTypes() {
  try {
    shopTypes.value = await request('/shop-type/list');
    selectedType.value = shopTypes.value[0]?.id || '';
    loadShops();
    log.value = '类型已加载';
  } catch (e) {
    log.value = e.message;
  }
}

async function loadShops() {
  if (!selectedType.value) return;
  try {
    shops.value = await request(`/shop/of/type?typeId=${selectedType.value}&current=1`);
  } catch (e) {
    log.value = e.message;
  }
}

onMounted(() => {
  if (session.role !== 'MERCHANT') {
    session.role = 'MERCHANT';
  }
  loadTypes();
});
</script>
