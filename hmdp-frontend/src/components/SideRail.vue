<template>
  <aside class="side-rail">
    <div class="role-card">
      <div class="avatar">{{ avatarText }}</div>
      <div class="info">
        <div class="name">{{ session.profile.nickName || '未设置昵称' }}</div>
        <div class="role-badge">{{ roleLabel }}</div>
      </div>
    </div>

    <nav class="nav-menu">
      <router-link
          v-for="item in currentMenu"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          active-class="active"
      >
        <span class="icon">{{ item.icon }}</span>
        <span class="label">{{ item.label }}</span>
      </router-link>
    </nav>

    <div class="bottom-actions">
      <div class="date-display">{{ today }}</div>
    </div>
  </aside>
</template>

<script setup>
import { computed } from 'vue';
import { useSessionStore } from '../stores/session';

const session = useSessionStore();

const avatarText = computed(() => {
  return (session.profile.nickName || 'U').charAt(0).toUpperCase();
});

const roleLabel = computed(() => {
  const map = { 'USER': '普通用户', 'MERCHANT': '商家入驻', 'ADMIN': '超级管理员' };
  return map[session.role] || '访客';
});

const today = new Date().toLocaleDateString();

// 定义不同角色的菜单配置
const menus = {
  USER: [
    { label: '个人中心', path: '/profile', icon: '👤' },
    { label: '我的订单', path: '/orders', icon: '🧾' },
    { label: '浏览首页', path: '/', icon: '🏠' }, // 方便用户切回首页
    { label: '探店笔记', path: '/blogs', icon: '📖' },
  ],
  MERCHANT: [
    { label: '数据看板', path: '/merchant/dashboard', icon: '📊' },
    { label: '店铺管理', path: '/merchant/shops', icon: '🏪' },
    { label: '优惠券管理', path: '/merchant/vouchers', icon: '🎟️' },
    { label: '内容营销', path: '/merchant/content', icon: '✍️' },
  ],
  ADMIN: [
    { label: '系统概览', path: '/admin/dashboard', icon: '🖥️' },
    { label: '商户审核', path: '/admin/shops', icon: '✅' },
    { label: '营销监管', path: '/admin/vouchers', icon: '👮' },
    { label: '社区内容', path: '/admin/blogs', icon: '💬' },
  ]
};

const currentMenu = computed(() => {
  return menus[session.role] || [];
});
</script>

<style scoped>
.side-rail {
  width: 240px;
  background: #fff;
  border-right: 1px solid #eee;
  display: flex;
  flex-direction: column;
  height: calc(100vh - 64px); /* 减去顶部导航高度 */
  position: sticky;
  top: 64px;
  flex-shrink: 0;
}

.role-card {
  padding: 24px 20px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid #f5f5f5;
  background: linear-gradient(to bottom, #fafafa, #fff);
}
.avatar {
  width: 48px; height: 48px;
  background: #f0f2f5;
  color: #666;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-weight: bold; font-size: 18px;
  border: 2px solid #fff;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
}
.info { flex: 1; overflow: hidden; }
.name { font-weight: 600; font-size: 14px; color: #333; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.role-badge {
  display: inline-block; font-size: 10px; padding: 2px 6px;
  background: #e6f7ff; color: #1890ff; border-radius: 4px; margin-top: 4px;
}

.nav-menu { flex: 1; padding: 16px 12px; overflow-y: auto; }
.nav-item {
  display: flex; align-items: center; gap: 12px;
  padding: 12px 16px;
  color: #666;
  text-decoration: none;
  border-radius: 8px;
  margin-bottom: 4px;
  transition: all 0.2s;
}
.nav-item:hover { background: #f5f5f5; color: #333; }
.nav-item.active {
  background: #fff0e6;
  color: #f63;
  font-weight: 600;
}
.nav-item .icon { font-size: 18px; }

.bottom-actions {
  padding: 20px;
  border-top: 1px solid #f5f5f5;
  text-align: center;
}
.date-display {
  font-size: 12px; color: #bbb;
  background: #f9f9f9; padding: 6px; border-radius: 4px;
}
</style>