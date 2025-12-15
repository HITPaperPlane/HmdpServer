<template>
  <div class="modal-mask">
    <div class="modal-wrapper">
      <div class="modal-container">
        <button class="close-btn" @click="$emit('close')">×</button>

        <h3 class="modal-title">欢迎登录 HMDP</h3>

        <div class="tabs">
          <div class="tab-item" :class="{ active: tab === 'USER' }" @click="tab = 'USER'">普通用户</div>
          <div class="tab-item" :class="{ active: tab === 'MERCHANT' }" @click="tab = 'MERCHANT'">我是商家</div>
          <div class="tab-item" :class="{ active: tab === 'ADMIN' }" @click="tab = 'ADMIN'">管理员</div>
        </div>

        <div class="form-body">
          <template v-if="tab !== 'ADMIN'">
            <div class="input-group">
              <input v-model="form.email" type="text" placeholder="请输入邮箱" />
            </div>
            <div class="input-group row">
              <input v-model="form.code" type="text" placeholder="请输入验证码" />
              <button class="code-btn" :disabled="loading.code || countdown > 0" @click="sendCode">
                {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
              </button>
            </div>
            <div class="tips" v-if="tab === 'USER'">未注册的邮箱验证通过后将自动注册</div>
          </template>

          <template v-else>
            <div class="input-group">
              <input v-model="admin.username" type="text" placeholder="管理员账号" />
            </div>
            <div class="input-group">
              <input v-model="admin.password" type="password" placeholder="请输入密码" />
            </div>
          </template>

          <div class="error-msg">{{ log }}</div>

          <button class="submit-btn" :disabled="loading.login" @click="login">
            {{ loading.login ? '登录中...' : '立即登录' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { request } from '../api/http';
import { useSessionStore } from '../stores/session';
import { useRouter } from 'vue-router';

const emit = defineEmits(['close', 'success']);
const session = useSessionStore();
const router = useRouter();

const tab = ref('USER');
const form = reactive({ email: '', code: '' });
const admin = reactive({ username: '', password: '' });
const loading = reactive({ code: false, login: false });
const log = ref('');
const countdown = ref(0);

// 发送验证码
async function sendCode() {
  if (!form.email) {
    log.value = '请填写邮箱';
    return;
  }
  loading.code = true;
  log.value = '';
  try {
    if (tab.value === 'MERCHANT') {
      await request('/merchant/code', { method: 'POST', body: { email: form.email } });
    } else {
      await request(`/user/code?email=${encodeURIComponent(form.email)}`, { method: 'POST' });
    }
    // 开始倒计时
    countdown.value = 60;
    const timer = setInterval(() => {
      countdown.value--;
      if (countdown.value <= 0) clearInterval(timer);
    }, 1000);
    log.value = '验证码已发送，请查收（模拟环境请查看后台日志或Redis）';
  } catch (e) {
    log.value = e.message;
  } finally {
    loading.code = false;
  }
}

// 获取用户信息
async function fetchProfile(token) {
  try {
    const profile = await request('/user/me', { token });
    return profile || {};
  } catch (e) {
    return {};
  }
}

// 登录逻辑
async function login() {
  loading.login = true;
  log.value = '';
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

    // 登录成功
    emit('success');
    emit('close');

    // 如果是管理角色，跳转到对应后台，否则留在当前页面
    if (role === 'ADMIN') router.push('/admin/dashboard');
    if (role === 'MERCHANT') router.push('/merchant/dashboard');

  } catch (e) {
    log.value = '登录失败：' + e.message;
  } finally {
    loading.login = false;
  }
}
</script>

<style scoped>
.modal-mask {
  position: fixed; z-index: 9998; top: 0; left: 0; width: 100%; height: 100%;
  background-color: rgba(0, 0, 0, 0.5); display: flex; justify-content: center; align-items: center;
  backdrop-filter: blur(4px);
}
.modal-wrapper { width: 100%; max-width: 400px; padding: 20px; }
.modal-container {
  background: #fff; border-radius: 16px; padding: 30px; position: relative;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
}
.close-btn {
  position: absolute; top: 15px; right: 15px; background: none; border: none; font-size: 24px; color: #999; cursor: pointer;
}
.modal-title { margin-top: 0; text-align: center; color: #333; font-size: 20px; margin-bottom: 24px; }

.tabs { display: flex; border-bottom: 1px solid #eee; margin-bottom: 24px; }
.tab-item {
  flex: 1; text-align: center; padding: 12px 0; cursor: pointer; color: #666; font-weight: 500;
  border-bottom: 2px solid transparent; transition: all 0.3s;
}
.tab-item.active { color: #f63; border-bottom-color: #f63; }

.input-group { margin-bottom: 16px; }
.input-group input {
  width: 100%; padding: 12px 14px; border: 1px solid #ddd; border-radius: 8px; font-size: 14px; outline: none; transition: border-color 0.2s;
}
.input-group input:focus { border-color: #f63; }
.input-group.row { display: flex; gap: 10px; }
.code-btn {
  white-space: nowrap; padding: 0 16px; background: #fff; border: 1px solid #f63; color: #f63; border-radius: 8px; cursor: pointer; font-size: 13px;
}
.code-btn:disabled { border-color: #ddd; color: #999; cursor: not-allowed; }

.tips { font-size: 12px; color: #999; margin-bottom: 16px; }
.error-msg { color: #ef4444; font-size: 13px; margin-bottom: 12px; min-height: 20px; text-align: center; }

.submit-btn {
  width: 100%; padding: 12px; background: linear-gradient(135deg, #ff8e53 0%, #ff6b6b 100%);
  color: #fff; border: none; border-radius: 8px; font-size: 16px; font-weight: bold; cursor: pointer;
  box-shadow: 0 4px 12px rgba(255, 107, 107, 0.3); transition: transform 0.1s;
}
.submit-btn:active { transform: scale(0.98); }
.submit-btn:disabled { background: #ccc; box-shadow: none; cursor: not-allowed; }
</style>
