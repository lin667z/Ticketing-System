<template>
  <Header :class="{ isLogin }">
    <div class="header-wrapper">
      <div>
        <div class="logo">高并发票务系统</div>
      </div>
      <div>
        <ul class="nav-list-wrapper">
          <a v-if="route.fullPath !== '/login'" @click="openChat">
            <li class="chat-btn">
              <CustomerServiceOutlined />
              <span>AI 客服</span>
            </li>
          </a>
          <a v-if="route.fullPath !== '/login'">
            <Dropdown :trigger="['click']">
              <li :style="{ padding: '0 0 0 30px' }">
                <Avatar shape="circle" style="background-color: #333">
                  {{ state.username?.slice(0, 1)?.toUpperCase() }}
                </Avatar>
              </li>
              <template #overlay>
                <Menu>
                  <MenuItem>
                    <a @click="() => router.push('/userInfo')">个人信息</a>
                  </MenuItem>
                  <MenuItem>
                    <a @click="() => logout()">退出登录</a>
                  </MenuItem>
                </Menu>
              </template>
            </Dropdown>
          </a>
        </ul>
      </div>
    </div>

    <Drawer
      v-model:visible="isChatVisible"
      placement="right"
      :width="620"
      :closable="false"
      :bodyStyle="{ padding: 0, display: 'flex', flexDirection: 'column', background: '#fff', height: '100%' }"
      wrapClassName="ai-service-drawer"
      @close="cancelStreaming"
    >
      <div class="chat-panel">
        <div class="chat-topbar">
          <div>
            <div class="chat-title">铁宝</div>
            <div class="chat-subtitle">AI Agent · 在线</div>
          </div>
          <div class="topbar-actions">
            <button class="topbar-btn" type="button" @click="toggleHistory" title="历史对话">
              <HistoryOutlined />
            </button>
            <button class="close-btn" type="button" @click="isChatVisible = false">×</button>
          </div>
        </div>

        <!-- 历史对话下拉面板 -->
        <div v-if="historyDropdownVisible" class="history-backdrop" @click="historyDropdownVisible = false"></div>
        <div v-if="historyDropdownVisible" class="history-dropdown">
          <div class="history-header">
            <span class="history-title">历史对话</span>
            <button class="history-new-btn" type="button" @click="handleNewConversation">+ 新对话</button>
          </div>
          <div class="history-list">
            <div v-if="conversationsLoading" class="history-loading">
              <span class="typing-dot"></span>
              <span class="typing-dot"></span>
              <span class="typing-dot"></span>
            </div>
            <div v-else-if="conversations.length === 0" class="history-empty">暂无历史对话</div>
            <div
              v-else
              v-for="conv in conversations"
              :key="conv.id"
              :class="['history-item', { active: conv.id === currentConversationId }]"
              @click="handleSelectConversation(conv)"
            >
              <div class="history-item-body">
                <div class="history-item-title">{{ conv.title || '新对话' }}</div>
                <div class="history-item-meta">
                  <span>{{ formatTime(conv.updateTime) }}</span>
                  <span v-if="conv.messageCount" class="history-item-count">{{ conv.messageCount }} 条</span>
                </div>
              </div>
              <button
                class="history-item-delete"
                type="button"
                title="删除"
                @click.stop="handleDeleteConversation(conv)"
              >
                <DeleteOutlined />
              </button>
            </div>
          </div>
        </div>

        <div ref="messageListRef" class="chat-message-list">
          <div
            v-for="msg in chatMessages"
            :key="msg.id"
            :class="['message-row', msg.role]"
          >
            <div class="message-bubble">
              <template v-if="msg.streaming && !msg.content && !msg.reasoningContent">
                <span class="typing-dot"></span>
                <span class="typing-dot"></span>
                <span class="typing-dot"></span>
              </template>
              <template v-else-if="msg.parts">
                <span
                  v-for="(part, partIndex) in msg.parts"
                  :key="partIndex"
                  :class="{ 'brand-highlight': part.highlight }"
                >
                  {{ part.text }}
                </span>
              </template>
              <template v-else-if="msg.reasoningContent || msg.content">
                <div v-if="msg.reasoningContent" class="reasoning-block">
                  <div
                    class="reasoning-toggle"
                    @click="msg.reasoningExpanded = !msg.reasoningExpanded"
                  >
                    <span class="reasoning-toggle-icon" :class="{ expanded: msg.reasoningExpanded }">›</span>
                    <span>思考过程</span>
                  </div>
                  <div v-if="msg.reasoningExpanded" class="reasoning-content">
                    {{ msg.reasoningContent }}
                  </div>
                </div>
                <div v-if="msg.content" class="answer-block">{{ msg.content }}</div>
                <span v-if="msg.streaming && msg.content" class="typing-cursor">|</span>
              </template>
              <template v-else>{{ msg.content }}</template>
            </div>
            <div v-if="msg.meta" class="message-meta">{{ msg.meta }}</div>
          </div>
        </div>

        <div class="chat-input-area">
          <div class="input-shell">
            <textarea
              ref="inputRef"
              v-model="inputValue"
              class="chat-input"
              rows="1"
              placeholder="输入问题，例如：帮我查询明天北京到上海的车票"
              :disabled="isSending"
              @input="autoResizeInput"
              @keydown.enter.exact.prevent="sendMessage"
            />
            <button
              class="input-tool-button"
              type="button"
              title="全屏输入"
              aria-label="全屏输入"
              :disabled="isSending"
              @click="openFullscreenInput"
            >
              <FullscreenOutlined />
            </button>
            <button
              class="send-button"
              type="button"
              :disabled="isSending || !inputValue.trim()"
              @click="sendMessage"
            >
              <SendOutlined />
            </button>
          </div>
        </div>

        <div v-if="isInputFullscreen" class="fullscreen-input-layer">
          <div class="fullscreen-input-panel">
            <div class="fullscreen-input-header">
              <span>全屏输入</span>
              <button
                class="input-tool-button"
                type="button"
                title="退出全屏输入"
                aria-label="退出全屏输入"
                @click="closeFullscreenInput"
              >
                <FullscreenExitOutlined />
              </button>
            </div>
            <textarea
              ref="fullscreenInputRef"
              v-model="inputValue"
              class="fullscreen-input"
              placeholder="输入完整问题..."
              :disabled="isSending"
              @keydown.enter.ctrl.prevent="sendMessage"
            />
            <div class="fullscreen-input-actions">
              <button class="plain-action-button" type="button" @click="closeFullscreenInput">收起</button>
              <button
                class="primary-action-button"
                type="button"
                :disabled="isSending || !inputValue.trim()"
                @click="sendMessage"
              >
                发送
              </button>
            </div>
          </div>
        </div>
      </div>
    </Drawer>
  </Header>
</template>

<script setup>
import {
  Layout,
  Avatar,
  Dropdown,
  Menu,
  MenuItem,
  message,
  Drawer
} from 'ant-design-vue'
import {
  CustomerServiceOutlined,
  FullscreenExitOutlined,
  FullscreenOutlined,
  SendOutlined,
  HistoryOutlined,
  DeleteOutlined
} from '@ant-design/icons-vue'
import { useRouter, useRoute } from 'vue-router'
import { reactive, toRefs, watch, ref, nextTick } from 'vue'
import {
  fetchLogout,
  fetchAiChatStream,
  createConversation,
  getConversationList,
  getConversationDetail,
  deleteConversation
} from '@/service'
import Cookie from 'js-cookie'
import dayjs from 'dayjs'

const username = Cookie.get('username')
const { Header } = Layout

const props = defineProps({
  isLogin: Boolean
})

const { isLogin } = toRefs(props)

const state = reactive({
  username
})

const router = useRouter()
const route = useRoute()
const isChatVisible = ref(false)
const inputValue = ref('')
const isSending = ref(false)
const isInputFullscreen = ref(false)
const currentConversationId = ref(null)
const messageListRef = ref(null)
const inputRef = ref(null)
const fullscreenInputRef = ref(null)
const streamController = ref(null)
const conversations = ref([])
const conversationsLoading = ref(false)
const historyDropdownVisible = ref(false)
const isAuthenticated = () => Boolean(Cookie.get('token') && Cookie.get('username'))
const guideAiError = (errorMessage) => {
  if (!errorMessage) return
  if (errorMessage.includes('用户未登录')) {
    message.warning('请先登录后再使用智能客服')
    isChatVisible.value = false
    router.push({
      name: 'login',
      query: { redirect: route.fullPath }
    })
  } else if (errorMessage.includes('用户未实名认证')) {
    message.warning('请先完成实名认证后再使用智能客服')
    isChatVisible.value = false
    router.push('/userInfo')
  }
}
const chatMessages = ref([])
const messageCache = new Map()

const getWelcomeMessage = () => ({
  id: 1,
  role: 'ai',
  content: '',
  parts: [
    { text: '您好 👋 我是您的铁路出行好帮手 ' },
    { text: '铁宝', highlight: true },
    { text: '。不论是搜车票、查订单，还是想了解退改签规则，随时交给我。' }
  ],
  meta: '铁宝 · AI Agent · 刚刚'
})

chatMessages.value = [getWelcomeMessage()]

const formatTime = (time) => {
  if (!time) return ''
  const d = dayjs(time)
  const now = dayjs()
  if (d.isSame(now, 'day')) return d.format('HH:mm')
  if (d.isSame(now, 'year')) return d.format('MM-DD HH:mm')
  return d.format('YYYY-MM-DD HH:mm')
}

const loadConversations = async () => {
  conversationsLoading.value = true
  try {
    const res = await getConversationList()
    if (res.success && res.data) {
      const list = res.data || []
      list.sort((a, b) => {
        const timeA = a.updateTime ? new Date(a.updateTime).getTime() : 0
        const timeB = b.updateTime ? new Date(b.updateTime).getTime() : 0
        return timeB - timeA
      })
      conversations.value = list
    }
  } catch (err) {
    console.error('加载对话列表失败:', err)
  } finally {
    conversationsLoading.value = false
  }
}

const handleSelectConversation = async (conv) => {
  if (conv.id === currentConversationId.value) {
    historyDropdownVisible.value = false
    return
  }
  if (isSending.value) cancelStreaming()

  // 缓存当前对话消息（保留思考过程）
  if (currentConversationId.value && chatMessages.value.length > 0) {
    messageCache.set(currentConversationId.value, [...chatMessages.value])
  }

  currentConversationId.value = conv.id
  chatMessages.value = []
  historyDropdownVisible.value = false

  // 优先使用本地缓存（包含思考过程）
  if (messageCache.has(conv.id)) {
    chatMessages.value = messageCache.get(conv.id)
    scrollToBottom()
    return
  }

  try {
    const res = await getConversationDetail(conv.id)
    if (res.success && res.data) {
      const messageList = res.data.messages || []
      chatMessages.value = messageList.map((msg) => ({
        id: msg.id || Date.now() + Math.random(),
        role: msg.role === 'assistant' ? 'ai' : msg.role,
        content: msg.content || '',
        reasoningContent: msg.reasoningContent || '',
        reasoningExpanded: false,
        meta: msg.role === 'assistant'
          ? `铁宝 · AI Agent · ${formatTime(msg.createTime)}`
          : undefined
      }))
      scrollToBottom()
    } else {
      message.error('该对话已失效，已自动移除')
      conversations.value = conversations.value.filter((c) => c.id !== conv.id)
      currentConversationId.value = null
      chatMessages.value = [getWelcomeMessage()]
    }
  } catch (err) {
    console.error('加载对话历史失败:', err)
    message.error('加载对话历史失败')
    currentConversationId.value = null
    chatMessages.value = [getWelcomeMessage()]
  }
}

const handleNewConversation = () => {
  if (isSending.value) cancelStreaming()
  if (currentConversationId.value && chatMessages.value.length > 0) {
    messageCache.set(currentConversationId.value, [...chatMessages.value])
  }
  currentConversationId.value = null
  chatMessages.value = [getWelcomeMessage()]
  historyDropdownVisible.value = false
}

const handleDeleteConversation = async (conv) => {
  try {
    const res = await deleteConversation(conv.id)
    if (res.success) {
      message.success('删除成功')
      conversations.value = conversations.value.filter((c) => c.id !== conv.id)
      if (currentConversationId.value === conv.id) {
        currentConversationId.value = null
        chatMessages.value = [getWelcomeMessage()]
      }
    } else {
      message.error(res.message || '删除失败')
    }
  } catch (err) {
    console.error('删除对话失败:', err)
    message.error('删除失败')
  }
}

const toggleHistory = () => {
  historyDropdownVisible.value = !historyDropdownVisible.value
}

const openChat = () => {
  if (!isAuthenticated()) {
    message.warning('请先登录后再使用智能客服')
    router.push({
      name: 'login',
      query: { redirect: route.fullPath }
    })
    return
  }
  isChatVisible.value = true
  loadConversations()
  scrollToBottom()
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    }
  })
}

const cancelStreaming = () => {
  if (streamController.value) {
    streamController.value.abort()
    streamController.value = null
  }
  isSending.value = false
}

const resizeTextarea = (target) => {
  if (!target) return
  const lineHeight = 22
  const verticalPadding = 2
  const maxHeight = lineHeight * 5 + verticalPadding
  target.style.height = 'auto'
  const nextHeight = Math.min(target.scrollHeight, maxHeight)
  target.style.height = `${nextHeight}px`
  target.style.overflowY = target.scrollHeight > maxHeight ? 'auto' : 'hidden'
}

const autoResizeInput = (event) => {
  resizeTextarea(event?.target || inputRef.value)
}

const syncInputHeight = () => {
  nextTick(() => {
    resizeTextarea(inputRef.value)
  })
}

const openFullscreenInput = () => {
  isInputFullscreen.value = true
  syncInputHeight()
}

const closeFullscreenInput = () => {
  isInputFullscreen.value = false
  syncInputHeight()
}

const sendMessage = async () => {
  const text = inputValue.value.trim()
  if (!text || isSending.value) return

  const userMessage = {
    id: Date.now(),
    role: 'user',
    content: text
  }
  const aiMessage = reactive({
    id: Date.now() + 1,
    role: 'ai',
    content: '',
    reasoningContent: '',
    reasoningExpanded: false,
    meta: '铁宝 · AI Agent · 正在输入',
    streaming: true
  })

  chatMessages.value.push(userMessage, aiMessage)
  inputValue.value = ''
  isInputFullscreen.value = false
  isSending.value = true
  streamController.value = new AbortController()
  syncInputHeight()
  scrollToBottom()

  try {
    if (!currentConversationId.value) {
      const createRes = await createConversation()
      if (createRes.success && createRes.data) {
        currentConversationId.value = createRes.data.id
      }
    }

    await fetchAiChatStream(
      {
        sessionId: currentConversationId.value,
        message: text
      },
      {
        signal: streamController.value.signal,
        onChunk: (chunk) => {
          if (chunk.sessionId && !currentConversationId.value) {
            currentConversationId.value = chunk.sessionId
          }
          if (chunk.error || chunk.eventType === 'ERROR') {
            aiMessage.content = chunk.error || chunk.answer || '服务暂时不可用，请稍后再试。'
            aiMessage.meta = '铁宝 · AI Agent · 发送失败'
            aiMessage.streaming = false
            guideAiError(aiMessage.content)
            return
          }

          if (chunk.eventType === 'TOOL_START') {
            aiMessage.meta = '铁宝 · AI Agent · 正在调用工具查询...'
          } else if (chunk.eventType === 'TOOL_END') {
            aiMessage.meta = '铁宝 · AI Agent · 工具调用完成，正在总结...'
          } else if (chunk.eventType === 'ASK_USER') {
            aiMessage.meta = '铁宝 · AI Agent · 正在询问'
          }

          if (chunk.delta) {
            aiMessage.content += chunk.delta
          }

          if (chunk.reasoningDelta) {
            aiMessage.reasoningContent += chunk.reasoningDelta
            if (!aiMessage.content) {
              aiMessage.meta = '铁宝 · AI Agent · 正在思考'
            }
          }

          if (chunk.done || chunk.eventType === 'DONE') {
            if (!aiMessage.content && chunk.answer) {
               aiMessage.content = chunk.answer
            }
            aiMessage.meta = '铁宝 · AI Agent · 刚刚'
            aiMessage.streaming = false
            loadConversations()
          }
          scrollToBottom()
        }
      }
    )
    if (!aiMessage.content) {
      aiMessage.content = '暂时没有收到回复，请稍后再试。'
      aiMessage.meta = '铁宝 · AI Agent · 发送失败'
    }
  } catch (error) {
    if (error.name !== 'AbortError') {
      aiMessage.content = error.message || '服务暂时不可用，请稍后再试。'
      aiMessage.meta = '铁宝 · AI Agent · 发送失败'
      guideAiError(aiMessage.content)
    }
  } finally {
    aiMessage.streaming = false
    isSending.value = false
    streamController.value = null
    scrollToBottom()
  }
}

watch(
  () => route.fullPath,
  () => {
    state.username = Cookie.get('username')
  },
  { immediate: true }
)

const logout = () => {
  const token = Cookie.get('token')
  fetchLogout({ accessToken: token }).then((res) => {
    if (res.success) {
      message.success('退出成功')
      location.href = 'login'
      Cookie.remove('token')
      Cookie.remove('username')
    }
  })
}
</script>

<style lang="scss" scoped>
.ant-layout-header {
  position: fixed;
  width: 100%;
  min-width: 800px;
  height: 64px;
  top: 0;
  z-index: 100;
  background-color: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(10px);
  box-shadow: 0 1px 0 rgba(15, 23, 42, 0.06);
}

.isLogin.ant-layout-header {
  background-color: transparent;
}

.header-wrapper {
  display: flex;
  flex: 1;
  flex-direction: row;
  justify-content: space-between;
  flex-wrap: nowrap;
  color: #333;
  height: 64px;
  align-items: center;
}

.logo {
  font-size: 19px;
  font-weight: 600;
  color: #333;
  font-family: Helvetica, Tahoma, Arial, 'PingFang SC', 'Hiragino Sans GB', 'Heiti SC', 'Microsoft YaHei', 'WenQuanYi Micro Hei';
  cursor: pointer;
  margin-left: -30px;
  letter-spacing: 0;
  transition: color 0.18s ease;
}

.logo:hover {
  color: #000;
}

.nav-list-wrapper {
  display: flex;
  text-decoration: none;
  list-style: none;
  margin: 0;
  align-items: center;

  a {
    color: #333;
    cursor: pointer;

    li {
      padding: 0 30px;
      display: flex;
      align-items: center;
    }
  }
}

.chat-btn {
  gap: 4px;
  padding: 0 15px !important;
  font-size: 15px;
  transition: color 0.3s;

  &:hover {
    color: #111;
  }
}

:deep(.ant-avatar) {
  box-shadow: 0 0 0 4px #f5f7fa;
  transition: box-shadow 0.18s ease, transform 0.18s ease;
}

:deep(.ant-avatar:hover) {
  box-shadow: 0 0 0 4px #eef0f4;
  transform: translateY(-1px);
}

.chat-panel {
  position: relative;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.chat-topbar {
  height: 72px;
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 28px 12px;
  border-bottom: 1px solid #f1f1f1;
}

.chat-title {
  font-size: 20px;
  line-height: 24px;
  font-weight: 700;
  color: #111;
}

.chat-subtitle {
  margin-top: 2px;
  font-size: 13px;
  line-height: 18px;
  color: #6f747c;
}

.close-btn {
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 50%;
  background: #f4f4f4;
  color: #202124;
  font-size: 24px;
  line-height: 1;
  cursor: pointer;
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.topbar-btn {
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: #6f747c;
  font-size: 16px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: background-color 0.18s ease, color 0.18s ease;

  &:hover {
    background: #f4f4f4;
    color: #111;
  }
}

.history-backdrop {
  position: absolute;
  inset: 72px 0 0;
  z-index: 9;
  background: transparent;
}

.history-dropdown {
  position: absolute;
  top: 72px;
  left: 16px;
  right: 16px;
  max-height: 420px;
  z-index: 10;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
  border: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid #f5f5f5;
  flex-shrink: 0;
}

.history-title {
  font-size: 14px;
  font-weight: 600;
  color: #111;
}

.history-new-btn {
  border: 0;
  background: transparent;
  color: #1677ff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  padding: 4px 10px;
  border-radius: 6px;
  transition: background-color 0.18s ease;

  &:hover {
    background: #f0f7ff;
  }
}

.history-list {
  flex: 1;
  overflow-y: auto;
  padding: 6px;

  &::-webkit-scrollbar {
    width: 4px;
  }

  &::-webkit-scrollbar-thumb {
    background: #e0e0e0;
    border-radius: 2px;
  }
}

.history-loading,
.history-empty {
  text-align: center;
  padding: 28px 16px;
  color: #999;
  font-size: 13px;
}

.history-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.15s ease;

  &:hover {
    background: #f5f7fa;

    .history-item-delete {
      opacity: 1;
    }
  }

  &.active {
    background: #f0f7ff;

    .history-item-title {
      color: #1677ff;
      font-weight: 600;
    }
  }
}

.history-item-body {
  flex: 1;
  min-width: 0;
}

.history-item-title {
  font-size: 13px;
  color: #333;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.4;
}

.history-item-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 2px;
  font-size: 11px;
  color: #999;
}

.history-item-count {
  color: #bbb;
}

.history-item-delete {
  width: 26px;
  height: 26px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #ccc;
  font-size: 12px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.15s ease, color 0.15s ease, background-color 0.15s ease;
  flex-shrink: 0;

  &:hover {
    color: #ff4d4f;
    background: #fff1f0;
  }
}

.chat-message-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.message-row {
  display: flex;
  flex-direction: column;
  max-width: 86%;

  &.ai {
    align-self: flex-start;

    .message-bubble {
      color: #1f2328;
      background: #f3f3f3;
      border-radius: 4px 18px 18px 18px;
    }
  }

  &.user {
    align-self: flex-end;
    align-items: flex-end;

    .message-bubble {
      color: #fff;
      background: #050505;
      border-radius: 18px 4px 18px 18px;
      min-width: 74px;
      text-align: left;
      font-weight: 500;
    }
  }
}

.message-bubble {
  padding: 14px 20px;
  font-size: 15px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.reasoning-block {
  margin-bottom: 10px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.03);
  padding: 8px 12px;
}

.reasoning-toggle {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  font-size: 12px;
  color: #8c8c8c;
  user-select: none;

  &:hover {
    color: #555;
  }
}

.reasoning-toggle-icon {
  display: inline-block;
  font-size: 14px;
  font-weight: 600;
  transition: transform 0.2s ease;

  &.expanded {
    transform: rotate(90deg);
  }
}

.reasoning-content {
  margin-top: 8px;
  padding-left: 12px;
  border-left: 2px solid #e0e0e0;
  color: #777;
  font-size: 13px;
  line-height: 1.55;
}

.answer-block {
  color: inherit;
}

.typing-cursor {
  display: inline;
  animation: blink 0.8s infinite;
  color: #1677ff;
  font-weight: 100;
}

.brand-highlight {
  font-weight: 700;
  color: #050505;
  padding: 0 1px;
}

.message-meta {
  margin: 6px 0 0 20px;
  font-size: 12px;
  line-height: 18px;
  color: #9ca3af;
}

.typing-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-right: 5px;
  border-radius: 50%;
  background: #9aa0a6;
  animation: typing-pulse 1.2s infinite ease-in-out;

  &:nth-child(2) {
    animation-delay: 0.15s;
  }

  &:nth-child(3) {
    margin-right: 0;
    animation-delay: 0.3s;
  }
}

.chat-input-area {
  flex: 0 0 auto;
  padding: 12px 24px 18px;
  border-top: 1px solid #f1f1f1;
  background: #fff;
}

.input-shell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 52px;
  padding: 7px 8px 7px 20px;
  border: 1px solid #e8e8e8;
  border-radius: 26px;
  background: #fafafa;
}

.chat-input {
  flex: 1;
  height: 22px;
  min-height: 22px;
  max-height: 112px;
  resize: none;
  border: 0;
  outline: none;
  padding: 0;
  background: transparent;
  color: #1f2328;
  font-size: 13px;
  line-height: 22px;
  letter-spacing: 0;
  overflow-y: hidden;
}

.chat-input::placeholder {
  color: #8a8f98;
  line-height: 22px;
}

.input-tool-button {
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: #6f747c;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  transition: background-color 0.18s ease, color 0.18s ease;

  &:hover {
    background: #eeeeee;
    color: #111;
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.35;
  }
}

.send-button {
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  border: 0;
  border-radius: 50%;
  background: #050505;
  color: #fff;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 17px;
  transition: opacity 0.18s ease;

  &:disabled {
    cursor: not-allowed;
    opacity: 0.35;
  }
}

.fullscreen-input-layer {
  position: absolute;
  inset: 72px 0 0;
  z-index: 2;
  display: flex;
  padding: 24px;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(8px);
}

.fullscreen-input-panel {
  width: 100%;
  display: flex;
  flex-direction: column;
  border: 1px solid #e8e8e8;
  border-radius: 22px;
  background: #fff;
  box-shadow: 0 18px 50px rgba(15, 23, 42, 0.12);
}

.fullscreen-input-header {
  flex: 0 0 auto;
  height: 58px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 18px 0 22px;
  color: #111;
  font-size: 16px;
  font-weight: 700;
  border-bottom: 1px solid #f1f1f1;
}

.fullscreen-input {
  flex: 1;
  min-height: 0;
  resize: none;
  border: 0;
  outline: none;
  padding: 22px;
  color: #1f2328;
  font-size: 16px;
  line-height: 26px;
  letter-spacing: 0;
}

.fullscreen-input::placeholder {
  color: #8a8f98;
}

.fullscreen-input-actions {
  flex: 0 0 auto;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 14px 18px 18px;
  border-top: 1px solid #f1f1f1;
}

.plain-action-button,
.primary-action-button {
  min-width: 72px;
  height: 38px;
  border: 0;
  border-radius: 19px;
  font-size: 14px;
  cursor: pointer;
}

.plain-action-button {
  background: #f1f1f1;
  color: #30343b;
}

.primary-action-button {
  background: #050505;
  color: #fff;

  &:disabled {
    cursor: not-allowed;
    opacity: 0.35;
  }
}

@keyframes typing-pulse {
  0%, 80%, 100% {
    opacity: 0.35;
    transform: translateY(0);
  }

  40% {
    opacity: 1;
    transform: translateY(-2px);
  }
}

@keyframes blink {
  0%, 50% {
    opacity: 1;
  }
  51%, 100% {
    opacity: 0;
  }
}

@media (max-width: 720px) {
  .ant-layout-header {
    min-width: 0;
  }

  .logo {
    margin-left: 0;
  }

  .message-bubble {
    font-size: 14px;
    padding: 12px 16px;
  }

  .message-row {
    max-width: 92%;
  }
}
</style>
