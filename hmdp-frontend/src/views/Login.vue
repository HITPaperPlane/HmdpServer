<template>
  <div class="login-page">
    <div class="bg"></div>

    <div class="panel">
      <div class="brand">
        <div class="logo">评</div>
        <div>
          <div class="title">HMDP 登录</div>
          <div class="sub">支持普通用户 / 商家 / 管理员 三种身份</div>
        </div>
      </div>

      <van-tabs v-model:active="tab" animated color="#ff6b6b" line-width="40">
        <van-tab title="普通用户" name="USER">
          <div class="tip">未注册的邮箱验证通过后将自动注册</div>
          <van-form @submit="login">
            <van-cell-group inset>
              <van-field v-model="form.email" label="邮箱" placeholder="请输入邮箱" clearable />
              <van-field v-model="form.code" label="验证码" placeholder="请输入验证码" clearable>
                <template #button>
                  <van-button
                      size="small"
                      type="primary"
                      plain
                      :disabled="loading.code || countdown > 0"
                      @click.prevent="sendCode"
                  >
                    {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
                  </van-button>
                </template>
              </van-field>
            </van-cell-group>
            <div class="actions">
              <van-button round block type="primary" native-type="submit" :loading="loading.login">登录 / 注册</van-button>
            </div>
          </van-form>
        </van-tab>

        <van-tab title="我是商家" name="MERCHANT">
          <div class="tip">商家登录后可进入商家中心：店铺/券/内容营销</div>
          <van-form @submit="login">
            <van-cell-group inset>
              <van-field v-model="form.email" label="邮箱" placeholder="请输入邮箱" clearable />
              <van-field v-model="form.code" label="验证码" placeholder="请输入验证码" clearable>
                <template #button>
                  <van-button
                      size="small"
                      type="primary"
                      plain
                      :disabled="loading.code || countdown > 0"
                      @click.prevent="sendCode"
                  >
                    {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
                  </van-button>
                </template>
              </van-field>
            </van-cell-group>
            <div class="actions">
              <van-button round block type="primary" native-type="submit" :loading="loading.login">立即登录</van-button>
            </div>
          </van-form>
        </van-tab>

        <van-tab title="管理员" name="ADMIN">
          <div class="tip">默认管理员账号：admin / Admin#123456</div>
          <van-form @submit="login">
            <van-cell-group inset>
              <van-field v-model="admin.username" label="账号" placeholder="admin" clearable />
              <van-field v-model="admin.password" type="password" label="密码" placeholder="Admin#123456" clearable />
            </van-cell-group>
            <div class="actions">
              <van-button round block type="primary" native-type="submit" :loading="loading.login">进入后台</van-button>
            </div>
          </van-form>
        </van-tab>
      </van-tabs>

      <van-notice-bar
          v-if="log"
          class="notice"
          color="#ef4444"
          background="#fff1f2"
          left-icon="warning-o"
          :text="log"
      />

      <div class="footer">
        <span class="muted">提示：验证码发送后可在后端日志或 Redis 中查看（教学环境）</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { request } from '../api/http';
import { useSessionStore } from '../stores/session';

const route = useRoute();
const router = useRouter();
const session = useSessionStore();

const tab = ref('USER');
const form = reactive({ email: '', code: '' });
const admin = reactive({ username: '', password: '' });
const loading = reactive({ code: false, login: false });
const log = ref('');
const countdown = ref(0);

async function sendCode() {
  if (!form.email.trim()) {
    log.value = '请先填写邮箱';
    return;
  }
  loading.code = true;
  log.value = '';
  try {
    if (tab.value === 'MERCHANT') {
      await request('/merchant/code', { method: 'POST', body: { email: form.email.trim() } });
    } else {
      await request(`/user/code?email=${encodeURIComponent(form.email.trim())}`, { method: 'POST' });
    }
    countdown.value = 60;
    const timer = setInterval(() => {
      countdown.value -= 1;
      if (countdown.value <= 0) clearInterval(timer);
    }, 1000);
    log.value = '验证码已发送，请查收（教学环境可查看后端日志或 Redis）';
  } catch (e) {
    log.value = e?.message || '发送失败';
  } finally {
    loading.code = false;
  }
}

async function fetchProfile(token) {
  try {
    return await request('/user/me', { token });
  } catch {
    return {};
  }
}

async function login() {
  loading.login = true;
  log.value = '';
  try {
    let token = '';
    if (tab.value === 'MERCHANT') {
      token = await request('/merchant/login', { method: 'POST', body: { email: form.email.trim(), code: form.code.trim() } });
    } else if (tab.value === 'ADMIN') {
      token = await request('/admin/login', { method: 'POST', body: admin });
    } else {
      token = await request('/user/login', { method: 'POST', body: { email: form.email.trim(), code: form.code.trim() } });
    }

    const profile = await fetchProfile(token);
    const role = profile?.role || tab.value;
    session.setSession({ token, role, profile: profile || {} });

    const redirect = route.query.redirect ? String(route.query.redirect) : '';
    if (redirect) {
      router.replace(redirect);
      return;
    }
    if (role === 'ADMIN') router.replace('/admin/dashboard');
    else if (role === 'MERCHANT') router.replace('/merchant/dashboard');
    else router.replace('/');
  } catch (e) {
    log.value = '登录失败：' + (e?.message || '未知错误');
  } finally {
    loading.login = false;
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.bg {
  position: absolute;
  inset: -40px;
  background:
      radial-gradient(circle at 15% 20%, rgba(255, 107, 107, 0.25), transparent 45%),
      radial-gradient(circle at 90% 10%, rgba(6, 182, 212, 0.22), transparent 40%),
      radial-gradient(circle at 60% 90%, rgba(168, 85, 247, 0.22), transparent 45%),
      linear-gradient(135deg, #fff 0%, #f7f8fa 100%);
  filter: blur(0);
}

.panel {
  position: relative;
  z-index: 2;
  width: 520px;
  max-width: 92vw;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 20px;
  padding: 18px;
  box-shadow: 0 30px 50px rgba(0, 0, 0, 0.08);
  backdrop-filter: blur(10px);
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 6px 14px;
}

.logo {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 900;
  color: #fff;
  background: linear-gradient(135deg, #ff6b6b 0%, #ff8e53 100%);
}

.title {
  font-size: 18px;
  font-weight: 900;
  color: #111;
}

.sub {
  margin-top: 4px;
  font-size: 12px;
  color: #6b7280;
}

.tip {
  margin: 10px 6px 6px;
  font-size: 12px;
  color: #6b7280;
}

.actions {
  margin: 16px;
}

.notice {
  margin-top: 12px;
  border-radius: 12px;
}

.footer {
  margin-top: 12px;
  padding: 0 6px 6px;
}

.muted {
  color: #9ca3af;
  font-size: 12px;
}
</style>
