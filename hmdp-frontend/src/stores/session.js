import { defineStore } from 'pinia';

const key = 'hmdp-token';
const roleKey = 'hmdp-role';

export const useSessionStore = defineStore('session', {
  state: () => ({
    token: localStorage.getItem(key) || '',
    role: localStorage.getItem(roleKey) || 'USER',
    profile: { nickName: '' }
  }),
  actions: {
    setSession({ token, role = 'USER', profile = {} }) {
      this.token = token || '';
      this.role = role;
      this.profile = profile || {};
      if (token) localStorage.setItem(key, token);
      else localStorage.removeItem(key);
      localStorage.setItem(roleKey, role);
    },
    clearSession() {
      this.token = '';
      this.profile = { nickName: '' };
      this.role = 'USER';
      localStorage.removeItem(key);
      localStorage.setItem(roleKey, 'USER');
    }
  }
});
