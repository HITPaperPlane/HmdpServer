<template>
  <aside class="rail">
    <div class="rail-title">{{ title }}</div>
    <nav class="rail-links">
      <RouterLink
        v-for="item in currentMenu"
        :key="item.to"
        :to="item.to"
        :class="{ active: route.path.startsWith(item.activePrefix || item.to) }"
      >
        <span class="dot"></span>
        {{ item.label }}
      </RouterLink>
    </nav>
  </aside>
</template>

<script setup>
import { computed } from 'vue';
import { useRoute, RouterLink } from 'vue-router';
import { useSessionStore } from '../stores/session';

const route = useRoute();
const session = useSessionStore();

const menus = {
  USER: [
    { label: '发现店铺', to: '/', activePrefix: '/' },
    { label: '热门笔记', to: '/blogs', activePrefix: '/blogs' },
    { label: '我的订单', to: '/orders', activePrefix: '/orders' },
    { label: '我的主页', to: '/profile', activePrefix: '/profile' }
  ],
  MERCHANT: [
    { label: '经营总览', to: '/merchant/dashboard' },
    { label: '店铺管理', to: '/merchant/shops' },
    { label: '券与秒杀', to: '/merchant/vouchers' },
    { label: '内容/笔记', to: '/merchant/content' }
  ],
  ADMIN: [
    { label: '实时指标', to: '/admin/dashboard' },
    { label: '店铺巡检', to: '/admin/shops' },
    { label: '券池管理', to: '/admin/vouchers' },
    { label: '笔记巡查', to: '/admin/blogs' }
  ]
};

const currentMenu = computed(() => menus[session.role] || menus.USER);
const title = computed(() => {
  if (session.role === 'MERCHANT') return '商家侧边栏';
  if (session.role === 'ADMIN') return '运营后台';
  return '用户导航';
});
</script>

<style scoped>
.rail {
  width: 210px;
  padding: 16px;
  background: #f9fafb;
  border-right: 1px solid var(--border);
}
.rail-title {
  font-weight: 800;
  margin-bottom: 12px;
  color: var(--muted);
  letter-spacing: 0.4px;
}
.rail-links {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.rail a {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 12px;
  text-decoration: none;
  color: var(--text);
  border: 1px solid transparent;
}
.rail a .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #cbd5e1;
}
.rail a.active {
  background: rgba(14,165,233,0.14);
  border-color: rgba(14,165,233,0.2);
  color: var(--primary);
}
.rail a.active .dot {
  background: var(--primary);
  box-shadow: 0 0 0 4px rgba(14,165,233,0.15);
}
@media (max-width: 960px) {
  .rail { display: none; }
}
</style>
