<template>
  <div class="shell">
    <NavBar />
    <div class="layout" :class="{ 'with-sidebar': session.token }">

      <SideRail v-if="session.token" />

      <main class="main">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import NavBar from './components/NavBar.vue';
import SideRail from './components/SideRail.vue';
import { useSessionStore } from './stores/session';

const session = useSessionStore();
</script>

<style scoped>
.shell {
  min-height: 100vh;
  background-color: #f9f9f9;
}

.layout {
  /* 默认布局：没有侧边栏时，主内容全宽 */
  display: flex;
  flex-direction: column;
}

.layout.with-sidebar {
  /* 有侧边栏时，改为横向排列 */
  flex-direction: row;
  align-items: flex-start;
}

.main {
  flex: 1;
  width: 100%;
  padding: 20px;
  /* 限制最大宽度，防止内容在大屏上过于拉伸 */
  /* max-width: 1400px; */
  margin: 0 auto;
}
</style>