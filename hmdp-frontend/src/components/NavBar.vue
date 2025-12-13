<template>
  <header class="nav">
    <div class="brand" @click="go('/')">
      <div class="dot"></div>
      <span>HMDP 全端</span>
    </div>
    <nav class="links">
      <RouterLink to="/" @click="setRole('USER')" :class="{ active: route.path.startsWith('/') && !route.path.startsWith('/merchant') && !route.path.startsWith('/admin') }">用户站点</RouterLink>
      <RouterLink to="/merchant/dashboard" @click="setRole('MERCHANT')" :class="{ active: route.path.startsWith('/merchant') }">商家中心</RouterLink>
      <RouterLink to="/admin/dashboard" @click="setRole('ADMIN')" :class="{ active: route.path.startsWith('/admin') }">运营后台</RouterLink>
    </nav>
    <div class="right">
      <span class="pill muted">{{ roleLabel }}</span>
      <span class="muted">{{ session.profile.nickName || '未登录' }}</span>
      <button class="secondary" v-if="session.token" @click="logout">退出</button>
      <button v-else @click="go('/login')">登录</button>
    </div>
  </header>
</template>

<script setup>
import { useRoute, useRouter, RouterLink } from 'vue-router';
import { computed } from 'vue';
import { useSessionStore } from '../stores/session';

const route = useRoute();
const router = useRouter();
const session = useSessionStore();

const roleLabel = computed(() => {
  if (session.role === 'MERCHANT') return '商家';
  if (session.role === 'ADMIN') return '管理员';
  return '用户';
});

function go(path) {
  router.push(path);
}

function logout() {
  session.clearSession();
  router.push('/login');
}

function setRole(role) {
  session.role = role;
}
</script>

<style scoped>
.nav {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid var(--border);
  padding: 14px 18px;
  box-shadow: 0 12px 28px rgba(15,23,42,0.08);
}
.brand { font-weight: 800; font-size: 18px; cursor: pointer; display: flex; align-items: center; gap: 8px; letter-spacing: 0.5px; }
.dot { width: 14px; height: 14px; border-radius: 50%; background: linear-gradient(120deg, #0ea5e9, #22c55e); box-shadow: 0 6px 14px rgba(14,165,233,0.35); }
.links { display: flex; gap: 12px; }
.links a { text-decoration: none; color: var(--text); padding: 10px 14px; border-radius: 12px; font-weight: 700; transition: all 0.15s ease; }
.links a:hover { background: rgba(14,165,233,0.08); }
.links a.active { background: rgba(14,165,233,0.14); color: var(--primary); box-shadow: inset 0 0 0 1px rgba(14,165,233,0.2); }
.right { display: flex; gap: 8px; align-items: center; }
.pill {
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(15,23,42,0.06);
  border: 1px solid var(--border);
  font-size: 12px;
}
</style>
