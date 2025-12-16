<template>
  <div class="page">
    <section class="hero">
      <div>
        <div class="hero-title">店铺管理（商家端）</div>
        <div class="hero-sub">新建/编辑店铺，支持多图上传与预览（`POST /upload/shop`）</div>
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
            <div class="title">新建 / 编辑</div>
            <div class="muted">创建：`POST /shop`，更新：`PUT /shop`（更新会淘汰店铺缓存）</div>
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
            <van-field label="店铺类型" is-link readonly :model-value="typeLabel" placeholder="请选择" @click="typePicker.show=true" />

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
            <div class="log">{{ log }}</div>
          </div>
        </van-form>
      </div>

      <div class="card">
        <div class="section-head">
          <div>
            <div class="title">搜索店铺（全局）</div>
            <div class="muted">接口：`GET /shop/of/name?name=...`</div>
          </div>
          <div class="row">
            <van-field v-model="searchName" placeholder="输入关键字" clearable @keyup.enter="searchShops" />
            <van-button size="small" plain type="primary" @click="searchShops">搜索</van-button>
          </div>
        </div>

        <div v-if="searchResult.length === 0" style="padding: 10px 0;">
          <van-empty description="暂无搜索结果" />
        </div>
        <div v-else class="shop-grid">
          <div v-for="s in searchResult" :key="s.id" class="shop-card">
            <van-image class="cover" :src="firstShopImage(s)" fit="cover" radius="12" />
            <div class="meta">
              <div class="name">{{ s.name }}</div>
              <div class="muted">ID {{ s.id }} · {{ typeName(s.typeId) }}</div>
              <div class="muted">{{ s.address }}</div>
              <div class="row" style="margin-top:10px;">
                <van-button size="small" plain type="primary" @click="prefill(s)">填入表单</van-button>
                <van-button size="small" plain type="default" @click="openDetail(s)">查看</van-button>
              </div>
            </div>
          </div>
        </div>
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
import { request, upload } from '../api/http';
import { resolveImg, splitImages } from '../utils/media';
import { useSessionStore } from '../stores/session';
import { useRoute, useRouter } from 'vue-router';

const session = useSessionStore();
const route = useRoute();
const router = useRouter();

const log = ref('准备就绪');
const lookupId = ref('');
const searchName = ref('');
const searchResult = ref([]);
const types = ref([]);

const typePicker = reactive({ show: false, columns: [] });

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
  log.value = '已清空';
}

async function reloadTypes() {
  await loadTypes();
  log.value = '类型已刷新';
}

async function loadTypes() {
  const list = await request('/shop-type/list');
  types.value = Array.isArray(list) ? list : [];
  typePicker.columns = types.value.map(t => ({ text: `${t.name}（${t.id}）`, value: t.id }));
}

function onPickType({ selectedValues }) {
  form.typeId = selectedValues[0];
  typePicker.show = false;
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
    } else {
      delete payload.id;
      const id = await request('/shop', { method: 'POST', body: payload, token: session.token });
      lookupId.value = String(id);
      form.id = Number(id);
      log.value = `创建成功：店铺ID ${id}`;
    }
  } catch (e) {
    log.value = e?.message || '提交失败';
  }
}

async function searchShops() {
  if (!searchName.value.trim()) return;
  try {
    const list = await request(`/shop/of/name?name=${encodeURIComponent(searchName.value.trim())}&current=1`);
    searchResult.value = Array.isArray(list) ? list : [];
    log.value = `已找到 ${searchResult.value.length} 条`;
  } catch (e) {
    log.value = e?.message || '搜索失败';
  }
}

function prefill(shop) {
  applyShop(shop);
  log.value = '已填入表单，可直接更新';
}

function openDetail(shop) {
  router.push(`/shops/${shop.id}`);
}

onMounted(async () => {
  await loadTypes();
  const loadId = route.query.load ? String(route.query.load) : '';
  if (loadId) {
    lookupId.value = loadId;
    await loadShopById();
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
  background: linear-gradient(135deg, #ff6b6b 0%, #ff8e53 100%);
  color: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  box-shadow: 0 10px 30px rgba(255, 107, 107, 0.2);
}

.hero-title {
  font-size: 18px;
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

.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.form-actions {
  padding: 16px;
}

.shop-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
}

.shop-card {
  display: grid;
  grid-template-columns: 140px 1fr;
  gap: 12px;
  border-radius: 16px;
  border: 1px solid rgba(0, 0, 0, 0.05);
  overflow: hidden;
  background: #fff;
  box-shadow: 0 10px 22px rgba(0, 0, 0, 0.04);
}

.cover {
  width: 140px;
  height: 120px;
  background: #f5f6f7;
}

.meta {
  padding: 12px 12px;
}

.name {
  font-weight: 900;
  color: #222;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
