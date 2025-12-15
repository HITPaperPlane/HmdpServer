import { createRouter, createWebHistory } from 'vue-router';
import Home from '../views/Home.vue';
import Login from '../views/Login.vue';
import Orders from '../views/Orders.vue';
import Blogs from '../views/Blogs.vue';
import BlogDetail from '../views/BlogDetail.vue';
import Profile from '../views/Profile.vue';
import ShopDetail from '../views/ShopDetail.vue';
import MerchantDashboard from '../views/MerchantDashboard.vue';
import MerchantShops from '../views/MerchantShops.vue';
import MerchantVouchers from '../views/MerchantVouchers.vue';
import MerchantContent from '../views/MerchantContent.vue';
import AdminDashboard from '../views/AdminDashboard.vue';
import AdminShops from '../views/AdminShops.vue';
import AdminVouchers from '../views/AdminVouchers.vue';
import AdminBlogs from '../views/AdminBlogs.vue';
import { useSessionStore } from '../stores/session';

const routes = [
  { path: '/', name: 'home', component: Home },
  { path: '/login', name: 'login', component: Login },
  { path: '/orders', name: 'orders', component: Orders },
  { path: '/blogs', name: 'blogs', component: Blogs },
  { path: '/blogs/:id', name: 'blog-detail', component: BlogDetail },
  { path: '/shops/:id', name: 'shop-detail', component: ShopDetail },
  { path: '/profile', name: 'profile', component: Profile },
  { path: '/merchant/dashboard', name: 'merchant-dashboard', component: MerchantDashboard },
  { path: '/merchant/shops', name: 'merchant-shops', component: MerchantShops },
  { path: '/merchant/vouchers', name: 'merchant-vouchers', component: MerchantVouchers },
  { path: '/merchant/content', name: 'merchant-content', component: MerchantContent },
  { path: '/admin/dashboard', name: 'admin-dashboard', component: AdminDashboard },
  { path: '/admin/shops', name: 'admin-shops', component: AdminShops },
  { path: '/admin/vouchers', name: 'admin-vouchers', component: AdminVouchers },
  { path: '/admin/blogs', name: 'admin-blogs', component: AdminBlogs }
];

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
});

router.beforeEach((to) => {
  const session = useSessionStore();
  const path = to.path || '';

  const needsAuth =
      path === '/profile' ||
      path === '/orders' ||
      path.startsWith('/merchant') ||
      path.startsWith('/admin');

  if (needsAuth && !session.token) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }

  if (path.startsWith('/merchant') && session.role !== 'MERCHANT') {
    return { path: '/', query: { denied: 'merchant' } };
  }
  if (path.startsWith('/admin') && session.role !== 'ADMIN') {
    return { path: '/', query: { denied: 'admin' } };
  }
  return true;
});

export default router;
