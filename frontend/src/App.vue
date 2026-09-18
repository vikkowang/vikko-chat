<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { renderMarkdown } from './markdown.js'

const conversations = ref([]) // { conversationId, title }
const conversationId = ref(uuid())
const messages = ref([]) // { role: 'user' | 'assistant', content: '' }
const input = ref('')
const sending = ref(false)
const listEl = ref(null)
const inputEl = ref(null)
const convListEl = ref(null)
const page = ref(0)
const pageSize = 50
const hasMore = ref(true)
const loadingMore = ref(false)
const refreshing = ref(false)

onMounted(refreshConversations)

function uuid() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID()
  }
  return 'conv-' + Date.now() + '-' + Math.random().toString(16).slice(2)
}

async function refreshConversations() {
  refreshing.value = true
  page.value = 0
  hasMore.value = true
  try {
    const resp = await fetch('/api/chat/conversations?page=0&size=' + pageSize)
    if (resp.ok) {
      const data = await resp.json()
      conversations.value = data.items
      hasMore.value = data.hasMore
      nextTick(() => {
        if (convListEl.value) convListEl.value.scrollTop = 0
        maybeLoadMore()
      })
    }
  } catch (e) {
    /* 忽略加载失败 */
  } finally {
    refreshing.value = false
  }
}

async function loadMore() {
  if (loadingMore.value || refreshing.value || !hasMore.value) return
  loadingMore.value = true
  const next = page.value + 1
  try {
    const resp = await fetch('/api/chat/conversations?page=' + next + '&size=' + pageSize)
    if (resp.ok) {
      const data = await resp.json()
      conversations.value = conversations.value.concat(data.items)
      page.value = next
      hasMore.value = data.hasMore
      nextTick(maybeLoadMore)
    }
  } catch (e) {
    /* 忽略加载失败 */
  } finally {
    loadingMore.value = false
  }
}

function onConvScroll() {
  const el = convListEl.value
  if (!el) return
  // 距底部不足 80px 时加载下一页
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 80) loadMore()
}

function maybeLoadMore() {
  const el = convListEl.value
  if (!el || loadingMore.value || !hasMore.value) return
  // 内容不足以撑出滚动条时自动补拉,直到出现滚动条或没有更多
  if (el.scrollHeight <= el.clientHeight) loadMore()
}

async function selectConversation(id) {
  if (sending.value) return
  conversationId.value = id
  messages.value = []
  try {
    const resp = await fetch('/api/chat/conversations/' + encodeURIComponent(id))
    if (resp.ok) {
      const list = await resp.json()
      messages.value = list.map((m) => ({ role: m.role, content: m.content }))
    }
  } catch (e) {
    /* 忽略 */
  }
  scrollToBottom()
}

function newConversation() {
  if (sending.value) return
  conversationId.value = uuid()
  messages.value = []
  input.value = ''
  nextTick(resizeInput)
}

async function send() {
  const text = input.value.trim()
  if (!text || sending.value) return
  input.value = ''
  nextTick(resizeInput)
  messages.value.push({ role: 'user', content: text })
  const assistant = { role: 'assistant', content: '' }
  messages.value.push(assistant)
  sending.value = true
  scrollToBottom()

  try {
    const resp = await fetch('/api/chat/memory/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: text, conversationId: conversationId.value }),
    })
    if (!resp.ok || !resp.body) {
      throw new Error('HTTP ' + resp.status)
    }
    const reader = resp.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      let idx
      while ((idx = buffer.indexOf('\n\n')) >= 0) {
        const raw = buffer.slice(0, idx)
        buffer = buffer.slice(idx + 2)
        const data = extractData(raw)
        if (data) {
          assistant.content += data
          scrollToBottom()
        }
      }
    }
    if (buffer.trim()) {
      const data = extractData(buffer)
      if (data) assistant.content += data
    }
  } catch (e) {
    assistant.content += (assistant.content ? '\n\n' : '') + '⚠️ 出错了: ' + e.message
  } finally {
    sending.value = false
    scrollToBottom()
    refreshConversations()
  }
}

function onEnter(e) {
  // 中文输入法组合中(候选词未上屏)时,Enter 用于确认候选词,不发送
  if (e.isComposing || e.keyCode === 229) return
  e.preventDefault()
  send()
}

function extractData(raw) {
  return raw
    .split('\n')
    .filter((l) => l.startsWith('data:'))
    .map((l) => l.slice(5).replace(/^ /, ''))
    .join('\n')
}

function isStreaming(index) {
  return index === messages.value.length - 1 && sending.value
}

function scrollToBottom() {
  nextTick(() => {
    if (listEl.value) listEl.value.scrollTop = listEl.value.scrollHeight
  })
}

function resizeInput() {
  const el = inputEl.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 160) + 'px'
}
</script>

<template>
  <div class="app">
    <aside class="sidebar">
      <button class="btn-new" :disabled="sending" @click="newConversation">＋ 新对话</button>
      <div ref="convListEl" class="conv-list" @scroll="onConvScroll">
        <div
          v-for="c in conversations"
          :key="c.conversationId"
          class="conv-item"
          :class="{ active: c.conversationId === conversationId }"
          :title="c.title"
          @click="selectConversation(c.conversationId)"
        >
          {{ c.title || '新对话' }}
        </div>
        <div v-if="loadingMore || (conversations.length > 0 && !hasMore)" class="conv-sentinel">
          <span v-if="loadingMore">加载中…</span>
          <span v-else>没有更多了</span>
        </div>
        <div v-if="conversations.length === 0" class="conv-empty">暂无会话记录</div>
      </div>
    </aside>

    <div class="chat">
      <header class="chat-header">
        <div class="chat-brand">
          <span class="chat-logo">🤖</span>
          <span>DeepSeek · 多轮对话</span>
        </div>
      </header>

      <main ref="listEl" class="chat-body">
        <div v-if="messages.length === 0" class="chat-empty">
          <p class="empty-title">有什么可以帮你的？</p>
          <p class="empty-sub">试试：「查一下 alice 的状态」 · 「把 bob 改成封禁」 · 「北京天气怎么样」</p>
        </div>

        <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
          <div class="msg-avatar">{{ m.role === 'user' ? '👤' : '🤖' }}</div>
          <div class="msg-content">
            <template v-if="m.role === 'user'">
              <div class="msg-text">{{ m.content }}</div>
            </template>
            <template v-else>
              <div v-if="isStreaming(i)" class="msg-text streaming">{{ m.content || '思考中…' }}</div>
              <div v-else class="markdown" v-html="renderMarkdown(m.content)"></div>
            </template>
          </div>
        </div>
      </main>

      <footer class="chat-footer">
        <div class="input-wrap">
          <textarea
            ref="inputEl"
            v-model="input"
            class="chat-input"
            rows="1"
            placeholder="输入消息，Enter 发送，Shift+Enter 换行"
            @input="resizeInput"
            @keydown.enter.exact="onEnter"
          ></textarea>
          <button class="btn-send" :disabled="sending || !input.trim()" @click="send">发送</button>
        </div>
        <p class="footer-hint">会话 ID: {{ conversationId }}</p>
      </footer>
    </div>
  </div>
</template>
