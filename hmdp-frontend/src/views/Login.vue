<template>
  <div class="grid">
    <div class="card">
      <h3 class="title">登录中心</h3>
      <div class="row">
        <button class="secondary" :class="{ active: tab === 'USER' }" @click="tab = 'USER'">用户</button>
        <button class="secondary" :class="{ active: tab === 'MERCHANT' }" @click="tab = 'MERCHANT'">商家</button>
        <button class="secondary" :class="{ active: tab === 'ADMIN' }" @click="tab = 'ADMIN'">管理员</button>
      </div>

      <template v-if="tab !== 'ADMIN'">
        <label>手机号 / 邮箱</label>
        <input v-model="form.phone" placeholder="user@example.com" />
        <label>验证码</label>
        <div class="row">
          <input v-model="form.code" placeholder="收到的验证码" />
          <button class="secondary" :disabled="loading.code" @click="sendCode">发送</button>
        </div>
      </template>

      <template v-else>
        <label>管理员账号</label>
        <input v-model="admin.username" placeholder="admin" />
        <label>密码</label>
        <input type="password" v-model="admin.password" placeholder="Admin#123456" />
      </template>

      <button :disabled="loading.login" @click="login">{{ loginText }}</button>
      <div class="log">{{ log }}</div>
    </div>
    <div class="card">
      <h4 class="title">提示</h4>
      <ul class="list">
        <li>用户：手机号/邮箱 + 验证码，接口 `/user/code`、`/user/login`。</li>
        <li>商家：邮箱 + 验证码，接口 `/merchant/code`、`/merchant/login`（首次会生成商家角色）。</li>
        <li>管理员：固定账号密码，接口 `/admin/login`。</li>
      </ul>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, computed } from 'vue';
import { request } from '../api/http';
import { useSessionStore } from '../stores/session';
import { useRouter } from 'vue-router';

const session = useSessionStore();
const router = useRouter();
const tab = ref('USER');
const form = reactive({ phone: '', code: '' });
const admin = reactive({ username: 'admin', password: 'Admin#123456' });
const loading = reactive({ code: false, login: false });
const log = ref('等待登录');

const loginText = computed(() => {
  if (tab.value === 'MERCHANT') return '登录商家中心';
  if (tab.value === 'ADMIN') return '登录运营后台';
  return '登录用户站';
});

async function sendCode() {
  if (!form.phone) {
    log.value = '请填写手机号/邮箱';
    return;
  }
  loading.code = true;
  try {
    if (tab.value === 'MERCHANT') {
      await request('/merchant/code', { method: 'POST', body: { phone: form.phone } });
    } else {
      await request(`/user/code?phone=${encodeURIComponent(form.phone)}`, { method: 'POST' });
    }
    log.value = '验证码已发送';
  } catch (e) {
    log.value = e.message;
  } finally {
    loading.code = false;
  }
}

async function fetchProfile(token) {
  try {
    const profile = await request('/user/me', { token });
    return profile || {};
  } catch (e) {
    log.value = `登录成功，读取资料失败：${e.message}`;
    return {};
  }
}

async function login() {
  loading.login = true;
  try {
    let token = '';
    let role = 'USER';
    if (tab.value === 'MERCHANT') {
      token = await request('/merchant/login', { method: 'POST', body: form });
      role = 'MERCHANT';
    } else if (tab.value === 'ADMIN') {
      token = await request('/admin/login', { method: 'POST', body: admin });
      role = 'ADMIN';
    } else {
      token = await request('/user/login', { method: 'POST', body: form });
      role = 'USER';
    }
    const profile = await fetchProfile(token);
    session.setSession({ token, role, profile });
    log.value = '登录成功';
    router.push(role === 'ADMIN' ? '/admin/dashboard' : role === 'MERCHANT' ? '/merchant/dashboard' : '/');
  } catch (e) {
    log.value = e.message;
  } finally {
    loading.login = false;
  }
}
</script>
