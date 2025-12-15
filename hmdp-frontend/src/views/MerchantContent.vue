<template>
  <div class="page">
    <section class="hero">
      <div class="hero-left">
        <div class="hero-title">内容营销（笔记）</div>
        <div class="hero-sub">富文本（HTML）排版 · 图片上传 · 关联店铺 · 推送关注流</div>
      </div>
      <div class="hero-actions">
        <van-button size="small" plain type="default" @click="refreshMine">刷新我的笔记</van-button>
        <van-tag plain type="primary">发布入口已迁移至用户端</van-tag>
      </div>
    </section>

    <section class="card">
      <div class="section-head">
        <div>
          <div class="title">我发布的笔记</div>
          <div class="muted">接口：`GET /blog/of/me`（内容为 HTML，渲染时已做 XSS 清洗）</div>
        </div>
      </div>

      <van-list v-model:loading="mine.loading" :finished="mine.finished" finished-text="没有更多了" @load="loadMine">
        <div class="blog-grid">
          <article v-for="b in mine.list" :key="b.id" class="blog-card">
            <div class="blog-head">
              <div class="blog-title">{{ b.title }}</div>
              <div class="row" style="gap:8px;">
                <van-tag v-if="b.shopId" type="primary" plain size="mini">店铺 {{ b.shopId }}</van-tag>
                <van-tag type="success" plain size="mini">点赞 {{ b.liked || 0 }}</van-tag>
              </div>
            </div>

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
              <van-button size="small" plain type="primary" @click="goDetail(b.id)">查看详情</van-button>
              <van-button size="small" plain type="default" @click="toggleLike(b)">
                {{ b.isLike ? '已赞' : '点赞' }}
              </van-button>
            </div>
          </article>
        </div>
      </van-list>

      <div class="log">{{ log }}</div>
    </section>

    <van-popup v-model:show="compose.show" position="right" class="compose-popup" v-if="false">
      <div class="compose-head">
        <div>
          <div class="compose-title">发布笔记</div>
          <div class="muted">图片上传：`POST /upload/blog`；内容以 HTML 存储到 `tb_blog.content`</div>
        </div>
        <van-button size="small" plain type="default" @click="compose.show=false">关闭</van-button>
      </div>

      <div class="compose-body">
        <van-form @submit="submitBlog">
          <van-cell-group inset>
            <van-field
                v-model="compose.shopKeyword"
                label="关联店铺"
                placeholder="输入店铺名称关键词，选择一个"
                clearable
                @update:model-value="onShopKeywordChange"
            />
            <div v-if="compose.shopOptions.length" class="shop-options">
              <div v-for="s in compose.shopOptions" :key="s.id" class="shop-option" @click="selectShop(s)">
                <div class="shop-option-name">{{ s.name }}</div>
                <div class="muted">ID {{ s.id }} · ¥{{ s.avgPrice || 0 }}/人 · 评分 {{ ((s.score || 0) / 10).toFixed(1) }}</div>
              </div>
            </div>
            <van-field :model-value="compose.shopId ? `已选择：${compose.shopName}（ID ${compose.shopId}）` : ''" label="已选店铺" readonly placeholder="可选：不关联则留空" />

            <van-field v-model="compose.title" label="标题" placeholder="新品上线 / 门店活动 / 新品体验" />

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
            <div class="muted" style="margin-top:8px;">提示：发布会推送到粉丝收件箱 `feed:{userId}`</div>
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
import { QuillEditor } from '@vueup/vue-quill';
import '@vueup/vue-quill/dist/vue-quill.snow.css';

const router = useRouter();
const session = useSessionStore();

const log = ref('准备就绪');

const mine = reactive({ list: [], page: 1, loading: false, finished: false });

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
  // shopId 可空，但标题和内容必填（Quill 空内容通常是 <p><br></p>）
  const titleOk = compose.title.trim().length > 0;
  const contentOk = compose.content && compose.content.replace(/<(.|\\n)*?>/g, '').trim().length > 0;
  return !(titleOk && contentOk);
});

function normalizeBlog(b) {
  const imgs = splitImages(b.images);
  const snippet = sanitizeHtml(b.content || '');
  return { ...b, _imgs: imgs, _snippet: snippet };
}

async function loadMine(force = false) {
  if (mine.loading || mine.finished) return;
  mine.loading = true;
  try {
    const list = await request(`/blog/of/me?current=${mine.page}`, { token: session.token });
    const rows = Array.isArray(list) ? list : [];
    if (rows.length === 0) {
      mine.finished = true;
      return;
    }
    mine.list.push(...rows.map(normalizeBlog));
    mine.page += 1;
    if (rows.length < 10) mine.finished = true;
    if (force) log.value = '已刷新';
  } catch (e) {
    mine.finished = true;
    log.value = e?.message || '加载失败';
  } finally {
    mine.loading = false;
  }
}

function refreshMine() {
  mine.list = [];
  mine.page = 1;
  mine.finished = false;
  loadMine(true);
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
  try {
    const payload = {
      shopId: compose.shopId ? Number(compose.shopId) : null,
      title: compose.title.trim(),
      images: compose.fileList.map(f => f.name).filter(Boolean).join(','),
      content: compose.content
    };
    const id = await request('/blog', { method: 'POST', body: payload, token: session.token });
    log.value = `发布成功：${id}`;
    compose.show = false;

    // reset
    compose.shopKeyword = '';
    compose.shopOptions = [];
    compose.shopId = null;
    compose.shopName = '';
    compose.title = '';
    compose.fileList = [];
    compose.content = '';

    refreshMine();
    router.push(`/blogs/${id}`);
  } catch (e) {
    log.value = e?.message || '发布失败';
  }
}

onMounted(async () => {
  await loadMine();
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
  background: linear-gradient(135deg, #ff6b6b 0%, #ff8e53 100%);
  color: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  box-shadow: 0 10px 30px rgba(255, 107, 107, 0.2);
}

.hero-title {
  font-size: 18px;
  font-weight: 900;
}

.hero-sub {
  margin-top: 6px;
  font-size: 13px;
  opacity: 0.92;
}

.hero-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
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
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.blog-title {
  font-size: 15px;
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
  font-weight: 900;
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
