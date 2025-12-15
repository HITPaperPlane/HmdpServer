<template>
  <div class="card">
    <div class="flex-between">
      <h3 class="title">我的订单</h3>
      <button class="secondary" @click="load">刷新</button>
    </div>
    <div class="row" style="margin-bottom:8px;">
      <input v-model.number="query.current" placeholder="页码" />
      <input v-model.number="query.size" placeholder="条数" />
    </div>
    <table class="table">
      <thead><tr><th>ID</th><th>券ID</th><th>状态</th><th>创建时间</th></tr></thead>
      <tbody>
        <tr v-for="o in orders" :key="o.id">
          <td>{{ o.id }}</td>
          <td>{{ o.voucherId }}</td>
          <td>{{ o.payType || '排队/待消费' }}</td>
          <td>{{ o.createTime }}</td>
        </tr>
      </tbody>
    </table>
    <div class="log">{{ log }}</div>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue';
import { request } from '../api/http';
import { useSessionStore } from '../stores/session';

const session = useSessionStore();
const query = reactive({ current: 1, size: 10 });
const orders = ref([]);
const log = ref('等待操作');

onMounted(() => {
  load();
});

async function load() {
  if (!session.token) {
    log.value = '请先登录';
    return;
  }
  try {
    const params = new URLSearchParams({ current: query.current || 1, size: query.size || 10 });
    orders.value = await request(`/voucher-order/my?${params.toString()}`, { token: session.token });
    log.value = `共 ${orders.value.length} 条`;
  } catch (e) {
    log.value = e.message;
  }
}
</script>
