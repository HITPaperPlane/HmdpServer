<template>
  <div class="page">
    <section class="hero">
      <div class="hero-left">
        <div class="hero-title">店铺巡检 & 编辑</div>
        <div class="hero-sub">按类型巡检店铺，支持多图上传、创建/更新（更新会淘汰缓存）</div>
      </div>
      <div class="hero-actions">
        <van-button size="small" plain type="default" @click="resetForm">清空表单</van-button>
        <van-button size="small" type="primary" @click="reloadTypes">刷新类型</van-button>
      </div>
    </section>

    <section class="grid-2">
      <div class="card">
        <div class="section-head">
          <div>
            <div class="title">按类型巡检</div>
            <div class="muted">接口：`GET /shop/of/type`（可选传 `x/y` 计算距离）</div>
          </div>
          <van-button size="small" plain type="primary" @click="refreshByType">刷新</van-button>
        </div>

        <van-cell-group inset>
          <van-field label="店铺类型" is-link readonly :model-value="selectedTypeLabel" placeholder="请选择" @click="openTypePicker('filter')" />
          <van-field v-model="coords.x" label="经度(选填)" placeholder="例如 120.15" clearable />
          <van-field v-model="coords.y" label="纬度(选填)" placeholder="例如 30.28" clearable />
        </van-cell-group>

        <van-list v-model:loading="list.loading" :finished="list.finished" finished-text="没有更多了" @load="loadByType">
          <div class="shop-grid">
            <div v-for="s in list.rows" :key="s.id" class="shop-card">
              <van-image class="cover" :src="firstShopImage(s)" fit="cover" radius="14" />
              <div class="meta">
                <div class="name">{{ s.name }}</div>
                <div class="muted">ID {{ s.id }} · {{ typeName(s.typeId) }}</div>
                <div class="muted van-ellipsis">{{ s.address }}</div>
                <div class="metrics">
                  <span class="pill">评分 {{ ((s.score || 0) / 10).toFixed(1) }}</span>
                  <span class="pill">人均 ¥{{ s.avgPrice || 0 }}</span>
                  <span class="pill" v-if="s.distance != null">距 {{ Number(s.distance).toFixed(2) }} km</span>
                </div>
                <div class="actions">
                  <van-button size="small" plain type="primary" @click="prefill(s)">编辑</van-button>
                  <van-button size="small" plain type="default" @click="go(`/shops/${s.id}`)">预览C端</van-button>
                  <van-button size="small" plain type="default" @click="go({ path: '/admin/vouchers', query: { shopId: String(s.id) } })">券池</van-button>
                </div>
              </div>
            </div>
          </div>
        </van-list>

        <div class="log">{{ log }}</div>
      </div>

      <div class="card">
        <div class="section-head">
          <div>
            <div class="title">新建 / 编辑</div>
            <div class="muted">创建：`POST /shop`，更新：`PUT /shop`；图片上传：`POST /upload/shop`</div>
          </div>
          <div class="row">
            <van-field
                v-model="lookupId"
                placeholder="输入店铺ID加载"
                style="max-width: 220px;"
                clearable
                @keyup.enter="loadShopById"
            />
            <van-button size="small" plain type="primary" @click="loadShopById">加载</van-button>
          </div>
        </div>

        <van-form @submit="submit">
          <van-cell-group inset>
            <van-field v-model="form.name" label="店铺名称" placeholder="必填" />
            <van-field label="店铺类型" is-link readonly :model-value="typeLabel" placeholder="请选择" @click="openTypePicker('form')" />

            <van-field name="images" label="店铺图片">
              <template #input>
                <van-uploader
                    v-model="form.fileList"
                    multiple
                    :max-count="9"
                    preview-size="64"
                    :after-read="afterReadShopImage"
                />
              </template>
            </van-field>

            <van-field v-model="form.area" label="商圈" placeholder="如：大关" />
            <van-field v-model="form.address" label="地址" placeholder="必填" />
            <van-field v-model.number="form.x" type="number" label="经度" placeholder="必填" />
            <van-field v-model.number="form.y" type="number" label="纬度" placeholder="必填" />
            <van-field v-model.number="form.avgPrice" type="number" label="人均消费" placeholder="如：80" />
            <van-field v-model.number="form.score" type="number" label="评分*10" placeholder="如：45 = 4.5分" />
            <van-field v-model.number="form.sold" type="number" label="销量" placeholder="默认 0" />
            <van-field v-model.number="form.comments" type="number" label="评论数" placeholder="默认 0" />
            <van-field v-model="form.openHours" label="营业时间" placeholder="如：10:00-22:00" />
          </van-cell-group>

          <div class="form-actions">
            <van-button block type="primary" native-type="submit">
              {{ isEditing ? '保存更新' : '创建店铺' }}
            </van-button>
          </div>
        </van-form>
      </div>
    </section>

    <van-popup v-model:show="typePicker.show" position="bottom" round>
      <van-picker
          title="选择店铺类型"
          :columns="typePicker.columns"
          @confirm="onPickType"
          @cancel="typePicker.show=false"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { request, upload } from '../api/http';
import { resolveImg, splitImages } from '../utils/media';
import { useSessionStore } from '../stores/session';

const session = useSessionStore();
const router = useRouter();

const log = ref('准备就绪');

const types = ref([]);
const selectedType = ref(null);
const coords = reactive({ x: '', y: '' });
const list = reactive({ rows: [], page: 1, loading: false, finished: false, inFlight: false });

const lookupId = ref('');
const typePicker = reactive({ show: false, target: 'filter', columns: [] });

const form = reactive({
  id: null,
  name: '',
  typeId: null,
  fileList: [],
  area: '',
  address: '',
  x: null,
  y: null,
  avgPrice: null,
  score: 40,
  sold: 0,
  comments: 0,
  openHours: ''
});

const isEditing = computed(() => Boolean(form.id || lookupId.value));

const selectedTypeLabel = computed(() => {
  const id = selectedType.value;
  if (!id) return '';
  const t = types.value.find(x => x.id === id);
  return t ? `${t.name}（${t.id}）` : `类型 ${id}`;
});

const typeLabel = computed(() => {
  const id = form.typeId;
  if (!id) return '';
  const t = types.value.find(x => x.id === id);
  return t ? `${t.name}（${t.id}）` : `类型 ${id}`;
});

function typeName(typeId) {
  const t = types.value.find(x => x.id === typeId);
  return t ? t.name : `类型 ${typeId}`;
}

function firstShopImage(shop) {
  return splitImages(shop.images)[0] || '';
}

function go(target) {
  router.push(target);
}

function resetForm() {
  lookupId.value = '';
  Object.assign(form, {
    id: null,
    name: '',
    typeId: null,
    fileList: [],
    area: '',
    address: '',
    x: null,
    y: null,
    avgPrice: null,
    score: 40,
    sold: 0,
    comments: 0,
    openHours: ''
  });
  log.value = '已清空表单';
}

async function reloadTypes() {
  await loadTypes();
  log.value = '类型已刷新';
}

async function loadTypes() {
  const list = await request('/shop-type/list');
  types.value = Array.isArray(list) ? list : [];
  typePicker.columns = types.value.map(t => ({ text: `${t.name}（${t.id}）`, value: t.id }));
  if (!selectedType.value && types.value.length) {
    selectedType.value = types.value[0].id;
  }
}

function openTypePicker(target) {
  typePicker.target = target;
  typePicker.show = true;
}

function onPickType(value) {
  const picked = Number(value);
  if (typePicker.target === 'form') {
    form.typeId = picked;
  } else {
    selectedType.value = picked;
    refreshByType();
  }
  typePicker.show = false;
}

function refreshByType() {
  list.rows = [];
  list.page = 1;
  list.finished = false;
  list.loading = false;
  list.inFlight = false;
  loadByType();
}

async function loadByType() {
  if (!selectedType.value || list.finished || list.inFlight) return;
  list.inFlight = true;
  list.loading = true;
  try {
    const q = new URLSearchParams({ typeId: String(selectedType.value), current: String(list.page) });
    if (coords.x) q.set('x', coords.x);
    if (coords.y) q.set('y', coords.y);
    const rows = await request(`/shop/of/type?${q.toString()}`);
    const arr = Array.isArray(rows) ? rows : [];
    if (arr.length === 0) {
      list.finished = true;
      return;
    }
    list.rows.push(...arr);
    list.page += 1;
    if (arr.length < 10) list.finished = true;
  } catch (e) {
    list.finished = true;
    log.value = e?.message || '加载失败';
  } finally {
    list.loading = false;
    list.inFlight = false;
  }
}

async function afterReadShopImage(fileItem) {
  const items = Array.isArray(fileItem) ? fileItem : [fileItem];
  for (const it of items) {
    const file = it?.file || it;
    if (!file) continue;
    try {
      it.status = 'uploading';
      const path = await upload('/upload/shop', file, session.token);
      it.url = resolveImg(path);
      it.name = path;
      it.status = 'done';
    } catch {
      it.status = 'failed';
      it.message = '上传失败';
    }
  }
}

function applyShop(data) {
  lookupId.value = String(data.id || '');
  form.id = data.id || null;
  form.name = data.name || '';
  form.typeId = data.typeId || null;
  form.area = data.area || '';
  form.address = data.address || '';
  form.x = data.x ?? null;
  form.y = data.y ?? null;
  form.avgPrice = data.avgPrice ?? null;
  form.score = data.score ?? 40;
  form.sold = data.sold ?? 0;
  form.comments = data.comments ?? 0;
  form.openHours = data.openHours || '';
  const rawImgs = (data.images || '').split(',').map(s => s.trim()).filter(Boolean);
  form.fileList = rawImgs.map(p => ({ url: resolveImg(p), name: p, status: 'done' }));
}

function prefill(shop) {
  applyShop(shop);
  log.value = '已填入表单，可直接更新';
}

async function loadShopById() {
  if (!lookupId.value) return;
  try {
    const data = await request(`/shop/${lookupId.value}`);
    applyShop(data || {});
    log.value = `已加载：${data?.name || lookupId.value}`;
  } catch (e) {
    log.value = e?.message || '加载失败';
  }
}

async function submit() {
  if (!form.name.trim() || !form.typeId || !form.address.trim() || form.x == null || form.y == null) {
    log.value = '请补齐必填项（名称/类型/地址/经纬度）';
    return;
  }
  const payload = {
    id: form.id || (lookupId.value ? Number(lookupId.value) : undefined),
    name: form.name.trim(),
    typeId: Number(form.typeId),
    images: form.fileList.map(f => f.name).filter(Boolean).join(','),
    area: form.area || null,
    address: form.address.trim(),
    x: Number(form.x),
    y: Number(form.y),
    avgPrice: form.avgPrice != null ? Number(form.avgPrice) : null,
    score: form.score != null ? Number(form.score) : 40,
    sold: form.sold != null ? Number(form.sold) : 0,
    comments: form.comments != null ? Number(form.comments) : 0,
    openHours: form.openHours || null
  };

  try {
    if (isEditing.value) {
      await request('/shop', { method: 'PUT', body: payload, token: session.token });
      log.value = '更新成功';
      refreshByType();
    } else {
      delete payload.id;
      const id = await request('/shop', { method: 'POST', body: payload, token: session.token });
      lookupId.value = String(id);
      form.id = Number(id);
      log.value = `创建成功：店铺ID ${id}`;
      refreshByType();
    }
  } catch (e) {
    log.value = e?.message || '提交失败';
  }
}

onMounted(async () => {
  await loadTypes();
  refreshByType();
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
  background: linear-gradient(135deg, #111827 0%, #334155 100%);
  color: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.22);
}

.hero-title {
  font-size: 18px;
  font-weight: 900;
}

.hero-sub {
  margin-top: 6px;
  font-size: 13px;
  opacity: 0.9;
}

.hero-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.card {
  padding: 14px;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.shop-grid {
  display: grid;
  grid-template-columns: 1fr;
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
  font-weight: 900;
  color: #111827;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metrics {
  margin-top: 10px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.actions {
  margin-top: 10px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.form-actions {
  padding: 16px;
}

@media (max-width: 980px) {
  .grid-2 {
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
