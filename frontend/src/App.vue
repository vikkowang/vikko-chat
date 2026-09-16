<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { renderMarkdown } from './markdown.js'

const conversations = ref([]) // { conversationId, title }
const conversationId = ref(uuid())
const messages = ref([]) // { role: 'user' | 'assistant', content: '' }
const input = ref('')
const sending = ref(false)
const listEl = ref(null)

onMounted(loadConversations)

function uuid() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID()
  }
  return 'conv-' + Date.now() + '-' + Math.random().toString(16).slice(2)
}

async function loadConversations() {
  try {
    const resp = await fetch('/api/chat/conversations')
    if (resp.ok) conversations.value = await resp.json()
  } catch (e) {
    /* 忽略加载失败 */
  }
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
}

async function send() {
  const text = input.value.trim()
  if (!text || sending.value) return
  input.value = ''
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
    loadConversations()
  }
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
</script>

<template>
  <div class="app">
    <aside class="sidebar">
      <button class="btn-new" :disabled="sending" @click="newConversation">＋ 新对话</button>
      <div class="conv-list">
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
            v-model="input"
            class="chat-input"
            rows="1"
            placeholder="输入消息，Enter 发送，Shift+Enter 换行"
            @keydown.enter.exact.prevent="send"
          ></textarea>
          <button class="btn-send" :disabled="sending || !input.trim()" @click="send">发送</button>
        </div>
        <p class="footer-hint">会话 ID: {{ conversationId }}</p>
      </footer>
    </div>
  </div>
</template>
