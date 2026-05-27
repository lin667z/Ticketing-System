<template>
  <div class="ai-chat-page">
    <!-- 左侧对话列表 -->
    <ConversationSidebar
      :conversations="conversations"
      :currentId="currentConversationId"
      :loading="conversationsLoading"
      :collapsed="sidebarCollapsed"
      @select="handleSelectConversation"
      @create="handleCreateConversation"
      @rename="handleRenameConversation"
      @delete="handleDeleteConversation"
      @toggle-collapse="sidebarCollapsed = !sidebarCollapsed"
    />

    <!-- 右侧聊天区域 -->
    <div class="chat-main">
      <!-- 顶部标题栏 -->
      <div class="chat-header">
        <div>
          <div class="chat-title">铁宝</div>
          <div class="chat-subtitle">AI Agent · 在线</div>
        </div>
      </div>

      <!-- 消息列表区域 -->
      <div class="chat-messages" ref="messagesContainer" @scroll="onScroll">
        <!-- 空状态 -->
        <div v-if="messages.length === 0 && !isStreaming" class="empty-chat">
          <div class="empty-icon">
            <RobotOutlined />
          </div>
          <div class="empty-title">铁路出行好帮手 铁宝</div>
          <div class="empty-desc">可以问我车票查询、订单管理、退改签规则等问题</div>
          <div class="quick-prompts">
            <div
              v-for="prompt in quickPrompts"
              :key="prompt"
              class="quick-prompt-item"
              @click="sendQuickPrompt(prompt)"
            >
              {{ prompt }}
            </div>
          </div>
        </div>

        <!-- 消息气泡 -->
        <div
          v-for="msg in messages"
          :key="msg.id"
          :class="['message-row', msg.role]"
        >
          <div class="message-body">
            <!-- 工具调用状态 -->
            <div v-if="msg.toolStatus" class="tool-indicator">
              <LoadingOutlined v-if="msg.toolStatus === 'calling'" spin />
              <CheckCircleOutlined v-else-if="msg.toolStatus === 'done'" />
              <span class="tool-text">{{ msg.toolMessage }}</span>
            </div>

            <div class="message-bubble">
              <!-- 流式加载动画（等待首字） -->
              <template v-if="msg.streaming && !msg.content && !msg.reasoningContent">
                <span class="typing-dot"></span>
                <span class="typing-dot"></span>
                <span class="typing-dot"></span>
              </template>

              <!-- 推理过程展示（可折叠） -->
              <div v-if="msg.reasoningContent" class="reasoning-block">
                <div class="reasoning-header" @click="msg.reasoningCollapsed = !msg.reasoningCollapsed">
                  <RightOutlined v-if="msg.reasoningCollapsed" class="reasoning-chevron" />
                  <DownOutlined v-else class="reasoning-chevron" />
                  <span class="reasoning-label">思考过程</span>
                  <span v-if="msg.streaming && !msg.content" class="typing-cursor">|</span>
                </div>
                <div v-show="!msg.reasoningCollapsed" class="reasoning-content">{{ msg.reasoningContent }}</div>
              </div>

              <!-- 消息内容：统一使用 Markdown 渲染（流式和完成态） -->
              <div
                v-if="msg.content && msg.renderedHtml"
                class="message-text markdown-body"
                v-html="msg.renderedHtml"
              ></div>

              <!-- 兜底纯文本（renderedHtml 尚未生成时） -->
              <div
                v-if="msg.content && !msg.renderedHtml"
                class="message-text"
              >{{ msg.content }}</div>

              <!-- 业务卡片组件 -->
              <div
                v-if="msg.components && msg.components.length > 0 && !msg.streaming"
                class="message-components"
              >
                <TrainCard
                  v-for="(comp, cIdx) in msg.components.filter(c => c.type === 'train_card')"
                  :key="comp.id || cIdx"
                  :data="comp.data"
                />
                <OrderCard
                  v-for="(comp, cIdx) in msg.components.filter(c => c.type === 'order_card')"
                  :key="comp.id || cIdx"
                  :data="comp.data"
                />
              </div>

              <!-- 流式打字光标 -->
              <span v-if="msg.streaming && msg.content" class="typing-cursor">|</span>
            </div>

            <!-- 消息元信息 -->
            <div class="message-meta">
              <span>{{ msg.timestamp || formatTime(Date.now()) }}</span>
            </div>

            <!-- 操作按钮 -->
            <div v-if="msg.content && !msg.streaming" class="message-actions">
              <Button type="text" size="small" @click="copyMessage(msg.content)">
                <CopyOutlined />
              </Button>
            </div>
          </div>
        </div>

        <!-- 错误提示 -->
        <div v-if="errorMessage && !isStreaming" class="error-banner">
          <Alert
            :message="errorMessage"
            type="error"
            closable
            @close="errorMessage = ''"
            show-icon
          />
          <Button type="link" @click="retryLastMessage" v-if="lastUserMessage">
            重试
          </Button>
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="chat-input-area">
        <div class="input-shell">
          <textarea
            ref="inputRef"
            v-model="inputMessage"
            class="chat-input"
            :placeholder="isStreaming ? 'AI 正在回复中...' : '输入您的问题，例如：帮我查询明天北京到上海的车票'"
            :disabled="isStreaming"
            rows="1"
            @input="autoResize"
            @keydown.enter.exact.prevent="sendMessage"
            @keydown.enter.shift.exact="newline"
          />
          <button
            class="send-button"
            type="button"
            :disabled="!inputMessage.trim() || isStreaming"
            @click="sendMessage"
          >
            <SendOutlined />
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import {
  Button,
  Alert,
  message
} from 'ant-design-vue'
import {
  SendOutlined,
  RobotOutlined,
  CopyOutlined,
  LoadingOutlined,
  CheckCircleOutlined,
  DownOutlined,
  RightOutlined
} from '@ant-design/icons-vue'
import {
  fetchAiChatStream,
  createConversation,
  deleteConversation,
  renameConversation,
  getConversationList,
  getConversationDetail
} from '@/service'
import { renderMarkdown, preprocessStreamingContent } from '@/utils/markdown-renderer'
import ConversationSidebar from './components/ConversationSidebar.vue'
import TrainCard from './components/TrainCard.vue'
import OrderCard from './components/OrderCard.vue'
import dayjs from 'dayjs'

// ===================== 响应式状态 =====================

const sidebarCollapsed = ref(false)

// 对话列表
const conversations = ref([])
const conversationsLoading = ref(false)
const conversationsLoaded = ref(false)
const currentConversationId = ref(null)

// 消息列表
const messages = ref([])
const inputMessage = ref('')
const isStreaming = ref(false)
const tokenUsage = ref(null)
const errorMessage = ref('')
const lastUserMessage = ref('')
const messagesContainer = ref(null)
const inputRef = ref(null)
const streamController = ref(null)
const shouldAutoScroll = ref(true)

// 流式 Markdown 渲染的防抖定时器
let streamRenderTimer = null
const STREAM_RENDER_INTERVAL = 80 // ms，每 80ms 渲染一次 markdown

const renderStreamingMarkdown = (aiMsg) => {
  if (!aiMsg.content) return
  const safeContent = preprocessStreamingContent(aiMsg.content)
  const { html } = renderMarkdown(safeContent || aiMsg.content)
  aiMsg.renderedHtml = html
}

const scheduleStreamRender = (aiMsg) => {
  // 每次收到新的 delta 都重置防抖定时器，确保：
  // 1. 高速涌入时不频繁渲染（防抖）
  // 2. 最后一批 delta 在流停止后一定会被渲染（不再有新的 chunk 来触发）
  if (streamRenderTimer) {
    clearTimeout(streamRenderTimer)
  }
  streamRenderTimer = setTimeout(() => {
    streamRenderTimer = null
    renderStreamingMarkdown(aiMsg)
  }, STREAM_RENDER_INTERVAL)
}

// 快捷提示
const quickPrompts = [
  '帮我查询明天北京到上海的高铁',
  '如何办理退票？',
  '查询我的订单状态',
  '儿童票有什么优惠？',
  '改签需要什么条件？'
]

// ===================== 生命周期 =====================

onMounted(() => {
  loadConversations()
})

// ===================== 对话管理 =====================

// 加载对话列表
const loadConversations = async () => {
  conversationsLoading.value = true
  try {
    const res = await getConversationList()
    if (res.success && res.data) {
      // 按更新时间倒序排列
      const list = res.data || []
      list.sort((a, b) => {
        const timeA = a.updateTime ? new Date(a.updateTime).getTime() : 0
        const timeB = b.updateTime ? new Date(b.updateTime).getTime() : 0
        return timeB - timeA
      })
      conversations.value = list
    }
    conversationsLoaded.value = true
  } catch (err) {
    console.error('加载对话列表失败:', err)
    conversationsLoaded.value = true
  } finally {
    conversationsLoading.value = false
  }
}

// 新建对话
const handleCreateConversation = () => {
  if (isStreaming.value) {
    cancelStream()
  }
  currentConversationId.value = null
  messages.value = []
  tokenUsage.value = null
  errorMessage.value = ''
  lastUserMessage.value = ''
  inputRef.value?.focus()
}

// 选择对话
const handleSelectConversation = async (conv) => {
  const convId = conv.id
  if (convId === currentConversationId.value) return

  // 取消当前流式请求
  if (isStreaming.value) {
    cancelStream()
  }

  currentConversationId.value = convId
  messages.value = []
  tokenUsage.value = null
  errorMessage.value = ''
  lastUserMessage.value = ''

  // 加载该对话的历史消息
  try {
    const res = await getConversationDetail(convId)
    if (res.success && res.data) {
      const detail = res.data
      const messageList = detail.messages || []
      const historyMessages = messageList.map((msg) => {
        const msgContent = msg.content || ''
        const role = mapRole(msg.role)
        const { html, components: parsedComponents } = renderMarkdown(msgContent)
        return {
          id: msg.id || Date.now() + Math.random(),
          role: role,
          content: msgContent,
          reasoningContent: msg.reasoningContent || '',
          reasoningCollapsed: true,
          timestamp: formatTime(msg.createTime),
          usage: msg.tokenCount ? { totalTokens: msg.tokenCount } : null,
          streaming: false,
          components: parsedComponents || [],
          renderedHtml: html
        }
      })
      messages.value = historyMessages
      await nextTick()
      scrollToBottom()
    } else {
      message.error('该对话已失效，已自动移除')
      conversations.value = conversations.value.filter((c) => c.id !== convId)
      currentConversationId.value = null
    }
  } catch (err) {
    console.error('加载对话历史失败:', err)
    message.error('加载对话历史失败')
  }
}

// 将后端 role 映射为前端 role (assistant → ai)
const mapRole = (role) => {
  if (role === 'assistant' || role === 'ai') return 'ai'
  if (role === 'user') return 'user'
  return role
}

// 重命名对话
const handleRenameConversation = async ({ id, newName }) => {
  try {
    const res = await renameConversation(id, newName)
    if (res.success) {
      message.success('重命名成功')
      // 更新本地列表中的标题
      const conv = conversations.value.find((c) => c.id === id)
      if (conv) {
        conv.title = newName
      }
    } else {
      message.error(res.message || '重命名失败')
    }
  } catch (err) {
    console.error('重命名对话失败:', err)
    message.error('重命名失败')
  }
}

// 删除对话
const handleDeleteConversation = async (conv) => {
  try {
    const res = await deleteConversation(conv.id)
    if (res.success) {
      message.success('删除成功')
      // 从本地列表中移除
      conversations.value = conversations.value.filter((c) => c.id !== conv.id)
      // 如果删除的是当前对话，切回空状态
      if (currentConversationId.value === conv.id) {
        currentConversationId.value = null
        messages.value = []
        tokenUsage.value = null
        errorMessage.value = ''
        lastUserMessage.value = ''
      }
    } else {
      message.error(res.message || '删除失败')
    }
  } catch (err) {
    console.error('删除对话失败:', err)
    message.error('删除失败')
  }
}

// ===================== 消息发送 =====================

// 发送快捷提示
const sendQuickPrompt = (prompt) => {
  inputMessage.value = prompt
  sendMessage()
}

// 发送消息
const sendMessage = async () => {
  const text = inputMessage.value.trim()
  if (!text || isStreaming.value) return

  lastUserMessage.value = text

  // 添加用户消息
  const userMsg = {
    id: Date.now(),
    role: 'user',
    content: text,
    timestamp: formatTime(Date.now())
  }
  messages.value.push(userMsg)

  // 添加 AI 占位消息
  const aiMsg = {
    id: Date.now() + 1,
    role: 'ai',
    content: '',
    reasoningContent: '',
    reasoningCollapsed: false,
    timestamp: '',
    streaming: true,
    toolStatus: null,
    toolMessage: '',
    usage: null,
    components: [],
    renderedHtml: ''
  }
  messages.value.push(aiMsg)

  inputMessage.value = ''
  isStreaming.value = true
  errorMessage.value = ''
  tokenUsage.value = null
  shouldAutoScroll.value = true

  // 重置输入框高度
  if (inputRef.value) {
    inputRef.value.style.height = 'auto'
  }

  await nextTick()
  scrollToBottom()

  // 如果是新对话，先创建对话
  if (!currentConversationId.value) {
    try {
      const createRes = await createConversation()
      if (createRes.success && createRes.data) {
        currentConversationId.value = createRes.data.id
      }
    } catch (err) {
      console.error('创建对话失败:', err)
    }
  }

  // 发起 SSE 流式请求
  streamController.value = new AbortController()

  try {
    await fetchAiChatStream(
      {
        sessionId: currentConversationId.value,
        message: text
      },
      {
        signal: streamController.value.signal,
        onChunk: (chunk) => {
          handleChunk(chunk, aiMsg)
        }
      }
    )

    // 流结束后的收尾
    if (aiMsg.streaming) {
      if (streamRenderTimer) {
        clearTimeout(streamRenderTimer)
        streamRenderTimer = null
      }
      aiMsg.streaming = false
      aiMsg.timestamp = formatTime(Date.now())
      if (!aiMsg.content && !aiMsg.reasoningContent) {
        aiMsg.content = '暂时没有收到回复，请稍后再试。'
      }
      if (aiMsg.content) {
        const { html, components: parsedComponents } = renderMarkdown(aiMsg.content)
        aiMsg.renderedHtml = html
        if (parsedComponents && parsedComponents.length > 0) {
          aiMsg.components = [...aiMsg.components, ...parsedComponents]
        }
      }
    }

    // 刷新对话列表
    loadConversations()
  } catch (err) {
    if (err.name !== 'AbortError') {
      aiMsg.streaming = false
      aiMsg.timestamp = formatTime(Date.now())
      if (!aiMsg.content) {
        const errMsg = err.message || '服务暂时不可用，请稍后再试。'
        aiMsg.content = errMsg

        // 判断是否为限流错误
        if (errMsg.includes('429') || errMsg.includes('限流') || errMsg.includes('rate limit')) {
          errorMessage.value = '请求过于频繁，请稍后再试。'
        } else if (errMsg.includes('500') || errMsg.includes('服务器')) {
          errorMessage.value = '服务器繁忙，请稍后再试。'
        } else if (errMsg.includes('网络') || errMsg.includes('fetch')) {
          errorMessage.value = '网络连接异常，请检查网络后重试。'
        }
      }
      // 错误情况下也生成 renderedHtml
      if (aiMsg.content && !aiMsg.renderedHtml) {
        const { html } = renderMarkdown(aiMsg.content)
        aiMsg.renderedHtml = html
      }
    }
  } finally {
    isStreaming.value = false
    streamController.value = null
    await nextTick()
    scrollToBottom()
  }
}

// 处理 SSE 数据块
const handleChunk = (chunk, aiMsg) => {
  // 更新 conversationId（服务端返回时使用）
  if (chunk.sessionId && !currentConversationId.value) {
    currentConversationId.value = chunk.sessionId
  }

  // 处理错误事件
  if (chunk.eventType === 'ERROR' || chunk.error) {
    const errText = chunk.error || chunk.answer || '服务暂时不可用，请稍后再试。'
    if (errText.includes('会话不存在') || errText.includes('无权访问')) {
      message.error('该对话已失效，已自动移除')
      conversations.value = conversations.value.filter((c) => c.id !== currentConversationId.value)
      currentConversationId.value = null
      messages.value = messages.value.filter((m) => m.id !== aiMsg.id)
      return
    }
    aiMsg.content = errText
    aiMsg.streaming = false
    aiMsg.toolStatus = null
    return
  }

  // 处理重试提示事件（模型超时自动切换节点）
  if (chunk.eventType === 'RETRYING') {
    aiMsg.toolStatus = 'calling'
    aiMsg.toolMessage = chunk.delta || '当前线路繁忙，正在切换备用节点...'
    return
  }

  // 处理工具调用开始
  if (chunk.eventType === 'TOOL_START') {
    aiMsg.toolStatus = 'calling'
    aiMsg.toolMessage = chunk.toolName
      ? `正在调用：${chunk.toolName}...`
      : '正在查询相关信息...'
  }

  // 处理工具调用结束
  if (chunk.eventType === 'TOOL_END') {
    aiMsg.toolStatus = 'done'
    aiMsg.toolMessage = '查询完成，正在整理回复...'
  }

  // 处理 COMPONENT 事件（结构化组件数据）
  if (chunk.eventType === 'COMPONENT' || chunk.contentType === 'component') {
    if (chunk.componentType && chunk.componentData) {
      aiMsg.components.push({
        id: `comp-${chunk.componentType}-${aiMsg.components.length}`,
        type: chunk.componentType,
        data: chunk.componentData
      })
    } else if (chunk.delta) {
      // 尝试从 delta JSON 解析组件数据
      try {
        const compData = typeof chunk.delta === 'string' ? JSON.parse(chunk.delta) : chunk.delta
        if (compData.type && compData.data) {
          const compType = compData.type === 'train_card' ? 'train_card' : (compData.type === 'order_card' ? 'order_card' : compData.type)
          aiMsg.components.push({
            id: `comp-${compType}-${aiMsg.components.length}`,
            type: compType,
            data: compData.data
          })
        }
      } catch (e) {
        // 忽略解析错误
      }
    }
  }

  // 处理流式文本增量（排除 component delta）
  if (chunk.delta && chunk.eventType !== 'COMPONENT' && chunk.contentType !== 'component') {
    aiMsg.content += chunk.delta
    if (aiMsg.streaming) {
      scheduleStreamRender(aiMsg)
    }
  }

  // 处理推理增量
  if (chunk.reasoningDelta) {
    aiMsg.reasoningContent += chunk.reasoningDelta
  }

  // 处理完成事件
  if (chunk.eventType === 'DONE' || chunk.done) {
    // 清除流式渲染定时器，立即做最终渲染
    if (streamRenderTimer) {
      clearTimeout(streamRenderTimer)
      streamRenderTimer = null
    }
    aiMsg.streaming = false
    aiMsg.toolStatus = null
    aiMsg.timestamp = formatTime(Date.now())
    // 流结束后自动折叠推理过程，突出最终回答
    if (aiMsg.reasoningContent) {
      aiMsg.reasoningCollapsed = true
    }

    // 如果 answer 代替 delta 给了完整回复，直接赋值
    if (!aiMsg.content && chunk.answer) {
      aiMsg.content = chunk.answer
    }

    // 最终渲染：完整 Markdown
    if (aiMsg.content) {
      const { html, components: parsedComponents } = renderMarkdown(aiMsg.content)
      aiMsg.renderedHtml = html
      if (parsedComponents && parsedComponents.length > 0) {
        aiMsg.components = [...aiMsg.components, ...parsedComponents]
      }
    }

    // 记录 Token 用量
    if (chunk.usage) {
      aiMsg.usage = chunk.usage
      tokenUsage.value = chunk.usage
    }
  }

  // 自动滚动
  if (shouldAutoScroll.value) {
    nextTick(() => scrollToBottom())
  }
}

// ===================== 滚动控制 =====================

// 滚动到底部
const scrollToBottom = () => {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

// 监听用户滚动，判断是否在底部
const onScroll = () => {
  if (!messagesContainer.value) return
  const { scrollTop, scrollHeight, clientHeight } = messagesContainer.value
  // 距离底部小于 60px 认为在底部
  shouldAutoScroll.value = scrollHeight - scrollTop - clientHeight < 60
}

// ===================== 输入框 =====================

// 自动调整输入框高度
const autoResize = () => {
  const el = inputRef.value
  if (!el) return
  const lineHeight = 22
  const maxHeight = lineHeight * 5 + 16
  el.style.height = 'auto'
  const nextHeight = Math.min(el.scrollHeight, maxHeight)
  el.style.height = `${nextHeight}px`
  el.style.overflowY = el.scrollHeight > maxHeight ? 'auto' : 'hidden'
}

// Shift+Enter 换行
const newline = () => {
  // textarea 默认行为就是换行，不需要额外处理
  autoResize()
}

// ===================== 其他功能 =====================

// 取消流式请求
const cancelStream = () => {
  if (streamController.value) {
    streamController.value.abort()
    streamController.value = null
  }
  isStreaming.value = false

  // 找到最后一条 AI 消息，标记为非流式
  const lastAiMsg = [...messages.value].reverse().find((m) => m.role === 'ai')
  if (lastAiMsg && lastAiMsg.streaming) {
    lastAiMsg.streaming = false
    if (!lastAiMsg.content) {
      lastAiMsg.content = '已取消发送。'
    }
    // 取消后渲染 Markdown
    if (lastAiMsg.content && !lastAiMsg.renderedHtml) {
      const { html, components: parsedComponents } = renderMarkdown(lastAiMsg.content)
      lastAiMsg.renderedHtml = html
      if (parsedComponents && parsedComponents.length > 0) {
        lastAiMsg.components = [...lastAiMsg.components, ...parsedComponents]
      }
    }
  }
}

// 复制消息内容
const copyMessage = async (content) => {
  try {
    await navigator.clipboard.writeText(content)
    message.success('已复制到剪贴板')
  } catch (err) {
    // 降级方案
    const textarea = document.createElement('textarea')
    textarea.value = content
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    message.success('已复制到剪贴板')
  }
}

// 重试最后一条消息
const retryLastMessage = () => {
  if (!lastUserMessage.value) return
  errorMessage.value = ''
  // 移除最后一条失败的AI消息
  const lastIdx = messages.value.length - 1
  if (lastIdx >= 0 && messages.value[lastIdx].role === 'ai') {
    messages.value.splice(lastIdx, 1)
  }
  inputMessage.value = lastUserMessage.value
  sendMessage()
}

// ===================== 工具函数 =====================

// 格式化时间
const formatTime = (time) => {
  if (!time) return ''
  const d = dayjs(time)
  const now = dayjs()
  if (d.isSame(now, 'day')) {
    return d.format('HH:mm')
  }
  if (d.isSame(now, 'year')) {
    return d.format('MM-DD HH:mm')
  }
  return d.format('YYYY-MM-DD HH:mm')
}
</script>

<style lang="scss" scoped>
// ===================== 整体布局 =====================
.ai-chat-page {
  display: flex;
  height: calc(100vh - 64px - 42px); // 减去顶部导航和 margin
  background: #fff;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.035);
}

// ===================== 右侧聊天区域 =====================
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: #fff;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 28px 12px;
  border-bottom: 1px solid #f1f1f1;
  background: #fff;
  flex-shrink: 0;
  height: 72px;
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

// ===================== 消息区域 =====================
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;

  &::-webkit-scrollbar {
    width: 4px;
  }

  &::-webkit-scrollbar-thumb {
    background: #d9d9d9;
    border-radius: 2px;
  }
}

// 空状态
.empty-chat {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  text-align: center;
  padding: 48px 24px;
}

.empty-icon {
  font-size: 56px;
  color: #d9d9d9;
  margin-bottom: 16px;
}

.empty-title {
  font-size: 20px;
  font-weight: 600;
  color: #333;
  margin-bottom: 8px;
}

.empty-desc {
  font-size: 14px;
  color: #999;
  margin-bottom: 28px;
}

.quick-prompts {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
  max-width: 520px;
}

.quick-prompt-item {
  padding: 8px 16px;
  border: 1px solid #e8e8e8;
  border-radius: 20px;
  font-size: 13px;
  color: #555;
  cursor: pointer;
  transition: all 0.18s ease;
  background: #fafafa;

  &:hover {
    border-color: #1677ff;
    color: #1677ff;
    background: #f0f5ff;
  }
}

// 消息行
.message-row {
  display: flex;
  flex-direction: column;
  max-width: 86%;

  &.user {
    align-self: flex-end;
    align-items: flex-end;
  }

  &.ai {
    align-self: flex-start;
  }
}

.message-body {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

// 工具调用指示器
.tool-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  margin-bottom: 6px;
  font-size: 12px;
  color: #888;
  background: #f8f9fb;
  border-radius: 6px;
  border: 1px solid #eef0f4;

  .anticon {
    font-size: 13px;
  }
}

// 消息气泡
.message-bubble {
  padding: 14px 20px;
  font-size: 15px;
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;

  .ai & {
    color: #1f2328;
    background: #f3f3f3;
    border-radius: 4px 18px 18px 18px;
  }

  .user & {
    color: #fff;
    background: #050505;
    border-radius: 18px 4px 18px 18px;
    font-weight: 500;
  }
}

// 推理过程（可折叠）
.reasoning-block {
  margin-bottom: 10px;
  background: #fafbfc;
  border: 1px solid #ececec;
  border-radius: 8px;
  overflow: hidden;
}

.reasoning-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  cursor: pointer;
  user-select: none;
  font-size: 12px;
  color: #666;
  transition: background-color 0.15s ease;

  &:hover {
    background: #f0f2f5;
  }

  .reasoning-chevron {
    font-size: 10px;
    color: #999;
    transition: transform 0.15s ease;
  }

  .reasoning-label {
    font-weight: 500;
  }
}

.reasoning-content {
  padding: 8px 12px 10px 22px;
  border-top: 1px solid #ececec;
  color: #888;
  font-size: 13px;
  line-height: 1.6;
  font-style: italic;
  white-space: pre-wrap;
  word-break: break-word;
}

// 消息正文
.message-text {
  white-space: pre-wrap;
  word-break: break-word;
}

// ===================== Markdown 渲染样式 =====================
.markdown-body {
  word-break: break-word;
  line-height: 1.7;
  // 流式输出时内容增长的平滑过渡
  min-height: 0;

  // 段落
  p {
    margin: 0 0 8px 0;

    &:last-child {
      margin-bottom: 0;
    }
  }

  // 加粗
  strong {
    font-weight: 600;
    color: #1a1a1a;
  }

  // 斜体
  em {
    font-style: italic;
  }

  // 删除线
  s, del {
    text-decoration: line-through;
    color: #999;
  }

  // 链接
  a {
    color: #1677ff;
    text-decoration: none;

    &:hover {
      text-decoration: underline;
    }
  }

  // 标题
  h1, h2, h3, h4, h5, h6 {
    margin: 12px 0 6px 0;
    font-weight: 600;
    line-height: 1.4;
    color: #1a1a1a;

    &:first-child {
      margin-top: 0;
    }
  }

  h1 { font-size: 1.4em; }
  h2 { font-size: 1.25em; }
  h3 { font-size: 1.1em; }
  h4 { font-size: 1.05em; }

  // 列表
  ul, ol {
    margin: 4px 0;
    padding-left: 20px;

    li {
      margin-bottom: 2px;
    }
  }

  ul {
    list-style: disc;

    ul { list-style: circle; }
  }

  ol {
    list-style: decimal;
  }

  // 引用块
  blockquote {
    margin: 8px 0;
    padding: 6px 12px;
    border-left: 3px solid #e0e0e0;
    background: #f9fafb;
    color: #666;
    font-size: 13px;

    p {
      margin: 0;
    }
  }

  // 代码行内
  code {
    padding: 2px 6px;
    background: #f0f2f5;
    border-radius: 4px;
    font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
    font-size: 0.9em;
    color: #d63384;
  }

  // 代码块
  pre {
    margin: 8px 0;
    padding: 12px 14px;
    background: #1e1e1e;
    border-radius: 6px;
    overflow-x: auto;
    line-height: 1.5;

    code {
      padding: 0;
      background: transparent;
      color: #d4d4d4;
      font-size: 0.85em;
      white-space: pre;
    }
  }

  // 水平线
  hr {
    border: 0;
    border-top: 1px solid #eee;
    margin: 12px 0;
  }

  // 表格
  table {
    width: 100%;
    margin: 8px 0;
    border-collapse: collapse;
    font-size: 13px;
    border: 1px solid #e8e8e8;
    border-radius: 6px;
    overflow: hidden;
  }

  thead {
    background: #f5f7fa;

    th {
      padding: 8px 12px;
      font-weight: 600;
      color: #333;
      border-bottom: 2px solid #e0e0e0;
    }
  }

  tbody {
    tr {
      &:nth-child(even) {
        background: #fafbfc;
      }

      &:hover {
        background: #f0f5ff;
      }
    }

    td {
      padding: 7px 12px;
      color: #555;
      border-bottom: 1px solid #f0f0f0;
    }
  }

  th, td {
    text-align: left;

    &:first-child {
      padding-left: 14px;
    }

    &:last-child {
      padding-right: 14px;
    }
  }

  // 图片
  img {
    max-width: 100%;
    height: auto;
    border-radius: 4px;
  }

  // 高亮标记
  mark {
    background: #fff3cd;
    padding: 1px 4px;
    border-radius: 2px;
  }
}

// 业务卡片组件容器
.message-components {
  margin-top: 8px;
}

// 打字光标动画
.typing-cursor {
  display: inline;
  animation: blink 0.8s infinite;
  color: #1677ff;
  font-weight: 100;
}

@keyframes blink {
  0%, 50% {
    opacity: 1;
  }
  51%, 100% {
    opacity: 0;
  }
}

// 打字点动画（等待首字）
.typing-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 6px;
  border-radius: 50%;
  background: #b0b8c1;
  animation: typing-pulse 1.2s infinite ease-in-out;

  &:nth-child(2) {
    animation-delay: 0.15s;
  }

  &:nth-child(3) {
    margin-right: 0;
    animation-delay: 0.3s;
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

// 消息元信息
.message-meta {
  margin: 6px 0 0 20px;
  font-size: 12px;
  line-height: 18px;
  color: #9ca3af;
}

// 消息操作
.message-actions {
  display: flex;
  gap: 4px;
  padding: 0 4px;
  opacity: 0;
  transition: opacity 0.18s ease;

  .message-row:hover & {
    opacity: 1;
  }

  .ant-btn {
    color: #bbb;
    font-size: 13px;

    &:hover {
      color: #555;
    }
  }
}

// 错误横幅
.error-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 4px;

  .ant-alert {
    flex: 1;
  }
}

// ===================== 输入区域 =====================
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
  transition: border-color 0.18s ease, background-color 0.18s ease;

  &:focus-within {
    border-color: #d0d0d0;
    background: #fff;
  }
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
  font-size: 14px;
  line-height: 22px;
  overflow-y: hidden;

  &::placeholder {
    color: #8a8f98;
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.6;
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

// ===================== 响应式 =====================
@media (max-width: 768px) {
  .message-row {
    max-width: 95%;
  }

  .chat-messages {
    padding: 16px;
  }

  .chat-input-area {
    padding: 12px 16px;
  }

  .ai-chat-page {
    height: calc(100vh - 64px);
    margin: 0;
    border-radius: 0;
    box-shadow: none;
  }
}
</style>
