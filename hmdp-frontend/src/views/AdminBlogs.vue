<template>
  <div class="page">
    <section class="hero">
      <div class="hero-left">
        <div class="hero-title">社区内容巡查</div>
        <div class="hero-sub">热榜巡检 · 单条查询 · 素材上传/删除（图片托管到 `/imgs/**`）</div>
      </div>
      <div class="hero-actions">
        <van-button size="small" type="primary" @click="refreshHot">刷新热榜</van-button>
      </div>
    </section>

    <section class="grid-2">
      <div class="card">
        <div class="section-head">
          <div>
            <div class="title">热榜巡查</div>
            <div class="muted">接口：`GET /blog/hot`，详情：`GET /blog/{id}`，点赞：`PUT /blog/like/{id}`</div>
          </div>
          <div class="row">
            <van-field
                v-model="query.blogId"
                placeholder="输入笔记ID"
                clearable
                style="max-width: 220px;"
                @keyup.enter="loadOne"
            />
            <van-button size="small" plain type="primary" @click="loadOne">查询</van-button>
          </div>
        </div>

        <van-list v-model:loading="hot.loading" :finished="hot.finished" finished-text="没有更多了" @load="loadHot">
          <div class="blog-grid">
            <article v-for="b in hot.list" :key="b.id" class="blog-card">
              <div class="blog-head">
                <div class="author">
                  <van-image round width="36" height="36" :src="b._authorAvatar || defaultAvatar" />
                  <div class="author-meta">
                    <div class="author-name">{{ b.name || '匿名用户' }}</div>
                    <div class="muted">ID {{ b.id }} · 店铺 {{ b.shopId || '-' }}</div>
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
                <van-button size="small" plain type="default" @click="copyId(b.id)">复制ID</van-button>
              </div>
            </article>
          </div>
        </van-list>

        <div class="log">{{ log }}</div>
      </div>

      <div class="card">
        <div class="section-head">
          <div>
            <div class="title">素材管理（图片）</div>
            <div class="muted">上传：`POST /upload/blog`；删除：`GET /upload/blog/delete?name=`</div>
          </div>
        </div>

        <van-cell-group inset>
          <van-field name="upload" label="上传图片">
            <template #input>
              <van-uploader
                  v-model="uploadState.fileList"
                  :after-read="afterRead"
                  :max-count="1"
                  accept="image/*"
                  preview-size="72"
              />
            </template>
          </van-field>
          <van-field :model-value="uploadState.path" label="返回路径" readonly placeholder="上传后自动显示" />
          <van-field v-model="uploadState.deleteName" label="删除路径" placeholder="例如 /blogs/0/1/xxx.png" clearable />
        </van-cell-group>

        <div class="upload-actions">
          <van-button block plain type="danger" :disabled="!uploadState.deleteName.trim()" @click="deleteImg">删除该图片</van-button>
        </div>

        <div v-if="uploadState.previewUrl" class="preview">
          <div class="muted" style="margin-bottom:8px;">预览</div>
          <van-image :src="uploadState.previewUrl" fit="cover" radius="12" width="100%" height="180" />
        </div>

        <div class="log">{{ log }}</div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { request, upload } from '../api/http';
import { resolveImg, splitImages } from '../utils/media';
import { sanitizeHtml } from '../utils/sanitize';
import { useSessionStore } from '../stores/session';
import defaultAvatar from '../assets/default-avatar.svg';

const session = useSessionStore();
const router = useRouter();

const log = ref('准备就绪');
const query = reactive({ blogId: '' });
const hot = reactive({ list: [], page: 1, loading: false, finished: false });

const uploadState = reactive({
  fileList: [],
  path: '',
  previewUrl: '',
  deleteName: ''
});

function normalizeBlog(b) {
  const imgs = splitImages(b.images);
  const authorAvatar = resolveImg(b.icon);
  const snippet = sanitizeHtml(b.content || '');
  return { ...b, _imgs: imgs, _authorAvatar: authorAvatar, _snippet: snippet };
}

function goDetail(id) {
  router.push(`/blogs/${id}`);
}

async function loadHot() {
  if (hot.loading || hot.finished) return;
  hot.loading = true;
  try {
    const list = await request(`/blog/hot?current=${hot.page}`, { token: session.token || undefined });
    const rows = Array.isArray(list) ? list : [];
    if (rows.length === 0) {
      hot.finished = true;
      return;
    }
    hot.list.push(...rows.map(normalizeBlog));
    hot.page += 1;
    if (rows.length < 10) hot.finished = true;
  } catch (e) {
    hot.finished = true;
    log.value = e?.message || '加载失败';
  } finally {
    hot.loading = false;
  }
}

function refreshHot() {
  hot.list = [];
  hot.page = 1;
  hot.finished = false;
  loadHot();
}

async function loadOne() {
  const id = query.blogId.trim();
  if (!id) return;
  try {
    const item = await request(`/blog/${encodeURIComponent(id)}`, { token: session.token || undefined });
    hot.list = item ? [normalizeBlog(item)] : [];
    hot.finished = true;
    log.value = '已载入单条笔记';
  } catch (e) {
    log.value = e?.message || '查询失败';
  }
}

async function toggleLike(b) {
  try {
    await request(`/blog/like/${b.id}`, { method: 'PUT', token: session.token });
    const delta = b.isLike ? -1 : 1;
    b.isLike = !b.isLike;
    b.liked = (b.liked || 0) + delta;
    log.value = '点赞状态已切换';
  } catch (e) {
    log.value = e?.message || '操作失败';
  }
}

async function copyId(id) {
  try {
    await navigator.clipboard.writeText(String(id));
    log.value = '已复制';
  } catch {
    log.value = '复制失败（浏览器权限限制）';
  }
}

async function afterRead(fileItem) {
  const it = Array.isArray(fileItem) ? fileItem[0] : fileItem;
  const file = it?.file || it;
  if (!file) return;
  try {
    it.status = 'uploading';
    const path = await upload('/upload/blog', file, session.token);
    uploadState.path = path;
    uploadState.previewUrl = resolveImg(path);
    it.url = uploadState.previewUrl;
    it.name = path;
    it.status = 'done';
    log.value = `上传成功：${path}`;
  } catch (e) {
    it.status = 'failed';
    it.message = '上传失败';
    log.value = e?.message || '上传失败';
  }
}

async function deleteImg() {
  const name = uploadState.deleteName.trim();
  if (!name) return;
  try {
    await request(`/upload/blog/delete?name=${encodeURIComponent(name)}`, { token: session.token || undefined });
    log.value = '删除成功';
  } catch (e) {
    log.value = e?.message || '删除失败';
  }
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
  padding: 18px;
  background: linear-gradient(135deg, #111827 0%, #334155 100%);
  color: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.22);
}

.hero-title {
  font-size: 18px;
  font-weight: 900;
}

.hero-sub {
  margin-top: 6px;
  font-size: 13px;
  opacity: 0.9;
}

.hero-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.grid-2 {
  display: grid;
  grid-template-columns: 1.2fr 0.8fr;
  gap: 16px;
}

.card {
  padding: 14px;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
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
  font-weight: 900;
  color: #111827;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.blog-title {
  margin-top: 12px;
  font-size: 16px;
  font-weight: 900;
  color: #111827;
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
  color: #4b5563;
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

.upload-actions {
  margin: 16px;
}

.preview {
  margin: 0 16px 16px;
}

@media (max-width: 980px) {
  .grid-2 {
    grid-template-columns: 1fr;
  }
}
</style>
