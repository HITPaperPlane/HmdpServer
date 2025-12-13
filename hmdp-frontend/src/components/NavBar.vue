<template>
  <header class="nav">
    <div class="container">
      <div class="brand" @click="router.push('/')">
        <div class="logo-icon">评</div>
        <span class="logo-text">HMDP 全端</span>
      </div>

      <nav class="nav-links">
        <RouterLink to="/" active-class="active">首页</RouterLink>
        <RouterLink to="/blogs" active-class="active">探店笔记</RouterLink>
        <RouterLink v-if="session.role === 'MERCHANT'" to="/merchant/dashboard" active-class="active">商家中心</RouterLink>
        <RouterLink v-if="session.role === 'ADMIN'" to="/admin/dashboard" active-class="active">系统管理</RouterLink>
      </nav>

      <div class="user-actions">
        <template v-if="session.token">
          <div class="user-profile">
            <div class="avatar-placeholder">{{ avatarText }}</div>
            <span class="nickname">{{ session.profile.nickName || '用户' }}</span>
            <button class="logout-link" @click="logout">退出</button>
          </div>
        </template>
        <template v-else>
          <button class="login-btn" @click="showLoginModal = true">登录 / 注册</button>
        </template>
      </div>
    </div>

    <Teleport to="body">
      <Transition name="fade">
        <LoginModal v-if="showLoginModal" @close="showLoginModal = false" />
      </Transition>
    </Teleport>
  </header>
</template>

<script setup>
import { ref, computed } from 'vue';
import { useRouter, RouterLink } from 'vue-router';
import { useSessionStore } from '../stores/session';
import LoginModal from './LoginModal.vue';

const router = useRouter();
const session = useSessionStore();
const showLoginModal = ref(false);

const avatarText = computed(() => {
  const name = session.profile.nickName || 'User';
  return name.charAt(0).toUpperCase();
});

function logout() {
  if(confirm('确定要退出登录吗？')) {
    session.clearSession();
    router.push('/');
  }
}
</script>

<style scoped>
.nav {
  position: sticky; top: 0; z-index: 100;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px); /* 就是这个属性导致了定位问题 */
  border-bottom: 1px solid #f0f0f0;
  height: 64px;
}
.container {
  max-width: 1200px; margin: 0 auto; height: 100%; padding: 0 20px;
  display: flex; align-items: center; justify-content: space-between;
}

/* 品牌 Logo */
.brand { display: flex; align-items: center; cursor: pointer; gap: 10px; }
.logo-icon {
  width: 32px; height: 32px; background: #f63; color: white; border-radius: 8px;
  display: flex; align-items: center; justify-content: center; font-weight: bold; font-size: 18px;
}
.logo-text { font-size: 20px; font-weight: 800; color: #333; letter-spacing: -0.5px; }

/* 导航链接 */
.nav-links { display: flex; gap: 30px; margin-left: 40px; flex: 1; }
.nav-links a {
  text-decoration: none; color: #666; font-weight: 500; font-size: 15px; position: relative; transition: color 0.2s;
}
.nav-links a:hover { color: #f63; }
.nav-links a.active { color: #f63; font-weight: 700; }
.nav-links a.active::after {
  content: ''; position: absolute; bottom: -22px; left: 0; width: 100%; height: 2px; background: #f63;
}

/* 右侧按钮 */
.user-actions { display: flex; align-items: center; }
.login-btn {
  padding: 8px 20px; background: #333; color: #fff; border-radius: 20px; border: none; font-size: 14px; font-weight: 600; cursor: pointer; transition: background 0.2s;
}
.login-btn:hover { background: #000; }

.user-profile { display: flex; align-items: center; gap: 10px; }
.avatar-placeholder {
  width: 32px; height: 32px; background: #f0f2f5; color: #666; border-radius: 50%;
  display: flex; align-items: center; justify-content: center; font-size: 14px; font-weight: bold;
}
.nickname { font-size: 14px; color: #333; max-width: 100px; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.logout-link {
  background: none; border: none; color: #999; font-size: 13px; cursor: pointer; padding: 0 5px;
}
.logout-link:hover { color: #666; }

/* 简单的过渡动画 */
.fade-enter-active, .fade-leave-active { transition: opacity 0.2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>