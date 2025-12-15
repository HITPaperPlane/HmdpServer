import { defineStore } from 'pinia';
import { request } from '../api/http';

const tokenKey = 'hmdp-token';
const roleKey = 'hmdp-role';
const profileKey = 'hmdp-profile';

function readJson(key, fallback) {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return fallback;
    return JSON.parse(raw);
  } catch {
    return fallback;
  }
}

export const useSessionStore = defineStore('session', {
  state: () => ({
    token: localStorage.getItem(tokenKey) || '',
    role: localStorage.getItem(roleKey) || 'USER',
    profile: readJson(profileKey, { nickName: '', icon: '', id: null, role: '' })
  }),
  actions: {
    setSession({ token, role = 'USER', profile = {} }) {
      this.token = token || '';
      this.role = role;
      this.profile = profile || {};
      if (this.token) localStorage.setItem(tokenKey, this.token);
      else localStorage.removeItem(tokenKey);
      localStorage.setItem(roleKey, this.role);
      localStorage.setItem(profileKey, JSON.stringify(this.profile || {}));
    },
    updateProfile(partial = {}) {
      this.profile = { ...(this.profile || {}), ...(partial || {}) };
      if (partial?.role) {
        this.role = partial.role;
        localStorage.setItem(roleKey, this.role);
      }
      localStorage.setItem(profileKey, JSON.stringify(this.profile || {}));
    },
    async refreshProfile() {
      if (!this.token) return {};
      try {
        const me = await request('/user/me', { token: this.token });
        if (me) {
          this.updateProfile(me);
          if (me.role) {
            this.role = me.role;
            localStorage.setItem(roleKey, this.role);
          }
        }
        return this.profile;
      } catch (e) {
        // token 失效或后端不可用：直接清理本地会话
        this.clearSession();
        throw e;
      }
    },
    clearSession() {
      this.token = '';
      this.profile = { nickName: '', icon: '', id: null, role: '' };
      this.role = 'USER';
      localStorage.removeItem(tokenKey);
      localStorage.setItem(roleKey, 'USER');
      localStorage.removeItem(profileKey);
    }
  }
});
