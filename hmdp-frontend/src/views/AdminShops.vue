<template>
  <div class="grid">
    <div class="card">
      <h3 class="title">店铺巡检</h3>
      <div class="row">
        <select v-model="selectedType" @change="loadByType">
          <option v-for="t in types" :key="t.id" :value="t.id">{{ t.name }}</option>
        </select>
        <input v-model="coords.x" placeholder="经度(选填)" />
        <input v-model="coords.y" placeholder="纬度(选填)" />
        <button class="secondary" @click="loadByType">按类型加载</button>
      </div>
      <div class="grid">
        <div class="card" v-for="s in shops" :key="s.id">
          <div class="flex-between">
            <div>
              <div class="title">{{ s.name }}</div>
              <div class="muted">ID: {{ s.id }} | type: {{ s.typeId }}</div>
            </div>
            <span class="pill">距当前位置 {{ s.distance ? s.distance.toFixed(2) : '-' }} km</span>
          </div>
          <div class="muted">地址：{{ s.address }}</div>
          <div class="muted">人均 ¥{{ s.avgPrice }} · 评分 {{ (s.score || 0) / 10 }}</div>
        </div>
      </div>
    </div>
    <div class="card">
      <h4 class="title">精确编辑</h4>
      <div class="row">
        <input v-model="editId" placeholder="店铺ID" @keyup.enter="load" />
        <button class="secondary" @click="load">加载</button>
      </div>
      <div class="grid" style="grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));">
        <div><label>名称</label><input v-model="form.name" /></div>
        <div><label>typeId</label><input v-model.number="form.typeId" type="number" /></div>
        <div><label>图片</label><input v-model="form.images" /></div>
        <div><label>地址</label><input v-model="form.address" /></div>
        <div><label>商圈</label><input v-model="form.area" /></div>
        <div><label>经纬度</label><div class="row"><input v-model.number="form.x" /><input v-model.number="form.y" /></div></div>
        <div><label>人均</label><input v-model.number="form.avgPrice" type="number" /></div>
        <div><label>营业时间</label><input v-model="form.openHours" /></div>
        <div><label>评分*10</label><input v-model.number="form.score" type="number" /></div>
      </div>
      <div class="row" style="margin-top:10px;">
        <button @click="create">新增</button>
        <button class="secondary" @click="update">保存更新</button>
      </div>
      <div class="log">{{ log }}</div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { request } from '../api/http';
import { useSessionStore } from '../stores/session';

const session = useSessionStore();
const types = ref([]);
const selectedType = ref('');
const coords = reactive({ x: '', y: '' });
const shops = ref([]);
const form = reactive({
  id: '',
  name: '',
  typeId: '',
  images: '',
  area: '',
  address: '',
  x: '',
  y: '',
  avgPrice: '',
  openHours: '',
  score: ''
});
const editId = ref('');
const log = ref('等待操作');

async function loadTypes() {
  types.value = await request('/shop-type/list');
  selectedType.value = types.value[0]?.id || '';
}

async function loadByType() {
  if (!selectedType.value) return;
  const query = `/shop/of/type?typeId=${selectedType.value}&current=1${coords.x ? `&x=${coords.x}` : ''}${coords.y ? `&y=${coords.y}` : ''}`;
  shops.value = await request(query);
}

async function load() {
  if (!editId.value) return;
  try {
    const data = await request(`/shop/${editId.value}`);
    Object.assign(form, data);
    log.value = `已加载 ${data.name}`;
  } catch (e) {
    log.value = e.message;
  }
}

async function create() {
  try {
    const id = await request('/shop', { method: 'POST', body: form, token: session.token });
    log.value = `新增成功：${id}`;
    editId.value = id;
  } catch (e) {
    log.value = e.message;
  }
}

async function update() {
  if (!editId.value && !form.id) {
    log.value = '需要店铺ID';
    return;
  }
  try {
    await request('/shop', { method: 'PUT', body: { ...form, id: form.id || editId.value }, token: session.token });
    log.value = '更新成功';
  } catch (e) {
    log.value = e.message;
  }
}

onMounted(async () => {
  if (session.role !== 'ADMIN') session.role = 'ADMIN';
  try {
    await loadTypes();
    await loadByType();
  } catch (e) {
    log.value = e.message;
  }
});
</script>
