<template>
  <div class="grid">
    <div class="card">
      <h3 class="title">账户</h3>
      <div class="muted">{{ user.nickName ? `已登录：${user.nickName}` : '未登录' }}</div>
      <div class="row" style="margin-top:8px;">
        <button class="secondary" @click="loadMe">刷新</button>
        <button class="secondary" @click="goLogin">去登录/切换</button>
        <button class="secondary" @click="clearToken">退出</button>
      </div>
      <div class="log">{{ infoLog }}</div>
    </div>

    <div class="card">
      <h3 class="title">签到 / UV</h3>
      <div class="row">
        <button class="secondary" @click="sign">签到</button>
        <button class="secondary" @click="signCount">连续签到</button>
      </div>
      <div class="row" style="margin-top:8px;">
        <button class="secondary" @click="uvCollect">UV 采集</button>
        <button class="secondary" @click="uvQuery">近3日 UV</button>
      </div>
      <div class="log">{{ log }}</div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { request } from '../api/http';
import { useSessionStore } from '../stores/session';

const session = useSessionStore();
const router = useRouter();
const user = reactive({ nickName: '' });
const infoLog = ref('未加载');
const log = ref('等待操作');

onMounted(() => {
  session.role = 'USER';
  if (session.token) loadMe();
});

async function loadMe() {
  try {
    const data = await request('/user/me', { token: session.token });
    user.nickName = data?.nickName || '';
    infoLog.value = JSON.stringify(data, null, 2);
  } catch (e) {
    infoLog.value = e.message;
  }
}

async function sign() {
  try {
    await request('/user/sign', { method: 'POST', token: session.token });
    log.value = '签到成功';
  } catch (e) {
    log.value = e.message;
  }
}

async function signCount() {
  try {
    const data = await request('/user/sign/count', { token: session.token });
    log.value = `连续签到 ${data} 天`;
  } catch (e) {
    log.value = e.message;
  }
}

async function uvCollect() {
  try {
    const data = await request('/user/uv', { method: 'POST', token: session.token });
    log.value = `今日 UV: ${data}`;
  } catch (e) {
    log.value = e.message;
  }
}

async function uvQuery() {
  try {
    const data = await request('/user/uv?days=3', { token: session.token });
    log.value = `近3日 UV: ${data}`;
  } catch (e) {
    log.value = e.message;
  }
}

function goLogin() {
  router.push('/login');
}

function clearToken() {
  session.clearSession();
  user.nickName = '';
}
</script>
