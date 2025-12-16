<template>
  <div class="page">
    <section class="hero">
      <div class="hero-left">
        <div class="hero-title">探店笔记</div>
        <div class="hero-sub">支持富文本（HTML）排版、图片上传、点赞与关注流</div>
      </div>
      <div class="hero-actions">
        <van-button type="primary" size="small" @click="openCompose">发布笔记</van-button>
      </div>
    </section>

    <section class="card">
      <van-tabs v-model:active="activeTab" animated color="#ff6b6b" line-width="40">
        <van-tab title="热榜">
          <van-list v-model:loading="hot.loading" :finished="hot.finished" finished-text="没有更多了" @load="loadHot">
            <div class="blog-grid">
              <article v-for="b in hot.list" :key="b.id" class="blog-card">
                <div class="blog-head">
                  <div class="author">
                    <van-image
                        round
                        width="36"
                        height="36"
                        :src="b._authorAvatar || defaultAvatar"
                    />
                    <div class="author-meta">
                      <div class="author-name">{{ b.name || '匿名用户' }}</div>
                      <div class="muted">店铺 {{ b.shopId }} · {{ b.createTime || '' }}</div>
                    </div>
                  </div>
                  <van-button size="small" plain type="primary" @click="goDetail(b.id)">详情</van-button>
                </div>

                <div class="blog-title">{{ b.title }}</div>

                <div v-if="b._imgs.length" class="img-row">
                  <van-image
                      v-for="(img, idx) in b._imgs.slice(0, 3)"
                      :key="idx"
                      :src="img"
                      fit="cover"
                      radius="10"
                      class="thumb"
                      @click="goDetail(b.id)"
                  />
                  <div v-if="b._imgs.length > 3" class="more">+{{ b._imgs.length - 3 }}</div>
                </div>

                <div class="blog-snippet" v-html="b._snippet"></div>

                <div class="blog-actions">
                  <van-button size="small" plain type="primary" :disabled="!session.token" @click="toggleLike(b)">
                    {{ b.isLike ? '已赞' : '点赞' }} {{ b.liked || 0 }}
                  </van-button>
                  <van-button size="small" plain type="default" :disabled="!session.token || b.userId === session.profile.id" @click="toggleFollow(b)">
                    {{ b._following ? '已关注' : '关注作者' }}
                  </van-button>
                </div>
              </article>
            </div>
          </van-list>
        </van-tab>

        <van-tab title="关注流">
          <div v-if="!session.token" style="padding: 18px 0;">
            <van-empty description="登录后可查看关注流" />
            <div style="display:flex; justify-content:center; margin-top:10px;">
              <van-button type="primary" size="small" @click="router.push('/login')">去登录</van-button>
            </div>
          </div>

          <van-list
              v-else
              v-model:loading="feed.loading"
              :finished="feed.finished"
              finished-text="没有更多了"
              @load="loadFeed"
          >
            <div class="blog-grid">
              <article v-for="b in feed.list" :key="b.id" class="blog-card">
                <div class="blog-head">
                  <div class="author">
                    <van-image round width="36" height="36" :src="b._authorAvatar || defaultAvatar" />
                    <div class="author-meta">
                      <div class="author-name">{{ b.name || '匿名用户' }}</div>
                      <div class="muted">店铺 {{ b.shopId }} · {{ b.createTime || '' }}</div>
                    </div>
                  </div>
                  <van-button size="small" plain type="primary" @click="goDetail(b.id)">详情</van-button>
                </div>

                <div class="blog-title">{{ b.title }}</div>

                <div v-if="b._imgs.length" class="img-row">
                  <van-image
                      v-for="(img, idx) in b._imgs.slice(0, 3)"
                      :key="idx"
                      :src="img"
                      fit="cover"
                      radius="10"
                      class="thumb"
                      @click="goDetail(b.id)"
                  />
                  <div v-if="b._imgs.length > 3" class="more">+{{ b._imgs.length - 3 }}</div>
                </div>

                <div class="blog-snippet" v-html="b._snippet"></div>

                <div class="blog-actions">
                  <van-button size="small" plain type="primary" @click="toggleLike(b)">
                    {{ b.isLike ? '已赞' : '点赞' }} {{ b.liked || 0 }}
                  </van-button>
                  <van-button size="small" plain type="default" :disabled="b.userId === session.profile.id" @click="toggleFollow(b)">
                    {{ b._following ? '已关注' : '关注作者' }}
                  </van-button>
                </div>
              </article>
            </div>
          </van-list>
        </van-tab>
      </van-tabs>
    </section>

    <van-popup v-model:show="compose.show" position="right" class="compose-popup">
      <div class="compose-head">
        <div>
          <div class="compose-title">发布笔记</div>
          <div class="muted">图片会上传到 `POST /upload/blog`，内容以 HTML 存储到 `tb_blog.content`</div>
        </div>
        <van-button size="small" plain type="default" @click="compose.show=false">关闭</van-button>
      </div>

      <div v-if="!session.token" style="padding: 30px 0;">
        <van-empty description="登录后才能发布笔记" />
        <div style="display:flex; justify-content:center; margin-top:10px;">
          <van-button type="primary" size="small" @click="router.push('/login')">去登录</van-button>
        </div>
      </div>

      <div v-else class="compose-body">
        <van-form @submit="submitBlog">
          <van-cell-group inset>
            <van-field
                v-model="compose.shopKeyword"
                label="关联店铺"
                placeholder="输入店铺名称关键词，选择一个（可选）"
                clearable
                @update:model-value="onShopKeywordChange"
            />
            <div v-if="compose.shopOptions.length" class="shop-options">
              <div
                  v-for="s in compose.shopOptions"
                  :key="s.id"
                  class="shop-option"
                  @click="selectShop(s)"
              >
                <div class="shop-option-name">{{ s.name }}</div>
                <div class="muted">ID {{ s.id }} · {{ s.area || '未知商圈' }} · ¥{{ s.avgPrice || 0 }}/人</div>
              </div>
            </div>
            <van-field :model-value="compose.shopId ? `已选择：${compose.shopName}（ID ${compose.shopId}）` : ''" label="已选店铺" readonly placeholder="可选：不关联则留空" />
            <van-field v-model="compose.title" label="标题" placeholder="给笔记起个标题" />

            <van-field name="images" label="图片">
              <template #input>
                <van-uploader
                    v-model="compose.fileList"
                    :after-read="afterReadBlogImage"
                    :max-count="9"
                    multiple
                    preview-size="64"
                />
              </template>
            </van-field>

            <van-field name="content" label="内容">
              <template #input>
                <div class="editor">
                  <QuillEditor
                      v-model:content="compose.content"
                      contentType="html"
                      theme="snow"
                      :toolbar="toolbar"
                  />
                </div>
              </template>
            </van-field>
          </van-cell-group>

          <div class="compose-actions">
            <van-button block type="primary" native-type="submit" :disabled="submitDisabled">发布</van-button>
            <div class="muted" style="margin-top:8px;">提示：可选关联店铺，发布会推送到粉丝收件箱 `feed:{userId}`</div>
          </div>
        </van-form>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { request, upload } from '../api/http';
import { resolveImg, splitImages } from '../utils/media';
import { sanitizeHtml } from '../utils/sanitize';
import { useSessionStore } from '../stores/session';
import defaultAvatar from '../assets/default-avatar.svg';
import { QuillEditor } from '@vueup/vue-quill';
import '@vueup/vue-quill/dist/vue-quill.snow.css';

const router = useRouter();
const session = useSessionStore();

const activeTab = ref(0);

const hot = reactive({ list: [], page: 1, loading: false, finished: false });
const feed = reactive({ list: [], max: Date.now(), offset: 0, loading: false, finished: false });
const followCache = reactive({});

const toolbar = [
  [{ header: [1, 2, 3, false] }],
  ['bold', 'italic', 'underline', 'strike'],
  [{ color: [] }, { background: [] }],
  [{ list: 'ordered' }, { list: 'bullet' }],
  [{ align: [] }],
  ['blockquote', 'code-block', 'link'],
  ['clean']
];

const compose = reactive({
  show: false,
  shopKeyword: '',
  shopOptions: [],
  shopId: null,
  shopName: '',
  title: '',
  fileList: [],
  content: ''
});

const submitDisabled = computed(() => {
  const titleOk = compose.title.trim().length > 0;
  const contentOk = compose.content && compose.content.replace(/<(.|\\n)*?>/g, '').trim().length > 0;
  return !(titleOk && contentOk);
});

function normalizeBlog(b) {
  const imgs = splitImages(b.images);
  const authorAvatar = resolveImg(b.icon);
  const snippet = sanitizeHtml(b.content || '');
  const followed = followCache[b.userId] === true;
  return { ...b, _imgs: imgs, _authorAvatar: authorAvatar, _snippet: snippet, _following: followed };
}

async function loadHot() {
  hot.loading = true;
  try {
    const list = await request(`/blog/hot?current=${hot.page}`, { token: session.token || undefined });
    const rows = Array.isArray(list) ? list : [];
    if (rows.length === 0) {
      hot.finished = true;
      return;
    }
    hot.list.push(...rows.map(normalizeBlog));
    await preloadFollow(rows);
    hot.page += 1;
    if (rows.length < 10) hot.finished = true;
  } catch {
    hot.finished = true;
  } finally {
    hot.loading = false;
  }
}

async function loadFeed() {
  feed.loading = true;
  try {
    const data = await request(`/blog/of/follow?lastId=${feed.max}&offset=${feed.offset}`, { token: session.token });
    const rows = Array.isArray(data?.list) ? data.list : [];
    if (rows.length === 0) {
      feed.finished = true;
      return;
    }
    feed.list.push(...rows.map(normalizeBlog));
    await preloadFollow(rows);
    feed.max = data.minTime;
    feed.offset = data.offset;
  } catch {
    feed.finished = true;
  } finally {
    feed.loading = false;
  }
}

function openCompose() {
  compose.show = true;
}

function goDetail(id) {
  router.push(`/blogs/${id}`);
}

async function toggleLike(b) {
  if (!session.token) return;
  try {
    await request(`/blog/like/${b.id}`, { method: 'PUT', token: session.token });
    const delta = b.isLike ? -1 : 1;
    b.isLike = !b.isLike;
    b.liked = (b.liked || 0) + delta;
  } catch {
    // ignore
  }
}

async function toggleFollow(b) {
  if (!session.token || !b.userId || b.userId === session.profile.id) return;
  try {
    const isFollow = await request(`/follow/or/not/${b.userId}`, { token: session.token });
    const target = isFollow ? 'false' : 'true';
    await request(`/follow/${b.userId}/${target}`, { method: 'PUT', token: session.token });
    b._following = !isFollow;
    followCache[b.userId] = b._following;
  } catch {
    // ignore
  }
}

async function preloadFollow(rows) {
  if (!session.token) return;
  const ids = Array.from(new Set(rows.map(r => r.userId).filter(id => id && id !== session.profile.id && followCache[id] === undefined)));
  if (!ids.length) return;
  await Promise.all(ids.map(async id => {
    try {
      const f = await request(`/follow/or/not/${id}`, { token: session.token });
      followCache[id] = Boolean(f);
    } catch {
      followCache[id] = false;
    }
  }));
  // 回填已有列表的展示状态
  hot.list.forEach(b => { if (followCache[b.userId] !== undefined) b._following = followCache[b.userId]; });
  feed.list.forEach(b => { if (followCache[b.userId] !== undefined) b._following = followCache[b.userId]; });
}

let shopSearchTimer = null;
function onShopKeywordChange() {
  if (shopSearchTimer) clearTimeout(shopSearchTimer);
  shopSearchTimer = setTimeout(searchShop, 250);
}

async function searchShop() {
  const kw = compose.shopKeyword.trim();
  if (!kw) {
    compose.shopOptions = [];
    return;
  }
  try {
    const list = await request(`/shop/of/name?name=${encodeURIComponent(kw)}&current=1`);
    compose.shopOptions = (Array.isArray(list) ? list : []).slice(0, 8);
  } catch {
    compose.shopOptions = [];
  }
}

function selectShop(s) {
  compose.shopId = s.id;
  compose.shopName = s.name;
  compose.shopOptions = [];
}

async function afterReadBlogImage(fileItem) {
  const items = Array.isArray(fileItem) ? fileItem : [fileItem];
  for (const it of items) {
    const file = it?.file || it;
    if (!file) continue;
    try {
      it.status = 'uploading';
      const path = await upload('/upload/blog', file, session.token);
      // 让预览显示真实可访问 URL，同时把原始 path 放到 name 里用于提交
      it.url = resolveImg(path);
      it.name = path;
      it.status = 'done';
    } catch {
      it.status = 'failed';
      it.message = '上传失败';
    }
  }
}

async function submitBlog() {
  if (submitDisabled.value) return;
  const payload = {
    shopId: compose.shopId ? Number(compose.shopId) : null,
    title: compose.title.trim(),
    images: compose.fileList.map(f => f.name).filter(Boolean).join(','),
    content: compose.content
  };
  const id = await request('/blog', { method: 'POST', body: payload, token: session.token });
  compose.show = false;
  // reset
  compose.shopKeyword = '';
  compose.shopOptions = [];
  compose.shopId = null;
  compose.shopName = '';
  compose.title = '';
  compose.fileList = [];
  compose.content = '';
  // refresh hot list on success
  hot.list = [];
  hot.page = 1;
  hot.finished = false;
  await loadHot();
  router.push(`/blogs/${id}`);
}

onMounted(async () => {
  await loadHot();
});
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.hero {
  border-radius: 18px;
  padding: 18px 18px;
  background: linear-gradient(135deg, #ff6b6b 0%, #ff8e53 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  box-shadow: 0 10px 30px rgba(255, 107, 107, 0.2);
}

.hero-title {
  font-size: 20px;
  font-weight: 900;
}

.hero-sub {
  margin-top: 6px;
  font-size: 13px;
  opacity: 0.92;
}

.card {
  padding: 14px;
}

.blog-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 14px;
  padding: 14px 0;
}

.blog-card {
  border-radius: 16px;
  border: 1px solid rgba(0, 0, 0, 0.05);
  background: #fff;
  padding: 14px;
  box-shadow: 0 10px 22px rgba(0, 0, 0, 0.04);
}

.blog-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.author {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.author-meta {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.author-name {
  font-weight: 800;
  color: #222;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.blog-title {
  margin-top: 12px;
  font-size: 16px;
  font-weight: 900;
  color: #111;
  line-height: 1.25;
}

.img-row {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  position: relative;
}

.thumb {
  width: 100%;
  height: 92px;
  background: #f5f6f7;
}

.more {
  position: absolute;
  right: 10px;
  bottom: 10px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  padding: 3px 8px;
  border-radius: 999px;
}

.blog-snippet {
  margin-top: 12px;
  color: #555;
  font-size: 13px;
  line-height: 1.7;
  max-height: 88px;
  overflow: hidden;
  position: relative;
}

.blog-actions {
  margin-top: 12px;
  display: flex;
  gap: 10px;
}

.compose-popup {
  width: 560px;
  max-width: 92vw;
  height: 100vh;
  padding: 16px;
  overflow: auto;
}

.compose-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.compose-title {
  font-size: 18px;
  font-weight: 900;
  color: #222;
}

.compose-body {
  padding-bottom: 30px;
}

.compose-actions {
  margin: 16px;
}

.shop-options {
  margin: 0 16px 12px;
  border-radius: 12px;
  border: 1px solid rgba(0, 0, 0, 0.05);
  overflow: hidden;
}

.shop-option {
  padding: 12px 12px;
  cursor: pointer;
  background: #fff;
  border-bottom: 1px solid rgba(0, 0, 0, 0.04);
}

.shop-option:last-child {
  border-bottom: none;
}

.shop-option:hover {
  background: #fff7f2;
}

.shop-option-name {
  font-weight: 800;
  color: #222;
  font-size: 13px;
}

.editor {
  width: 100%;
}

.editor :deep(.ql-container) {
  min-height: 240px;
  border-radius: 10px;
}

.editor :deep(.ql-toolbar) {
  border-radius: 10px 10px 0 0;
}

.editor :deep(.ql-editor) {
  font-size: 14px;
  line-height: 1.8;
}
</style>
