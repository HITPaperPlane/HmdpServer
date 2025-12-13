<template>
  <div class="grid">
    <div class="card">
      <h3 class="title">券池管理</h3>
      <div class="row">
        <input v-model="shopId" placeholder="店铺ID" @keyup.enter="load" />
        <button class="secondary" @click="load">查看券</button>
      </div>
      <ul class="list">
        <li v-for="v in list" :key="v.id">
          <div class="flex-between">
            <div>
              <strong>{{ v.title }}</strong>
              <div class="muted">券ID: {{ v.id }} | 店铺 {{ v.shopId }}</div>
            </div>
            <span class="pill">{{ v.beginTime ? '秒杀券' : '普通券' }}</span>
          </div>
          <div class="muted">支付 ¥{{ v.payValue }} 抵 ¥{{ v.actualValue }} | {{ v.rules }}</div>
          <div class="muted" v-if="v.beginTime">窗口: {{ v.beginTime }} ~ {{ v.endTime }}</div>
        </li>
      </ul>
      <div class="log">{{ log }}</div>
    </div>
    <div class="card">
      <h4 class="title">新增普通券</h4>
      <div class="grid" style="grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));">
        <div><label>店铺ID</label><input v-model="voucher.shopId" /></div>
        <div><label>标题</label><input v-model="voucher.title" /></div>
        <div><label>副标题</label><input v-model="voucher.subTitle" /></div>
        <div><label>支付金额</label><input type="number" v-model.number="voucher.payValue" /></div>
        <div><label>抵扣金额</label><input type="number" v-model.number="voucher.actualValue" /></div>
        <div><label>规则</label><textarea v-model="voucher.rules"></textarea></div>
      </div>
      <button @click="createNormal">创建普通券</button>
    </div>
    <div class="card">
      <h4 class="title">新增秒杀券（含库存）</h4>
      <div class="grid" style="grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));">
        <div><label>店铺ID</label><input v-model="seckill.shopId" /></div>
        <div><label>标题</label><input v-model="seckill.title" /></div>
        <div><label>副标题</label><input v-model="seckill.subTitle" /></div>
        <div><label>支付金额</label><input type="number" v-model.number="seckill.payValue" /></div>
        <div><label>抵扣金额</label><input type="number" v-model.number="seckill.actualValue" /></div>
        <div><label>库存</label><input type="number" v-model.number="seckill.stock" /></div>
        <div>
          <label>限购类型</label>
          <select v-model.number="seckill.limitType">
            <option :value="1">一人一单</option>
            <option :value="2">一人多单</option>
            <option :value="3">累计限购</option>
          </select>
        </div>
        <div><label>用户限购数</label><input type="number" v-model.number="seckill.userLimit" /></div>
        <div><label>开始时间</label><input type="datetime-local" v-model="seckill.beginTime" /></div>
        <div><label>结束时间</label><input type="datetime-local" v-model="seckill.endTime" /></div>
        <div><label>规则</label><textarea v-model="seckill.rules"></textarea></div>
      </div>
      <button @click="createSeckill">创建秒杀券</button>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { request } from '../api/http';
import { useSessionStore } from '../stores/session';

const session = useSessionStore();
const shopId = ref('');
const list = ref([]);
const log = ref('等待操作');
const voucher = reactive({
  shopId: '',
  title: '',
  subTitle: '',
  payValue: 10,
  actualValue: 30,
  rules: '满50减20',
  type: 0,
  ownerType: 0,
  status: 1
});
const seckill = reactive({
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
  type: 1,
  ownerType: 0,
  status: 1
});

async function load() {
  if (!shopId.value) return;
  try {
    list.value = await request(`/voucher/list/${shopId.value}`);
    log.value = '券列表已加载';
  } catch (e) {
    log.value = e.message;
  }
}

async function createNormal() {
  try {
    const id = await request('/voucher', { method: 'POST', body: voucher, token: session.token });
    log.value = `普通券已创建：${id}`;
    if (!shopId.value) shopId.value = voucher.shopId;
    load();
  } catch (e) {
    log.value = e.message;
  }
}

async function createSeckill() {
  try {
    const payload = { ...seckill };
    const id = await request('/voucher/seckill', { method: 'POST', body: payload, token: session.token });
    log.value = `秒杀券创建：${id}（Redis 库存预热）`;
    if (!shopId.value) shopId.value = seckill.shopId;
    load();
  } catch (e) {
    log.value = e.message;
  }
}

if (session.role !== 'ADMIN') session.role = 'ADMIN';
</script>
