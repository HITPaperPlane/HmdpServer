<template>
  <div class="grid">
    <div class="card">
      <h3 class="title">店铺管理</h3>
      <div class="row">
        <input v-model="lookupId" placeholder="输入店铺ID，回车加载" @keyup.enter="loadShopById" />
        <button class="secondary" @click="loadShopById">加载</button>
      </div>
      <div class="grid" style="grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));">
        <div>
          <label>名称</label>
          <input v-model="shopForm.name" placeholder="店铺名称" />
        </div>
        <div>
          <label>类型ID</label>
          <input v-model.number="shopForm.typeId" placeholder="typeId" type="number" />
        </div>
        <div>
          <label>图片（逗号分隔）</label>
          <input v-model="shopForm.images" placeholder="http://..." />
        </div>
        <div>
          <label>商圈</label>
          <input v-model="shopForm.area" />
        </div>
        <div>
          <label>地址</label>
          <input v-model="shopForm.address" />
        </div>
        <div>
          <label>经度/纬度</label>
          <div class="row">
            <input v-model.number="shopForm.x" placeholder="经度" />
            <input v-model.number="shopForm.y" placeholder="纬度" />
          </div>
        </div>
        <div>
          <label>人均消费</label>
          <input v-model.number="shopForm.avgPrice" type="number" />
        </div>
        <div>
          <label>营业时间</label>
          <input v-model="shopForm.openHours" placeholder="10:00-22:00" />
        </div>
        <div>
          <label>评分(1-5倍10)</label>
          <input v-model.number="shopForm.score" type="number" placeholder="45 代表4.5分" />
        </div>
      </div>
      <div class="row" style="margin-top:10px;">
        <button @click="createShop">新建店铺</button>
        <button class="secondary" @click="updateShop">更新当前店铺</button>
      </div>
      <div class="log">{{ log }}</div>
    </div>
    <div class="card">
      <h4 class="title">搜索店铺（全局）</h4>
      <div class="row">
        <input v-model="searchName" placeholder="关键字" @keyup.enter="searchShops" />
        <button class="secondary" @click="searchShops">搜索</button>
      </div>
      <ul class="list">
        <li v-for="s in searchResult" :key="s.id">
          <div class="flex-between">
            <div>
              <strong>{{ s.name }}</strong>
              <div class="muted">ID: {{ s.id }} | 类型: {{ s.typeId }}</div>
            </div>
            <button class="secondary" @click="prefill(s)">填入表单</button>
          </div>
          <div class="muted">地址：{{ s.address }}</div>
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { request } from '../api/http';
import { useSessionStore } from '../stores/session';

const session = useSessionStore();
const shopForm = reactive({
  id: '',
  name: '',
  typeId: '',
  images: '',
  area: '',
  address: '',
  x: '',
  y: '',
  avgPrice: '',
  score: '',
  openHours: ''
});
const lookupId = ref('');
const searchName = ref('');
const searchResult = ref([]);
const log = ref('准备就绪');

function fillForm(data) {
  Object.assign(shopForm, data);
}

function prefill(shop) {
  fillForm(shop);
  lookupId.value = shop.id;
}

async function loadShopById() {
  if (!lookupId.value) return;
  try {
    const data = await request(`/shop/${lookupId.value}`);
    fillForm(data);
    log.value = `已加载店铺 ${data.name}`;
  } catch (e) {
    log.value = e.message;
  }
}

async function createShop() {
  try {
    const payload = { ...shopForm };
    delete payload.id;
    const id = await request('/shop', { method: 'POST', body: payload, token: session.token });
    log.value = `创建成功，店铺ID：${id}`;
    lookupId.value = id;
  } catch (e) {
    log.value = e.message;
  }
}

async function updateShop() {
  if (!lookupId.value && !shopForm.id) {
    log.value = '需要先加载或创建店铺';
    return;
  }
  try {
    await request('/shop', { method: 'PUT', body: { ...shopForm, id: shopForm.id || lookupId.value }, token: session.token });
    log.value = '更新成功（缓存会被淘汰）';
  } catch (e) {
    log.value = e.message;
  }
}

async function searchShops() {
  if (!searchName.value) return;
  try {
    searchResult.value = await request(`/shop/of/name?name=${encodeURIComponent(searchName.value)}&current=1`);
  } catch (e) {
    log.value = e.message;
  }
}

if (session.role !== 'MERCHANT') {
  session.role = 'MERCHANT';
}
</script>
