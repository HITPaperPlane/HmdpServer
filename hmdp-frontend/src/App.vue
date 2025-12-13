<template>
  <div class="shell">
    <NavBar />
    <div class="layout">
      <SideRail />
      <main class="main">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import NavBar from './components/NavBar.vue';
import SideRail from './components/SideRail.vue';
</script>
