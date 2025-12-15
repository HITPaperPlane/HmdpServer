<template>
  <div class="page">
    <section class="hero">
      <div class="hero-left">
        <div class="hero-title">券池管理（平台）</div>
        <div class="hero-sub">选择店铺后管理普通券/秒杀券（创建秒杀券会预热 Redis 库存）</div>
      </div>
      <div class="hero-actions">
        <van-button size="small" plain type="default" @click="resetAll">重置</van-button>
        <van-button size="small" type="primary" @click="loadVouchers" :disabled="!shop.shopId">刷新券列表</van-button>
      </div>
    </section>

    <section class="grid-2">
      <div class="card">
        <div class="section-head">
          <div>
            <div class="title">选择店铺</div>
            <div class="muted">接口：`GET /shop/of/name`，券列表：`GET /voucher/list/{shopId}`</div>
          </div>
        </div>

        <van-cell-group inset>
          <van-field
              v-model="shop.keyword"
              label="店铺搜索"
              placeholder="输入店铺名称关键字"
              clearable
              @update:model-value="onShopKeywordChange"
          />
          <van-field
              :model-value="shop.shopId ? `${shop.shopName}（ID ${shop.shopId}）` : ''"
              label="已选店铺"
              readonly
              placeholder="请先从下方候选中选择"
          />
        </van-cell-group>

        <div v-if="shop.options.length" class="options">
          <div v-for="s in shop.options" :key="s.id" class="opt" @click="selectShop(s)">
            <div class="opt-name">{{ s.name }}</div>
            <div class="muted">ID {{ s.id }} · ¥{{ s.avgPrice || 0 }}/人 · 评分 {{ ((s.score || 0) / 10).toFixed(1) }}</div>
          </div>
        </div>

        <div class="divider"></div>

        <div class="section-head">
          <div>
            <div class="title">券列表</div>
            <div class="muted">展示店铺当前券池（普通券/秒杀券）</div>
          </div>
        </div>

        <div v-if="!shop.shopId" style="padding: 10px 0;">
          <van-empty description="先选择一个店铺，再查看券列表" />
        </div>

        <div v-else-if="vouchers.length === 0 && !loading" style="padding: 10px 0;">
          <van-empty description="该店铺暂无优惠券" />
        </div>

        <div class="voucher-list">
          <div class="voucher-card" v-for="v in vouchers" :key="v.id">
            <div class="voucher-top">
              <div class="voucher-title">{{ v.title }}</div>
              <van-tag :type="v.beginTime ? 'danger' : 'primary'" plain size="mini">
                {{ v.beginTime ? '秒杀券' : '普通券' }}
              </van-tag>
            </div>
            <div class="voucher-sub muted">{{ v.subTitle }}</div>
            <div class="voucher-price">
              <span class="pay">¥{{ v.payValue }}</span>
              <span class="arrow">→</span>
              <span class="actual">抵 ¥{{ v.actualValue }}</span>
            </div>
            <div class="voucher-rule muted">{{ v.rules }}</div>
            <div class="muted" v-if="v.beginTime">窗口：{{ v.beginTime }} ~ {{ v.endTime }}</div>
            <div class="muted" v-if="v.beginTime">限购：{{ limitLabel(v.limitType, v.userLimit) }}</div>
          </div>
        </div>

        <div class="log">{{ log }}</div>
      </div>

      <div class="card">
        <div class="section-head">
          <div>
            <div class="title">创建优惠券（平台券）</div>
            <div class="muted">平台券 `ownerType=0`；秒杀券会同时写入 `tb_seckill_voucher`</div>
          </div>
        </div>

        <van-tabs v-model:active="activeTab" animated color="#111827" line-width="40">
          <van-tab title="普通券">
            <van-form @submit="createVoucher">
              <van-cell-group inset>
                <van-field :model-value="shop.shopId ? String(shop.shopId) : ''" label="店铺ID" readonly placeholder="先选择店铺" />
                <van-field v-model="voucherForm.title" label="标题" placeholder="例如：平台补贴券" />
                <van-field v-model="voucherForm.subTitle" label="副标题" placeholder="例如：新客专享" />
                <van-field v-model.number="voucherForm.payValue" type="number" label="支付金额" placeholder="如：10" />
                <van-field v-model.number="voucherForm.actualValue" type="number" label="抵扣金额" placeholder="如：30" />
                <van-field v-model="voucherForm.rules" label="规则" type="textarea" rows="2" autosize placeholder="使用规则" />
              </van-cell-group>
              <div class="form-actions">
                <van-button block type="primary" native-type="submit" :disabled="!shop.shopId">创建普通券</van-button>
              </div>
            </van-form>
          </van-tab>

          <van-tab title="秒杀券">
            <van-form @submit="createSeckill">
              <van-cell-group inset>
                <van-field :model-value="shop.shopId ? String(shop.shopId) : ''" label="店铺ID" readonly placeholder="先选择店铺" />
                <van-field v-model="seckillForm.title" label="标题" placeholder="例如：平台秒杀券" />
                <van-field v-model="seckillForm.subTitle" label="副标题" placeholder="例如：限时抢购" />
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
                <van-field v-model.number="seckillForm.userLimit" type="number" label="单用户限购" placeholder="如：1" />

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
                <van-button block type="danger" native-type="submit" :disabled="!canCreateSeckill">创建秒杀券</van-button>
                <div class="muted" style="margin-top:8px;">提示：创建会预热 Redis 库存键，方便秒杀 Lua 直接扣减</div>
              </div>
            </van-form>
          </van-tab>
        </van-tabs>

        <div class="log">{{ log }}</div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';
import { request } from '../api/http';
import { useSessionStore } from '../stores/session';

const session = useSessionStore();
const route = useRoute();

const activeTab = ref(0);
const vouchers = ref([]);
const log = ref('准备就绪');
const loading = ref(false);

const shop = reactive({
  keyword: '',
  options: [],
  shopId: null,
  shopName: ''
});

const voucherForm = reactive({
  title: '',
  subTitle: '',
  payValue: 10,
  actualValue: 30,
  rules: '满50减20',
  ownerType: 0,
  type: 0,
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
  ownerType: 0,
  type: 1,
  status: 1
});

const canCreateSeckill = computed(() => {
  return Boolean(shop.shopId && seckillForm.title.trim() && seckillForm.beginTime && seckillForm.endTime && seckillForm.stock > 0);
});

function resetAll() {
  vouchers.value = [];
  shop.keyword = '';
  shop.options = [];
  shop.shopId = null;
  shop.shopName = '';
  log.value = '已重置';
}

function limitLabel(limitType, userLimit) {
  const t = Number(limitType || 0);
  if (t === 1) return '一人一单';
  if (t === 2) return '一人多单';
  if (t === 3) return `累计限购 ${userLimit || 1} 单`;
  return '未设置';
}

let shopSearchTimer = null;
function onShopKeywordChange() {
  if (shopSearchTimer) clearTimeout(shopSearchTimer);
  shopSearchTimer = setTimeout(searchShop, 250);
}

async function searchShop() {
  const kw = shop.keyword.trim();
  if (!kw) {
    shop.options = [];
    return;
  }
  try {
    const list = await request(`/shop/of/name?name=${encodeURIComponent(kw)}&current=1`);
    shop.options = (Array.isArray(list) ? list : []).slice(0, 8);
  } catch {
    shop.options = [];
  }
}

function selectShop(s) {
  shop.shopId = s.id;
  shop.shopName = s.name;
  shop.options = [];
  loadVouchers();
}

async function loadVouchers() {
  if (!shop.shopId) return;
  loading.value = true;
  try {
    vouchers.value = await request(`/voucher/list/${shop.shopId}`);
    log.value = '券列表已加载';
  } catch (e) {
    log.value = e?.message || '加载失败';
  } finally {
    loading.value = false;
  }
}

async function createVoucher() {
  if (!shop.shopId) return;
  try {
    const payload = { ...voucherForm, shopId: Number(shop.shopId) };
    const id = await request('/voucher', { method: 'POST', body: payload, token: session.token });
    log.value = `创建普通券成功：${id}`;
    await loadVouchers();
  } catch (e) {
    log.value = e?.message || '创建失败';
  }
}

async function createSeckill() {
  if (!canCreateSeckill.value) return;
  try {
    const payload = { ...seckillForm, shopId: Number(shop.shopId) };
    const id = await request('/voucher/seckill', { method: 'POST', body: payload, token: session.token });
    log.value = `创建秒杀券成功：${id}（Redis 库存已预热）`;
    await loadVouchers();
  } catch (e) {
    log.value = e?.message || '创建失败';
  }
}

onMounted(async () => {
  const qShopId = route.query.shopId ? String(route.query.shopId) : '';
  if (qShopId) {
    shop.shopId = Number(qShopId);
    shop.shopName = `店铺 ${qShopId}`;
    await loadVouchers();
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

.options {
  margin: 12px 16px 0;
  border-radius: 12px;
  border: 1px solid rgba(0, 0, 0, 0.05);
  overflow: hidden;
}

.opt {
  padding: 12px;
  cursor: pointer;
  background: #fff;
  border-bottom: 1px solid rgba(0, 0, 0, 0.04);
}

.opt:last-child {
  border-bottom: none;
}

.opt:hover {
  background: #f3f4f6;
}

.opt-name {
  font-weight: 900;
  color: #111827;
  font-size: 13px;
}

.divider {
  height: 1px;
  background: rgba(0, 0, 0, 0.05);
  margin: 14px 0;
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
  color: #111827;
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
  color: #9ca3af;
}

.actual {
  font-weight: 800;
  color: #111827;
}

.voucher-rule {
  margin-top: 8px;
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
  .grid-2 {
    grid-template-columns: 1fr;
  }
}
</style>
