<template>
  <div class="page">
    <section class="hero">
      <div class="hero-left">
        <div class="hero-title">券与秒杀（商家端）</div>
        <div class="hero-sub">
          先选择「我创建的店铺」，再创建普通券或提交秒杀券审核；秒杀券需管理员审核并预热后，用户端才可见/可抢
        </div>
      </div>
      <div class="hero-actions">
        <van-button size="small" plain type="default" @click="reloadMyShops">刷新店铺</van-button>
        <van-button size="small" plain type="default" @click="router.push('/merchant/shops')">开店/编辑</van-button>
        <van-button size="small" type="primary" @click="reloadVouchers" :disabled="!selectedShop">刷新券列表</van-button>
      </div>
    </section>

    <section class="grid">
      <div class="card shop-card">
        <div class="section-head">
          <div>
            <div class="title">我的店铺</div>
            <div class="muted">接口：`GET /shop/of/me`（仅返回你创建的店铺）</div>
          </div>
        </div>

        <van-cell-group inset>
          <van-field v-model="shopFilter" label="筛选" placeholder="输入店铺名称关键字（仅筛选本人的店铺）" clearable />
          <van-field :model-value="selectedShopLabel" label="当前店铺" readonly placeholder="从下方列表选择一个" />
        </van-cell-group>

        <div v-if="shopsLoading" style="padding: 14px 0;">
          <van-loading size="24px" vertical>加载店铺中…</van-loading>
        </div>

        <div v-else-if="filteredShops.length === 0" style="padding: 10px 0;">
          <van-empty description="你还没有创建店铺" />
          <div style="display:flex; justify-content:center; margin-top:10px;">
            <van-button size="small" type="primary" @click="router.push('/merchant/shops')">去创建店铺</van-button>
          </div>
        </div>

        <div v-else class="shop-list">
          <van-radio-group v-model="selectedShopId">
            <div
                v-for="s in filteredShops"
                :key="s.id"
                class="shop-row"
                @click="selectShopId(s.id)"
            >
              <van-radio :name="s.id" />
              <div class="shop-meta">
                <div class="shop-name">{{ s.name }}</div>
                <div class="muted">ID {{ s.id }} · 类型 {{ s.typeId }} · {{ s.area || '未知商圈' }}</div>
              </div>
            </div>
          </van-radio-group>
        </div>

        <div class="divider"></div>

        <div class="section-head">
          <div>
            <div class="title">说明</div>
            <div class="muted">普通券：立即生效；秒杀券：提交后需管理员「审核并预热」才能在用户端展示</div>
          </div>
        </div>
      </div>

      <div class="card main-card">
        <div class="section-head">
          <div>
            <div class="title">券管理</div>
            <div class="muted">创建与查看均基于当前店铺；管理端列表接口：`GET /voucher/list/manage/{shopId}`</div>
          </div>
        </div>

        <div v-if="!selectedShop" style="padding: 10px 0;">
          <van-empty description="先在左侧选择一个店铺" />
        </div>

        <div v-else>
          <div class="selected-badge">
            当前：{{ selectedShop.name }}（ID {{ selectedShop.id }}）
          </div>

          <van-tabs v-model:active="activeTab" animated color="#ff6b6b" line-width="40">
            <van-tab title="普通券">
              <van-form @submit="createVoucher">
                <van-cell-group inset>
                  <van-field :model-value="String(selectedShop.id)" label="店铺ID" readonly />
                  <van-field v-model="voucherForm.title" label="标题" placeholder="例如：工作日特惠" />
                  <van-field v-model="voucherForm.subTitle" label="副标题" placeholder="例如：下午茶 2 人套餐" />
                  <van-field v-model.number="voucherForm.payValue" type="number" label="支付金额" placeholder="如：10" />
                  <van-field v-model.number="voucherForm.actualValue" type="number" label="抵扣金额" placeholder="如：30" />
                  <van-field v-model="voucherForm.rules" label="规则" type="textarea" rows="2" autosize placeholder="使用规则" />
                </van-cell-group>
                <div class="form-actions">
                  <van-button block type="primary" native-type="submit" :disabled="!canCreateVoucher">创建普通券</van-button>
                  <div class="muted" style="margin-top:8px;">普通券无库存概念，创建后用户端立即可见</div>
                </div>
              </van-form>
            </van-tab>

            <van-tab title="秒杀券（待审核）">
              <van-form @submit="createSeckill">
                <van-cell-group inset>
                  <van-field :model-value="String(selectedShop.id)" label="店铺ID" readonly />
                  <van-field v-model="seckillForm.title" label="标题" placeholder="例如：限时秒杀券" />
                  <van-field v-model="seckillForm.subTitle" label="副标题" placeholder="例如：仅限今天" />
                  <van-field v-model.number="seckillForm.payValue" type="number" label="支付金额" placeholder="如：1" />
                  <van-field v-model.number="seckillForm.actualValue" type="number" label="抵扣金额" placeholder="如：50" />
                  <van-field v-model.number="seckillForm.stock" type="number" label="库存" placeholder="如：100" />

                  <van-field name="limitType" label="限购类型">
                    <template #input>
                      <van-radio-group v-model="seckillForm.limitType" direction="horizontal">
                        <van-radio :name="1">一人一单</van-radio>
                        <van-radio :name="2">一人多单</van-radio>
                        <van-radio :name="3">累计限购</van-radio>
                      </van-radio-group>
                    </template>
                  </van-field>
                  <van-field
                      v-model.number="seckillForm.userLimit"
                      type="number"
                      label="单用户限购"
                      :disabled="seckillForm.limitType === 1"
                      placeholder="累计限购填写阈值，例如 5"
                  />

                  <van-field name="beginTime" label="开始时间">
                    <template #input>
                      <input class="native-input" type="datetime-local" v-model="seckillForm.beginTime" />
                    </template>
                  </van-field>
                  <van-field name="endTime" label="结束时间">
                    <template #input>
                      <input class="native-input" type="datetime-local" v-model="seckillForm.endTime" />
                    </template>
                  </van-field>

                  <van-field v-model="seckillForm.rules" label="规则" type="textarea" rows="2" autosize placeholder="秒杀规则" />
                </van-cell-group>
                <div class="form-actions">
                  <van-button block type="danger" native-type="submit" :disabled="!canCreateSeckill">提交秒杀券审核</van-button>
                  <div class="muted" style="margin-top:8px;">
                    秒杀券创建后默认「待审核预热」，管理员在后台点击「审核并预热」后，用户端才会展示并可抢购
                  </div>
                </div>
              </van-form>
            </van-tab>
          </van-tabs>

          <div class="divider"></div>

          <div class="section-head">
            <div>
              <div class="title">券列表</div>
              <div class="muted">普通券立即生效；秒杀券需预热后用户端可见</div>
            </div>
            <van-button size="small" plain type="primary" @click="reloadVouchers">刷新</van-button>
          </div>

          <div v-if="vouchersLoading" style="padding: 12px 0;">
            <van-loading size="24px" vertical>加载中…</van-loading>
          </div>

          <div v-else-if="vouchers.length === 0" style="padding: 10px 0;">
            <van-empty description="该店铺暂无优惠券" />
          </div>

          <div v-else class="voucher-list">
            <div class="voucher-card" v-for="v in vouchers" :key="v.id">
              <div class="voucher-top">
                <div class="voucher-title">{{ v.title }}</div>
                <div class="row" style="gap:6px;">
                  <van-tag :type="v.type === 1 ? 'danger' : 'primary'" plain size="mini">
                    {{ v.type === 1 ? '秒杀券' : '普通券' }}
                  </van-tag>
                  <van-tag
                      v-if="v.type === 1"
                      :type="Number(v.preheatStatus || 0) >= 2 ? 'success' : 'warning'"
                      plain
                      size="mini"
                  >
                    {{ preheatLabel(v.preheatStatus) }}
                  </van-tag>
                </div>
              </div>

              <div class="voucher-sub muted">ID {{ v.id }} · {{ v.subTitle || '—' }}</div>
              <div class="voucher-price">
                <span class="pay">¥{{ v.payValue }}</span>
                <span class="arrow">→</span>
                <span class="actual">抵 ¥{{ v.actualValue }}</span>
              </div>
              <div class="voucher-rule muted">{{ v.rules }}</div>
              <div class="muted" v-if="v.type === 1">
                窗口：{{ v.beginTime }} ~ {{ v.endTime }} · 库存 {{ v.stock ?? 0 }} · 限购 {{ limitLabel(v.limitType, v.userLimit) }}
              </div>

              <div class="row actions">
                <van-button size="small" plain type="default" @click="copyText(v.id)">复制ID</van-button>
                <van-button size="small" plain type="primary" @click="router.push(`/shops/${selectedShop.id}`)">用户端查看</van-button>
              </div>
            </div>
          </div>

          <div class="log">{{ log }}</div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { request } from '../api/http';
import { useSessionStore } from '../stores/session';

const session = useSessionStore();
const route = useRoute();
const router = useRouter();

const activeTab = ref(0);
const vouchers = ref([]);
const vouchersLoading = ref(false);
const shopsLoading = ref(false);
const log = ref('准备就绪');

const myShops = ref([]);
const shopFilter = ref('');
const selectedShopId = ref(null);

const selectedShop = computed(() => {
  const id = selectedShopId.value;
  return myShops.value.find(s => s.id === id) || null;
});

const selectedShopLabel = computed(() => {
  if (!selectedShop.value) return '';
  return `${selectedShop.value.name}（ID ${selectedShop.value.id}）`;
});

const filteredShops = computed(() => {
  const kw = shopFilter.value.trim().toLowerCase();
  if (!kw) return myShops.value;
  return myShops.value.filter(s => String(s.name || '').toLowerCase().includes(kw));
});

const voucherForm = reactive({
  title: '',
  subTitle: '',
  payValue: 10,
  actualValue: 30,
  rules: '满50减20',
  ownerType: 1,
  type: 0,
  limitType: 1,
  userLimit: 1,
  status: 1
});

const seckillForm = reactive({
  title: '',
  subTitle: '',
  payValue: 1,
  actualValue: 50,
  rules: '限时秒杀',
  stock: 100,
  beginTime: '',
  endTime: '',
  limitType: 1,
  userLimit: 1,
  ownerType: 1,
  type: 1,
  status: 1
});

const canCreateVoucher = computed(() => {
  return Boolean(selectedShop.value && voucherForm.title.trim() && voucherForm.payValue > 0 && voucherForm.actualValue > 0);
});

const canCreateSeckill = computed(() => {
  if (!selectedShop.value) return false;
  if (!seckillForm.title.trim()) return false;
  if (!seckillForm.beginTime || !seckillForm.endTime) return false;
  if (!seckillForm.stock || seckillForm.stock <= 0) return false;
  const t = Number(seckillForm.limitType || 1);
  if (t === 3 && (!seckillForm.userLimit || seckillForm.userLimit <= 0)) return false;
  return true;
});

function limitLabel(limitType, userLimit) {
  const t = Number(limitType || 0);
  if (t === 1) return '一人一单';
  if (t === 2) return '一人多单';
  if (t === 3) return `累计限购 ${userLimit || 1} 单`;
  return '未设置';
}

function preheatLabel(status) {
  const s = Number(status || 0);
  if (s >= 2) return '已预热';
  if (s === 1) return '预热中';
  return '待审核预热';
}

function selectShopId(id) {
  if (id === selectedShopId.value) return;
  selectedShopId.value = id;
  reloadVouchers();
}

async function reloadMyShops() {
  shopsLoading.value = true;
  try {
    const list = await request('/shop/of/me', { token: session.token });
    myShops.value = Array.isArray(list) ? list : [];
    log.value = `店铺已加载：${myShops.value.length} 家`;
  } catch (e) {
    myShops.value = [];
    log.value = e?.message || '加载店铺失败';
  } finally {
    shopsLoading.value = false;
  }
}

async function reloadVouchers() {
  if (!selectedShop.value) return;
  vouchersLoading.value = true;
  try {
    vouchers.value = await request(`/voucher/list/manage/${selectedShop.value.id}`, { token: session.token });
    log.value = '券列表已刷新';
  } catch (e) {
    log.value = e?.message || '加载失败';
  } finally {
    vouchersLoading.value = false;
  }
}

async function createVoucher() {
  if (!selectedShop.value || !canCreateVoucher.value) return;
  try {
    const payload = { ...voucherForm, shopId: Number(selectedShop.value.id) };
    const id = await request('/voucher', { method: 'POST', body: payload, token: session.token });
    log.value = `创建普通券成功：${id}`;
    await reloadVouchers();
  } catch (e) {
    log.value = e?.message || '创建失败';
  }
}

async function createSeckill() {
  if (!selectedShop.value || !canCreateSeckill.value) return;
  try {
    const payload = { ...seckillForm, shopId: Number(selectedShop.value.id) };
    const id = await request('/voucher/seckill', { method: 'POST', body: payload, token: session.token });
    log.value = `提交秒杀券成功：${id}（待管理员审核预热）`;
    await reloadVouchers();
  } catch (e) {
    log.value = e?.message || '创建失败';
  }
}

async function copyText(value) {
  const text = String(value ?? '');
  if (!text) return;
  try {
    await navigator.clipboard.writeText(text);
    log.value = '已复制到剪贴板';
  } catch {
    log.value = '复制失败（浏览器权限限制）';
  }
}

watch(
  () => seckillForm.limitType,
  (t) => {
    const v = Number(t || 1);
    if (v === 1) seckillForm.userLimit = 1;
    if (v === 2 && (!seckillForm.userLimit || seckillForm.userLimit < 1)) seckillForm.userLimit = 9999;
    if (v === 3 && (!seckillForm.userLimit || seckillForm.userLimit < 1)) seckillForm.userLimit = 5;
  },
  { immediate: true }
);

onMounted(async () => {
  const qShopId = route.query.shopId ? String(route.query.shopId) : '';
  await reloadMyShops();
  if (qShopId) {
    const id = Number(qShopId);
    if (myShops.value.some(s => s.id === id)) {
      selectedShopId.value = id;
      await reloadVouchers();
      return;
    }
  }
  if (myShops.value.length > 0) {
    selectedShopId.value = myShops.value[0].id;
    await reloadVouchers();
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

.hero-left {
  max-width: 720px;
}

.hero-title {
  font-size: 18px;
  font-weight: 900;
}

.hero-sub {
  margin-top: 6px;
  font-size: 13px;
  opacity: 0.92;
  line-height: 1.4;
}

.hero-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.grid {
  width: 100%;
  max-width: 1180px;
  margin: 0 auto;
  padding: 0 10px;
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: 16px;
}

.card {
  padding: 14px;
}

.shop-card {
  height: fit-content;
}

.main-card {
  min-width: 0;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.divider {
  height: 1px;
  background: rgba(0, 0, 0, 0.05);
  margin: 14px 0;
}

.shop-list {
  margin: 12px 16px 0;
  border-radius: 14px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  overflow: hidden;
  background: #fff;
}

.shop-row {
  display: grid;
  grid-template-columns: 32px 1fr;
  gap: 10px;
  padding: 12px;
  cursor: pointer;
  border-bottom: 1px solid rgba(0, 0, 0, 0.04);
  align-items: center;
}

.shop-row:last-child {
  border-bottom: none;
}

.shop-row:hover {
  background: #fff7f2;
}

.shop-meta {
  min-width: 0;
}

.shop-name {
  font-weight: 900;
  color: #111;
  font-size: 13px;
  line-height: 1.2;
}

.selected-badge {
  margin: 0 16px 12px;
  padding: 10px 12px;
  background: rgba(255, 107, 107, 0.08);
  border: 1px solid rgba(255, 107, 107, 0.22);
  color: #111;
  border-radius: 14px;
  font-weight: 800;
}

.voucher-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 8px 2px 2px;
}

.voucher-card {
  border-radius: 16px;
  border: 1px solid rgba(0, 0, 0, 0.05);
  background: #fff;
  padding: 12px;
  box-shadow: 0 10px 22px rgba(0, 0, 0, 0.04);
}

.voucher-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.voucher-title {
  font-size: 14px;
  font-weight: 900;
  color: #111;
}

.voucher-sub {
  margin-top: 6px;
}

.voucher-price {
  margin-top: 10px;
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.pay {
  color: #ef4444;
  font-weight: 900;
  font-size: 18px;
}

.arrow {
  color: #999;
}

.actual {
  font-weight: 800;
  color: #111;
}

.voucher-rule {
  margin-top: 8px;
}

.actions {
  margin-top: 10px;
  gap: 10px;
}

.form-actions {
  margin: 16px;
}

.native-input {
  width: 100%;
  border: none;
  outline: none;
  background: transparent;
  font-size: 14px;
  color: #323233;
}

@media (max-width: 980px) {
  .grid {
    grid-template-columns: 1fr;
    padding: 0 0;
  }
}
</style>
