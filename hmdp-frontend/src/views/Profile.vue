<template>
  <div class="profile-container">
    <van-nav-bar title="个人中心" left-text="返回" left-arrow @click-left="goBack" />

    <div class="user-card">
      <div class="user-info-row" @click="showEdit = true">
        <van-image
            round
            width="80px"
            height="80px"
            :src="avatarSrc"
            class="avatar"
        />
        <div class="info-content">
          <div class="nickname">
            {{ user.nickName || '未设置昵称' }}
            <van-tag type="primary" plain size="mini">Lv.{{ info.level ?? 0 }}</van-tag>
          </div>
          <div class="city-gender">
            <van-icon :name="info.gender === 1 ? 'manager' : 'manager-o'" :color="info.gender === 1 ? '#1989fa' : '#ff976a'" />
            <span class="city-text">{{ info.city || '未知城市' }}</span>
          </div>
          <div class="bio van-ellipsis">{{ info.introduce || '这个人很懒，什么都没写...' }}</div>
        </div>
        <van-icon name="arrow" class="arrow-icon" />
      </div>

      <div class="stats-row">
        <div class="stat-item">
          <span class="count">{{ info.followee || 0 }}</span>
          <span class="label">关注</span>
        </div>
        <div class="stat-item">
          <span class="count">{{ info.fans || 0 }}</span>
          <span class="label">粉丝</span>
        </div>
        <div class="stat-item">
          <span class="count">{{ info.credits || 0 }}</span>
          <span class="label">积分</span>
        </div>
        <div class="stat-item">
          <span class="count">{{ signDaysCount }}</span>
          <span class="label">签到天数</span>
        </div>
      </div>
    </div>

    <div class="section-card">
      <div class="card-header">
        <span class="title">每日签到</span>
        <van-button
            v-if="!isSignedToday"
            type="primary"
            size="small"
            round
            class="sign-btn"
            @click="handleSign"
        >
          立即签到
        </van-button>
        <van-button v-else disabled size="small" round type="success">今日已签</van-button>
      </div>
      <van-calendar
          :poppable="false"
          :show-confirm="false"
          :show-title="false"
          :show-subtitle="false"
          class="custom-calendar"
          :min-date="minDate"
          :max-date="maxDate"
          :formatter="calendarFormatter"
          row-height="40"
      />
    </div>

    <div class="section-card">
      <div class="card-header">
        <span class="title">访问统计 (UV)</span>
      </div>
      <van-row gutter="10" class="uv-row">
        <van-col span="12">
          <div class="uv-box today">
            <div class="uv-num">{{ todayUv }}</div>
            <div class="uv-label">今日访问</div>
          </div>
        </van-col>
        <van-col span="12">
          <div class="uv-box total">
            <div class="uv-num">{{ totalUv }}</div>
            <div class="uv-label">近7日访问</div>
          </div>
        </van-col>
      </van-row>
    </div>

    <div class="logout-box">
      <van-button block type="default" @click="handleLogout">退出登录</van-button>
    </div>

    <van-popup
        v-model:show="showEdit"
        position="bottom"
        :style="{ height: '70%' }"
        round
    >
      <div class="popup-title">编辑个人资料</div>
      <van-form @submit="onUpdateProfile">
        <van-cell-group inset>
          <van-field name="icon" label="头像">
            <template #input>
              <van-uploader
                  :max-count="1"
                  accept="image/*"
                  :after-read="afterReadAvatar"
              >
                <van-image
                    round
                    width="60px"
                    height="60px"
                    :src="editAvatarSrc"
                />
              </van-uploader>
            </template>
          </van-field>
          <van-field
              v-model="editForm.nickName"
              name="nickName"
              label="昵称"
              placeholder="请输入昵称"
          />
          <van-field name="gender" label="性别">
            <template #input>
              <van-radio-group v-model="editForm.gender" direction="horizontal">
                <van-radio :name="1">男</van-radio>
                <van-radio :name="0">女</van-radio>
              </van-radio-group>
            </template>
          </van-field>
          <van-field name="birthday" label="生日">
            <template #input>
              <input class="native-date" type="date" v-model="editForm.birthday" />
            </template>
          </van-field>
          <van-field
              v-model="editForm.city"
              name="city"
              label="城市"
              placeholder="如: 上海"
          />
          <van-field
              v-model="editForm.introduce"
              name="introduce"
              label="个人介绍"
              type="textarea"
              rows="2"
              autosize
              placeholder="介绍一下自己吧"
          />
        </van-cell-group>
        <div style="margin: 16px;">
          <van-button round block type="primary" native-type="submit">
            保存修改
          </van-button>
        </div>
      </van-form>
    </van-popup>

  </div>
</template>

<script>
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import axios from '../api/http'; // 假设这是你的 axios 封装
import { showToast, showSuccessToast } from 'vant';
import { useSessionStore } from '../stores/session';
import { upload } from '../api/http';
import defaultAvatar from '../assets/default-avatar.svg';

export default {
  name: 'Profile',
  setup() {
    const router = useRouter();
    const session = useSessionStore();
    const user = ref({});      // 基础信息 (User)
    const info = ref({});      // 详细信息 (UserInfo)
    const signedDays = ref([]); // 已签到的日期列表 [1, 5, 6...]
    const signDaysCount = ref(0); // 连续签到天数

    const todayUv = ref(0);
    const totalUv = ref(0);

    const showEdit = ref(false);
    const editForm = ref({
      nickName: '',
      gender: 1,
      birthday: '',
      city: '',
      introduce: '',
      icon: ''
    });

    // 日历范围：本月
    const minDate = new Date(new Date().getFullYear(), new Date().getMonth(), 1);
    const maxDate = new Date(new Date().getFullYear(), new Date().getMonth() + 1, 0);

    const IMG_BASE = import.meta.env.VITE_IMG_BASE || '/imgs';
    const resolveImg = (path) => {
      if (!path) return '';
      if (path.startsWith('data:') || path.startsWith('http://') || path.startsWith('https://')) return path;
      if (path.startsWith(IMG_BASE)) return path;
      if (path.startsWith('/')) return `${IMG_BASE}${path}`;
      return `${IMG_BASE}/${path}`;
    };

    const avatarSrc = computed(() => resolveImg(user.value.icon) || defaultAvatar);
    const editAvatarSrc = computed(() => resolveImg(editForm.value.icon || user.value.icon) || defaultAvatar);

    // 加载数据
    const loadData = async () => {
      try {
        // 1. 获取基础 User
        const userRes = await axios.get('/user/me');
        user.value = userRes || {};
        if (session.token) {
          const role = user.value.role || session.role;
          session.setSession({ token: session.token, role, profile: { ...session.profile, ...user.value } });
        }

        // 2. 获取详细 UserInfo
        if (user.value.id) {
          const infoRes = await axios.get(`/user/info/${user.value.id}`);
          info.value = infoRes || {};

          // 填充表单默认值
          editForm.value = {
            nickName: user.value.nickName,
            icon: user.value.icon,
            gender: info.value.gender !== undefined ? info.value.gender : 1,
            birthday: info.value.birthday || '',
            city: info.value.city,
            introduce: info.value.introduce
          };
        }

        // 先记录一次 UV（否则只查询会一直是 0）
        try {
          await axios.post('/user/uv');
        } catch (e) {
          // best-effort
        }

        // 3. 获取签到详情
        const signDetailRes = await axios.get('/user/sign/detail');
        signedDays.value = signDetailRes || [];

        // 4. 获取连续签到次数
        const countRes = await axios.get('/user/sign/count');
        signDaysCount.value = countRes || 0;

        // 5. UV 数据
        const todayUvRes = await axios.get('/user/uv?days=1');
        todayUv.value = todayUvRes || 0;
        const totalUvRes = await axios.get('/user/uv?days=7');
        totalUv.value = totalUvRes || 0;
      } catch (e) {
        showToast(e?.message || '加载失败，请重新登录');
        session.clearSession();
        router.push('/login');
      }
    };

    const afterReadAvatar = async (fileItem) => {
      try {
        const file = fileItem?.file || fileItem;
        if (!file) return;
        const path = await upload('/upload/avatar', file, session.token);
        editForm.value.icon = path;
        user.value.icon = path;
        showSuccessToast('头像上传成功');
      } catch (e) {
        showToast(e?.message || '头像上传失败');
      }
    };

    // 签到操作
    const handleSign = async () => {
      try {
        await axios.post('/user/sign');
        showSuccessToast('签到成功!');
        await loadData(); // 刷新签到/积分/等级等信息
      } catch (e) {
        showToast(e?.message || '签到失败');
      }
    };

    // 提交修改
    const onUpdateProfile = async () => {
      try {
        await axios.post('/user/update', editForm.value);
        showSuccessToast('保存成功');
        showEdit.value = false;
        await loadData(); // 刷新数据 + 同步顶部/侧边昵称
      } catch (e) {
        showToast('保存失败');
      }
    };

    const handleLogout = async () => {
      try {
        await axios.post('/user/logout');
      } catch (e) {
        // ignore
      } finally {
        session.clearSession();
        router.push('/login');
      }
    }

    const goBack = () => router.back();

    // 日历格式化：给已签到的日期打勾
    const calendarFormatter = (day) => {
      const dayNum = day.date.getDate();
      // 如果这个日期在 signedDays 数组里，标记为已签到
      if (signedDays.value.includes(dayNum)) {
        day.bottomInfo = '✅';
        day.className = 'signed-day';
      }
      return day;
    };

    // 计算今天是否已签到
    const isSignedToday = computed(() => {
      return signedDays.value.includes(new Date().getDate());
    });

    onMounted(() => {
      loadData();
    });

    return {
      user,
      info,
      avatarSrc,
      editAvatarSrc,
      signDaysCount,
      signedDays,
      todayUv,
      totalUv,
      showEdit,
      editForm,
      minDate,
      maxDate,
      calendarFormatter,
      isSignedToday,
      afterReadAvatar,
      handleSign,
      onUpdateProfile,
      goBack,
      handleLogout
    };
  }
};
</script>

<style scoped>
.profile-container {
  background-color: #f7f8fa;
  min-height: 100vh;
  padding-bottom: 20px;
}

/* 用户信息卡片 */
.user-card {
  background: linear-gradient(135deg, #FF6B6B 0%, #FF8E53 100%);
  color: #fff;
  padding: 24px 20px;
  border-bottom-left-radius: 20px;
  border-bottom-right-radius: 20px;
}

.user-info-row {
  display: flex;
  align-items: center;
  margin-bottom: 20px;
}

.avatar {
  border: 2px solid rgba(255,255,255,0.5);
  margin-right: 15px;
}

.info-content {
  flex: 1;
}

.nickname {
  font-size: 20px;
  font-weight: bold;
  display: flex;
  align-items: center;
  gap: 8px;
}

.city-gender {
  margin-top: 5px;
  font-size: 13px;
  opacity: 0.9;
  display: flex;
  align-items: center;
  gap: 4px;
}

.bio {
  margin-top: 6px;
  font-size: 12px;
  opacity: 0.8;
  max-width: 200px;
}

.arrow-icon {
  opacity: 0.7;
}

.stats-row {
  display: flex;
  justify-content: space-around;
  text-align: center;
}

.stat-item .count {
  display: block;
  font-size: 18px;
  font-weight: bold;
}

.stat-item .label {
  font-size: 12px;
  opacity: 0.8;
}

/* 通用卡片样式 */
.section-card {
  background: #fff;
  margin: 16px;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 2px 12px rgba(100, 100, 100, 0.05);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  border-left: 4px solid #FF6B6B;
  padding-left: 10px;
}

/* 日历样式 */
.custom-calendar {
  height: 320px;
  border-radius: 8px;
  overflow: hidden;
  --van-calendar-header-title-height: 30px;
  --van-calendar-month-mark-color: rgba(255, 107, 107, 0.1);
  --van-calendar-month-mark-font-size: 120px;
}

/* UV 统计 */
.uv-box {
  border-radius: 8px;
  padding: 15px;
  text-align: center;
  color: #fff;
}
.uv-box.today {
  background: linear-gradient(to right, #4facfe 0%, #00f2fe 100%);
}
.uv-box.total {
  background: linear-gradient(to right, #fa709a 0%, #fee140 100%);
}

.uv-num {
  font-size: 24px;
  font-weight: bold;
}
.uv-label {
  font-size: 12px;
  margin-top: 4px;
  opacity: 0.9;
}

.popup-title {
  text-align: center;
  padding: 16px;
  font-size: 16px;
  font-weight: bold;
}

.native-date {
  width: 100%;
  border: none;
  outline: none;
  background: transparent;
  font-size: 14px;
  color: #323233;
}

.logout-box {
  margin: 20px;
}
</style>
