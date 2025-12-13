<template>
  <div class="grid">
    <div class="card">
      <h3 class="title">优惠券/秒杀管理</h3>
      <div class="row">
        <input v-model="targetShopId" placeholder="店铺ID" @keyup.enter="loadVouchers" />
        <button class="secondary" @click="loadVouchers">查看券列表</button>
      </div>
      <ul class="list">
        <li v-for="v in vouchers" :key="v.id">
          <div class="flex-between">
            <div>
              <strong>{{ v.title }}</strong>
              <div class="muted">券ID: {{ v.id }} | 店铺 {{ v.shopId }}</div>
            </div>
            <span class="pill">¥{{ v.payValue }} 抵 ¥{{ v.actualValue }}</span>
          </div>
          <div class="muted">
            {{ v.beginTime ? `秒杀窗口 ${v.beginTime} ~ ${v.endTime}` : '普通券' }}
          </div>
        </li>
      </ul>
      <div class="log">{{ log }}</div>
    </div>
    <div class="card">
      <h4 class="title">创建普通券</h4>
      <div class="grid" style="grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));">
        <div>
          <label>店铺ID</label>
          <input v-model="voucherForm.shopId" placeholder="必填" />
        </div>
        <div>
          <label>标题</label>
          <input v-model="voucherForm.title" />
        </div>
        <div>
          <label>副标题</label>
          <input v-model="voucherForm.subTitle" />
        </div>
        <div>
          <label>支付金额</label>
          <input type="number" v-model.number="voucherForm.payValue" />
        </div>
        <div>
          <label>抵扣金额</label>
          <input type="number" v-model.number="voucherForm.actualValue" />
        </div>
        <div>
          <label>规则</label>
          <textarea v-model="voucherForm.rules"></textarea>
        </div>
      </div>
      <button @click="createVoucher">提交普通券</button>
    </div>
    <div class="card">
      <h4 class="title">创建秒杀券（预热 Redis 库存）</h4>
      <div class="grid" style="grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));">
        <div>
          <label>店铺ID</label>
          <input v-model="seckillForm.shopId" />
        </div>
        <div>
          <label>标题</label>
          <input v-model="seckillForm.title" />
        </div>
        <div>
          <label>副标题</label>
          <input v-model="seckillForm.subTitle" />
        </div>
        <div>
          <label>支付金额</label>
          <input type="number" v-model.number="seckillForm.payValue" />
        </div>
        <div>
          <label>抵扣金额</label>
          <input type="number" v-model.number="seckillForm.actualValue" />
        </div>
        <div>
          <label>库存</label>
          <input type="number" v-model.number="seckillForm.stock" />
        </div>
        <div>
          <label>限购类型</label>
          <select v-model.number="seckillForm.limitType">
            <option :value="1">一人一单</option>
            <option :value="2">一人多单</option>
            <option :value="3">累计限购</option>
          </select>
        </div>
        <div>
          <label>单用户限购数</label>
          <input type="number" v-model.number="seckillForm.userLimit" />
        </div>
        <div>
          <label>开始时间</label>
          <input type="datetime-local" v-model="seckillForm.beginTime" />
        </div>
        <div>
          <label>结束时间</label>
          <input type="datetime-local" v-model="seckillForm.endTime" />
        </div>
        <div>
          <label>规则</label>
          <textarea v-model="seckillForm.rules"></textarea>
        </div>
      </div>
      <button @click="createSeckill">提交秒杀券</button>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { request } from '../api/http';
import { useSessionStore } from '../stores/session';

const session = useSessionStore();
const targetShopId = ref('');
const vouchers = ref([]);
const log = ref('等待操作');
const voucherForm = reactive({
  shopId: '',
  title: '',
  subTitle: '',
  payValue: 0,
  actualValue: 0,
  rules: '满50减20',
  ownerType: 1,
  type: 0,
  status: 1
});
const seckillForm = reactive({
  shopId: '',
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

async function loadVouchers() {
  if (!targetShopId.value) return;
  try {
    vouchers.value = await request(`/voucher/list/${targetShopId.value}`);
    log.value = '券列表已加载';
  } catch (e) {
    log.value = e.message;
  }
}

async function createVoucher() {
  try {
    const id = await request('/voucher', { method: 'POST', body: voucherForm, token: session.token });
    log.value = `创建普通券成功：${id}`;
    if (!targetShopId.value) targetShopId.value = voucherForm.shopId;
    loadVouchers();
  } catch (e) {
    log.value = e.message;
  }
}

function normalizeTime(str) {
  if (!str) return '';
  return str.endsWith('Z') ? str : str;
}

async function createSeckill() {
  try {
    const payload = { ...seckillForm, beginTime: normalizeTime(seckillForm.beginTime), endTime: normalizeTime(seckillForm.endTime) };
    const id = await request('/voucher/seckill', { method: 'POST', body: payload, token: session.token });
    log.value = `创建秒杀券成功：${id}（Redis 库存已预热）`;
    if (!targetShopId.value) targetShopId.value = seckillForm.shopId;
    loadVouchers();
  } catch (e) {
    log.value = e.message;
  }
}

if (session.role !== 'MERCHANT') {
  session.role = 'MERCHANT';
}
</script>
