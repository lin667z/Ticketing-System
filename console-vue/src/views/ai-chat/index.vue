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
        <div class="chat-header-actions">
          <Button
            :type="showProcess ? 'primary' : 'default'"
            size="small"
            @click="showProcess = !showProcess"
          >
            {{ showProcess ? '隐藏过程' : '显示过程' }}
          </Button>
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

        <!-- 消息卡片列表（按类型渲染） -->
        <div
          v-for="(msg, index) in messages"
          :key="msg.id"
          :class="['message-row', msg.role]"
        >
          <!-- 阶段分隔标题：在用户消息后、或不同类型的AI阶段切换之间显示 -->
          <div
            v-if="index > 0 && shouldShowDivider(msg, messages[index - 1])"
            class="phase-header"
            :class="'phase-' + getDividerPhaseType(msg, messages[index - 1])"
          >
            <span class="phase-icon">
              <BulbOutlined v-if="getDividerPhaseType(msg, messages[index - 1]) === 'thinking'" />
              <ToolOutlined v-else-if="getDividerPhaseType(msg, messages[index - 1]) === 'tool'" />
              <MessageOutlined v-else-if="getDividerPhaseType(msg, messages[index - 1]) === 'reply'" />
              <IdcardOutlined v-else-if="getDividerPhaseType(msg, messages[index - 1]) === 'card'" />
              <RobotOutlined v-else />
            </span>
            <span class="phase-label">{{ getDividerLabel(msg, messages[index - 1]) }}</span>
          </div>

          <div class="message-body">
            <!-- ── 用户消息 ── -->
            <template v-if="msg.role === 'user'">
              <div class="message-bubble">
                <div class="message-text">{{ msg.content }}</div>
              </div>
              <div class="message-meta">
                <span>{{ msg.timestamp || formatTime(Date.now()) }}</span>
              </div>
              <div v-if="msg.content" class="message-actions">
                <Button type="text" size="small" @click="copyMessage(msg.content)">
                  <CopyOutlined />
                </Button>
              </div>
            </template>

            <!-- ── AI 卡片 ── -->
            <template v-else>
              <!-- 阶段流水线卡片 — 显眼的蓝色时间线 -->
              <div v-if="msg.cardType === 'stage'" class="card-stage-pipeline">
                <div class="stage-header">
                  <span class="stage-header-icon">
                    <LoadingOutlined v-if="msg.streaming" spin />
                    <CheckCircleOutlined v-else />
                  </span>
                  <span class="stage-header-title">{{ msg.streaming ? '正在处理' : '处理完成' }}</span>
                </div>
                <div class="stage-steps">
                  <div
                    v-for="(step, si) in (msg.stageSteps || [])"
                    :key="si"
                    :class="['stage-step', step.status]"
                  >
                    <span class="step-dot">
                      <LoadingOutlined v-if="step.status === 'active'" spin class="step-spin" />
                      <CheckCircleOutlined v-else-if="step.status === 'done'" class="step-check" />
                    </span>
                    <span class="step-label">{{ step.label }}</span>
                  </div>
                  <div v-if="(!msg.stageSteps || msg.stageSteps.length === 0) && msg.streaming" class="stage-step active">
                    <span class="step-dot"><LoadingOutlined spin class="step-spin" /></span>
                    <span class="step-label">处理中...</span>
                  </div>
                </div>
              </div>

              <!-- 工具执行卡片 — 显眼的琥珀色卡片 -->
              <div v-else-if="msg.cardType === 'tool'" class="card-tool" :class="{ done: msg.toolStatus === 'done' }">
                <div class="tool-card-header">
                  <div class="tool-card-header-left">
                    <LoadingOutlined v-if="msg.toolStatus !== 'done'" spin class="tool-spin" />
                    <CheckCircleOutlined v-else class="tool-done-icon" />
                    <span class="tool-agent-name">{{ msg.label || '工具执行' }}</span>
                  </div>
                  <span v-if="msg.toolStatus !== 'done'" class="tool-badge running">运行中</span>
                  <span v-else class="tool-badge done">完成</span>
                </div>
                <div v-if="msg.content" class="tool-card-body">{{ msg.content }}</div>
              </div>

              <!-- 推理过程卡片（可折叠）— eventType/reasoningDelta 绑定样式 -->
              <div v-else-if="msg.cardType === 'reasoning'" class="card-reasoning" :class="{ streaming: msg.streaming }">
                <div class="reasoning-header" @click="msg.collapsed = !msg.collapsed">
                  <RightOutlined v-if="msg.collapsed" class="reasoning-chevron" />
                  <DownOutlined v-else class="reasoning-chevron" />
                  <BulbOutlined class="reasoning-bulb" />
                  <span class="reasoning-label">{{ msg.label || '思考过程' }}</span>
                  <span v-if="msg.streaming" class="reasoning-badge live">推理中</span>
                  <span v-else class="reasoning-badge done">已折叠</span>
                </div>
                <div v-show="!msg.collapsed" class="reasoning-content">
                  {{ msg.content }}
                  <span v-if="msg.streaming" class="typing-cursor">|</span>
                </div>
              </div>

              <!-- 文本卡片（主回答）— eventType/delta/contentStyle 绑定样式 -->
              <!-- streaming 期间始终显示原始流式文本，结束后显示完整 Markdown -->
              <div v-else-if="msg.cardType === 'text'" :class="['message-bubble', msg.contentStyle ? 'msg-style-' + msg.contentStyle : '']">
                <div v-if="msg.streaming && msg.content" class="message-text streaming-raw">{{ msg.content }}<span class="typing-cursor">|</span></div>
                <div v-else-if="!msg.streaming && msg.renderedHtml" class="message-text markdown-body" v-html="msg.renderedHtml"></div>
                <div v-else-if="msg.content" class="message-text">{{ msg.content }}<span v-if="msg.streaming" class="typing-cursor">|</span></div>
              </div>
              <div v-if="msg.cardType === 'text' && msg.timestamp" class="message-meta">
                <span>{{ msg.timestamp }}</span>
              </div>
              <div v-if="msg.cardType === 'text' && msg.content && !msg.streaming" class="message-actions">
                <Button type="text" size="small" @click="copyMessage(msg.content)">
                  <CopyOutlined />
                </Button>
              </div>

              <!-- 车次卡片 -->
              <TrainCard v-else-if="msg.cardType === 'train_card'" :data="msg.data" />

              <!-- 订单卡片 -->
              <OrderCard v-else-if="msg.cardType === 'order_card'" :data="msg.data" />

              <!-- 兼容旧数据（无 cardType 的历史 AI 消息） -->
              <template v-else-if="!msg.cardType">
                <div class="message-bubble">
                  <div v-if="msg.reasoningContent" class="card-reasoning">
                    <div class="reasoning-header" @click="msg.reasoningCollapsed = !msg.reasoningCollapsed">
                      <RightOutlined v-if="msg.reasoningCollapsed" class="reasoning-chevron" />
                      <DownOutlined v-else class="reasoning-chevron" />
                      <BulbOutlined class="reasoning-bulb" />
                      <span class="reasoning-label">思考过程</span>
                    </div>
                    <div v-show="!msg.reasoningCollapsed" class="reasoning-content">{{ msg.reasoningContent }}</div>
                  </div>
                  <div v-if="msg.renderedHtml" class="message-text markdown-body" v-html="msg.renderedHtml"></div>
                  <div v-else class="message-text">{{ msg.content }}</div>
                </div>
                <div v-if="msg.timestamp" class="message-meta">
                  <span>{{ msg.timestamp }}</span>
                </div>
              </template>
            </template>
          </div>
        </div>

        <!-- 流式加载等待动画 -->
        <div v-if="isStreaming && !hasStreamingCards()" class="waiting-row">
          <div class="waiting-indicator">
            <span class="typing-dot"></span>
            <span class="typing-dot"></span>
            <span class="typing-dot"></span>
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
  RightOutlined,
  BulbOutlined,
  ToolOutlined,
  MessageOutlined,
  IdcardOutlined
} from '@ant-design/icons-vue'
import {
  fetchAiChatStream,
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

const STAGE_DISPLAY_MAP = {
  'AI Agent': '正在处理',
  'Session context': '正在加载会话上下文',
  'User profile': '正在加载用户信息',
  'ROUTER': '正在分析需求',
  'Master Agent': '正在制定执行计划',
  'Agent Dispatcher': '正在分配任务',
  '正在整理结果': '正在整合结果',
  '任务调度': '正在分配任务',
  'Ticket Info Agent': '正在查询车票信息',
  'Order Query Agent': '正在查询订单信息',
  'TICKET_INFO': '正在查询车票信息',
  'ORDER_QUERY': '正在查询订单信息',
  'GENERAL_CHAT': '正在处理',
  'AGGREGATOR': '正在整合结果'
}

const resolveStageLabel = (traceLabel) => {
  if (!traceLabel) return '处理中'
  if (STAGE_DISPLAY_MAP[traceLabel]) return STAGE_DISPLAY_MAP[traceLabel]
  const agentType = traceLabel.replace(/\s+Agent$/, '')
  if (STAGE_DISPLAY_MAP[agentType]) return STAGE_DISPLAY_MAP[agentType]
  return traceLabel
}

const PHASE_TYPE = {
  DEFAULT: 'default',
  THINKING: 'thinking',
  TOOL: 'tool',
  REPLY: 'reply',
  CARD: 'card'
}

const PHASE_DEFS = {
  [PHASE_TYPE.DEFAULT]: { label: '铁宝', color: '#1677ff' },
  [PHASE_TYPE.THINKING]: { label: '正在思考', color: '#1677ff' },
  [PHASE_TYPE.TOOL]: { label: '工具执行', color: '#fa8c16' },
  [PHASE_TYPE.REPLY]: { label: '开始回复', color: '#52c41a' },
  [PHASE_TYPE.CARD]: { label: '查询结果', color: '#13c2c2' }
}

// ===================== 响应式状态 =====================

const sidebarCollapsed = ref(false)
const conversations = ref([])
const conversationsLoading = ref(false)
const conversationsLoaded = ref(false)
const currentConversationId = ref(null)
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
const showProcess = ref(true)

let streamRenderTimer = null
const STREAM_RENDER_INTERVAL = 80

const PROCESS_EVENT_TYPES = ['STAGE', 'TRACE', 'TOOL_START', 'TOOL_END', 'RETRYING']

// ===================== 卡片管理 =====================

let activeCards = null

const resetActiveCards = () => {
  activeCards = {
    stage: null,
    reasoning: null,
    text: null,
    tool: null
  }
}

const hasStreamingCards = () => {
  return messages.value.some(m => m.role === 'ai' && m.streaming)
}

const createCard = (cardType, init = {}) => {
  const card = {
    id: Date.now() + Math.random(),
    role: 'ai',
    cardType,
    content: init.content || '',
    data: init.data || null,
    streaming: true,
    timestamp: '',
    collapsed: init.collapsed !== undefined ? init.collapsed : false,
    label: init.label || '',
    renderedHtml: '',
    contentStyle: init.contentStyle || null,
    stageSteps: cardType === 'stage' ? [] : undefined,
    toolStatus: cardType === 'tool' ? 'running' : undefined
  }
  messages.value.push(card)
  if (activeCards) {
    activeCards[cardType] = card
  }
  return card
}

const getOrCreateCard = (cardType, init = {}) => {
  if (activeCards && activeCards[cardType]) {
    return activeCards[cardType]
  }
  return createCard(cardType, init)
}

const finalizeCard = (cardType) => {
  if (!activeCards) return
  const card = activeCards[cardType]
  if (card) {
    card.streaming = false
    if (cardType === 'stage' && card.stageSteps && card.stageSteps.length > 0) {
      const lastStep = card.stageSteps[card.stageSteps.length - 1]
      if (lastStep && lastStep.status === 'active') {
        lastStep.status = 'done'
      }
    }
    if (cardType === 'reasoning') {
      card.collapsed = true
    }
    if (cardType === 'tool') {
      card.toolStatus = 'done'
    }
    if (cardType === 'text' && card.content) {
      const { html } = renderMarkdown(card.content)
      card.renderedHtml = html
    }
  }
  activeCards[cardType] = null
}

const finalizeAllCards = () => {
  if (!activeCards) return
  for (const type of Object.keys(activeCards)) {
    finalizeCard(type)
  }
}

const renderCardMarkdown = (card) => {
  if (!card || !card.content) return
  const safeContent = preprocessStreamingContent(card.content)
  const { html } = renderMarkdown(safeContent || card.content)
  card.renderedHtml = html
}

const scheduleCardRender = (card) => {
  if (streamRenderTimer) {
    clearTimeout(streamRenderTimer)
  }
  streamRenderTimer = setTimeout(() => {
    streamRenderTimer = null
    renderCardMarkdown(card)
  }, STREAM_RENDER_INTERVAL)
}

const addStageStep = (label) => {
  if (!label) return
  const card = getOrCreateCard('stage')
  if (!card.stageSteps) {
    card.stageSteps = []
  }
  const lastStep = card.stageSteps[card.stageSteps.length - 1]
  if (lastStep && lastStep.label === label) {
    return
  }
  if (lastStep && lastStep.status === 'active') {
    lastStep.status = 'done'
  }
  card.stageSteps.push({ label, status: 'active' })
}

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
    conversationsLoaded.value = true
  } catch (err) {
    console.error('加载对话列表失败:', err)
    conversationsLoaded.value = true
  } finally {
    conversationsLoading.value = false
  }
}

const handleCreateConversation = () => {
  if (isStreaming.value) {
    cancelStream()
  }
  currentConversationId.value = null
  messages.value = []
  tokenUsage.value = null
  errorMessage.value = ''
  lastUserMessage.value = ''
  resetActiveCards()
  inputRef.value?.focus()
}

const handleSelectConversation = async (conv) => {
  const convId = conv.id
  if (convId === currentConversationId.value) return
  if (isStreaming.value) {
    cancelStream()
  }
  currentConversationId.value = convId
  messages.value = []
  tokenUsage.value = null
  errorMessage.value = ''
  lastUserMessage.value = ''
  try {
    const res = await getConversationDetail(convId)
    if (res.success && res.data) {
      const detail = res.data
      const messageList = detail.messages || []
      const historyMessages = []
      messageList.forEach((msg) => {
        const msgContent = msg.content || ''
        const role = mapRole(msg.role)
        const { html } = renderMarkdown(msgContent)
        if (role === 'user') {
          historyMessages.push({
            id: msg.id || Date.now() + Math.random(),
            role: 'user',
            content: msgContent,
            timestamp: formatTime(msg.createTime)
          })
        } else {
          if (msg.reasoningContent) {
            historyMessages.push({
              id: (msg.id || Date.now()) + Math.random(),
              role: 'ai',
              cardType: 'reasoning',
              content: msg.reasoningContent || '',
              streaming: false,
              timestamp: '',
              collapsed: true,
              label: '',
              renderedHtml: ''
            })
          }
          historyMessages.push({
            id: (msg.id || Date.now()) + Math.random() + 1,
            role: 'ai',
            cardType: 'text',
            content: msgContent,
            data: null,
            streaming: false,
            timestamp: formatTime(msg.createTime),
            collapsed: false,
            label: '',
            renderedHtml: html
          })
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

const mapRole = (role) => {
  if (role === 'assistant' || role === 'ai') return 'ai'
  if (role === 'user') return 'user'
  return role
}

const handleRenameConversation = async ({ id, newName }) => {
  try {
    const res = await renameConversation(id, newName)
    if (res.success) {
      message.success('重命名成功')
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

const handleDeleteConversation = async (conv) => {
  try {
    const res = await deleteConversation(conv.id)
    if (res.success) {
      message.success('删除成功')
      conversations.value = conversations.value.filter((c) => c.id !== conv.id)
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

const sendQuickPrompt = (prompt) => {
  inputMessage.value = prompt
  sendMessage()
}

const sendMessage = async () => {
  const text = inputMessage.value.trim()
  if (!text || isStreaming.value) return

  lastUserMessage.value = text

  const userMsg = {
    id: Date.now(),
    role: 'user',
    content: text,
    timestamp: formatTime(Date.now())
  }
  messages.value.push(userMsg)

  resetActiveCards()

  inputMessage.value = ''
  isStreaming.value = true
  errorMessage.value = ''
  tokenUsage.value = null
  shouldAutoScroll.value = true

  if (inputRef.value) {
    inputRef.value.style.height = 'auto'
  }

  await nextTick()
  scrollToBottom()

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
          handleChunk(chunk)
        }
      }
    )

    if (streamRenderTimer) {
      clearTimeout(streamRenderTimer)
      streamRenderTimer = null
    }
    finalizeAllCards()

    if (!messages.value.some(m => m.role === 'ai')) {
      createCard('text', {
        content: '暂时没有收到回复，请稍后再试。',
        streaming: false,
        timestamp: formatTime(Date.now())
      })
      const textCard = activeCards?.text
      if (textCard) renderCardMarkdown(textCard)
    }

    loadConversations()
  } catch (err) {
    if (err.name !== 'AbortError') {
      const errMsg = err.message || '服务暂时不可用，请稍后再试。'
      const card = createCard('text', {
        content: errMsg,
        streaming: false,
        timestamp: formatTime(Date.now())
      })
      renderCardMarkdown(card)

      if (errMsg.includes('429') || errMsg.includes('限流') || errMsg.includes('rate limit')) {
        errorMessage.value = '请求过于频繁，请稍后再试。'
      } else if (errMsg.includes('500') || errMsg.includes('服务器')) {
        errorMessage.value = '服务器繁忙，请稍后再试。'
      } else if (errMsg.includes('网络') || errMsg.includes('fetch')) {
        errorMessage.value = '网络连接异常，请检查网络后重试。'
      }
    }
  } finally {
    isStreaming.value = false
    streamController.value = null
    resetActiveCards()
    await nextTick()
    scrollToBottom()
  }
}

// ===================== 卡片式 SSE 数据块处理 =====================
//
// 每条 SSE 消息依据 eventType 做分类拆分，四种字段类别绑定不同渲染：
//   eventType + reasoningDelta      → reasoning 卡片（灰色可折叠推理框）
//   eventType + delta + contentStyle → text 卡片（Markdown 气泡 + 差异化 CSS）
//   eventType + traceLabel + delta   → tool / stage 卡片（工具执行 / 流水线）
//   eventType + componentData        → train_card / order_card（结构化数据卡片）
// 不同类型内容不可混在同一文本区块

const handleChunk = (chunk) => {
  console.log('[AIChunk] eventType:', chunk.eventType, 'delta:', chunk.delta?.substring(0, 60), 'reasoningDelta:', chunk.reasoningDelta?.substring(0, 60), 'traceLabel:', chunk.traceLabel, 'traceType:', chunk.traceType, 'contentStyle:', chunk.contentStyle)

  if (chunk.sessionId && !currentConversationId.value) {
    currentConversationId.value = chunk.sessionId
  }

  const eventType = chunk.eventType

  if (!showProcess.value && PROCESS_EVENT_TYPES.includes(eventType)) {
    return
  }

  // ── STAGE：eventType + traceLabel + delta（阶段流水线卡片）──
  if (eventType === 'STAGE') {
    const displayLabel = resolveStageLabel(chunk.traceLabel) || chunk.delta || '处理中'
    addStageStep(displayLabel)
    if (shouldAutoScroll.value) nextTick(() => scrollToBottom())
    return
  }

  // ── TRACE：分流 reasoningDelta 和 status delta ──
  if (eventType === 'TRACE') {
    if (chunk.reasoningDelta) {
      const card = getOrCreateCard('reasoning', { collapsed: false })
      card.content += chunk.reasoningDelta
      if (chunk.traceLabel && !card.label) {
        card.label = resolveStageLabel(chunk.traceLabel) || chunk.traceLabel
      }
    }
    if (chunk.traceType === 'STATUS' && chunk.delta) {
      const card = getOrCreateCard('tool')
      card.content = chunk.delta
      card.toolStatus = 'running'
      if (chunk.traceLabel && !card.label) {
        card.label = resolveStageLabel(chunk.traceLabel) || chunk.traceLabel
      }
    }
    if (shouldAutoScroll.value) nextTick(() => scrollToBottom())
    return
  }

  // ── TOOL_START / TOOL_END：eventType + delta + traceLabel（工具执行卡片）──
  if (eventType === 'TOOL_START') {
    const agentLabel = chunk.traceLabel || chunk.agentType || '工具调用'
    const card = getOrCreateCard('tool', { label: agentLabel })
    card.content = chunk.delta || '正在执行...'
    card.toolStatus = 'running'
    if (shouldAutoScroll.value) nextTick(() => scrollToBottom())
    return
  }

  if (eventType === 'TOOL_END') {
    const card = getOrCreateCard('tool')
    card.toolStatus = 'done'
    if (chunk.delta) {
      card.content = chunk.delta
    }
    if (shouldAutoScroll.value) nextTick(() => scrollToBottom())
    return
  }

  // ── RETRYING：eventType + delta（工具卡片显示重试消息）──
  if (eventType === 'RETRYING') {
    const card = getOrCreateCard('tool')
    card.content = chunk.delta || '当前线路繁忙，正在切换备用节点...'
    card.toolStatus = 'running'
    if (shouldAutoScroll.value) nextTick(() => scrollToBottom())
    return
  }

  // ── COMPONENT：eventType + componentData（结构化数据卡片）──
  if (eventType === 'COMPONENT') {
    if (chunk.componentType && chunk.componentData) {
      const cType = chunk.componentType === 'train_card' ? 'train_card'
        : chunk.componentType === 'order_card' ? 'order_card'
        : chunk.componentType
      createCard(cType, {
        data: chunk.componentData,
        streaming: chunk.status === 'loading'
      })
    } else if (chunk.delta) {
      try {
        const compData = typeof chunk.delta === 'string' ? JSON.parse(chunk.delta) : chunk.delta
        if (compData.type && compData.data) {
          const cType = compData.type === 'train_card' ? 'train_card'
            : compData.type === 'order_card' ? 'order_card'
            : compData.type
          createCard(cType, { data: compData.data })
        }
      } catch (e) { /* ignore */ }
    }
    if (shouldAutoScroll.value) nextTick(() => scrollToBottom())
    return
  }

  // ── ERROR：eventType + error（一次性错误气泡）──
  if (eventType === 'ERROR' || chunk.error) {
    const errText = chunk.error || chunk.answer || '服务暂时不可用，请稍后再试。'
    if (errText.includes('会话不存在') || errText.includes('无权访问')) {
      message.error('该对话已失效，已自动移除')
      conversations.value = conversations.value.filter((c) => c.id === currentConversationId.value)
      currentConversationId.value = null
      messages.value = messages.value.filter((m) => m.role !== 'ai')
      return
    }
    createCard('text', {
      content: errText,
      streaming: false,
      timestamp: formatTime(Date.now()),
      contentStyle: 'error'
    })
    const textCard = activeCards?.text
    if (textCard) renderCardMarkdown(textCard)
    if (shouldAutoScroll.value) nextTick(() => scrollToBottom())
    return
  }

  // ── CHAT_CHUNK / ASK_USER：严格区分推理和文本内容 ──
  // reasoningDelta → 推理卡片，delta + contentStyle → 文本气泡
  if (eventType === 'CHAT_CHUNK' || eventType === 'ASK_USER') {
    if (chunk.reasoningDelta) {
      const rCard = getOrCreateCard('reasoning', { collapsed: false })
      rCard.content += chunk.reasoningDelta
      if (chunk.traceLabel && !rCard.label) {
        rCard.label = resolveStageLabel(chunk.traceLabel) || chunk.traceLabel
      }
    }
    if (chunk.delta) {
      const card = getOrCreateCard('text')
      card.content += chunk.delta
      if (chunk.contentStyle && !card.contentStyle) {
        card.contentStyle = chunk.contentStyle
      }
      scheduleCardRender(card)
    }
    if (shouldAutoScroll.value) nextTick(() => scrollToBottom())
    return
  }

  // ── DONE：结束所有流式卡片 ──
  if (eventType === 'DONE' || chunk.done) {
    if (streamRenderTimer) {
      clearTimeout(streamRenderTimer)
      streamRenderTimer = null
    }
    if (chunk.answer && !(activeCards?.text?.content)) {
      const card = getOrCreateCard('text', { content: chunk.answer })
      renderCardMarkdown(card)
    }
    finalizeAllCards()
    if (chunk.usage) {
      tokenUsage.value = chunk.usage
    }
    if (shouldAutoScroll.value) nextTick(() => scrollToBottom())
    return
  }
}

// ===================== 滚动控制 =====================

const scrollToBottom = () => {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

const onScroll = () => {
  if (!messagesContainer.value) return
  const { scrollTop, scrollHeight, clientHeight } = messagesContainer.value
  shouldAutoScroll.value = scrollHeight - scrollTop - clientHeight < 60
}

// ===================== 输入框 =====================

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

const newline = () => {
  autoResize()
}

// ===================== 其他功能 =====================

const cancelStream = () => {
  if (streamController.value) {
    streamController.value.abort()
    streamController.value = null
  }
  isStreaming.value = false
  const streamingCards = messages.value.filter(m => m.role === 'ai' && m.streaming)
  if (streamingCards.length > 0) {
    streamingCards.forEach(card => {
      card.streaming = false
      if (card.cardType === 'tool') {
        card.toolStatus = 'done'
      }
      if (card.cardType === 'text' && card.content && !card.renderedHtml) {
        renderCardMarkdown(card)
      }
    })
  } else {
    const card = createCard('text', {
      content: '已取消发送。',
      streaming: false,
      timestamp: formatTime(Date.now())
    })
    renderCardMarkdown(card)
  }
  resetActiveCards()
}

const copyMessage = async (content) => {
  try {
    await navigator.clipboard.writeText(content)
    message.success('已复制到剪贴板')
  } catch (err) {
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

const retryLastMessage = () => {
  if (!lastUserMessage.value) return
  errorMessage.value = ''
  resetActiveCards()
  while (messages.value.length > 0 && messages.value[messages.value.length - 1].role === 'ai') {
    messages.value.pop()
  }
  inputMessage.value = lastUserMessage.value
  sendMessage()
}

// ===================== 阶段分隔线逻辑 =====================

const detectPhaseType = (current, previous) => {
  if (previous.role === 'user') return PHASE_TYPE.DEFAULT
  if (current.role === 'user') return null

  if (previous.cardType === 'stage' && (current.cardType === 'tool' || current.cardType === 'reasoning')) {
    return PHASE_TYPE.THINKING
  }
  if ((previous.cardType === 'tool' || previous.cardType === 'stage') && current.cardType === 'reasoning') {
    return PHASE_TYPE.THINKING
  }
  if ((previous.cardType === 'tool' || previous.cardType === 'reasoning') && current.cardType === 'text') {
    return PHASE_TYPE.REPLY
  }
  if (previous.cardType === 'text' && (current.cardType === 'train_card' || current.cardType === 'order_card')) {
    return PHASE_TYPE.CARD
  }
  if (previous.cardType === 'reasoning' && current.cardType === 'tool') {
    return PHASE_TYPE.TOOL
  }

  // 任何 AI 卡片在文本之前，显示阶段转换
  if (current.cardType === 'text' && previous.role === 'ai') {
    if (previous.cardType === 'stage') return PHASE_TYPE.REPLY
    return PHASE_TYPE.DEFAULT
  }

  // stage 之前如果有 AI 消息，显示默认
  if (current.cardType === 'stage' && previous.role === 'ai') {
    return PHASE_TYPE.DEFAULT
  }

  return null
}

const shouldShowDivider = (current, previous) => {
  if (previous.role === 'user') return true
  if (current.role === 'user') return previous.role === 'ai'
  if (previous.role === 'ai' && current.role === 'ai') {
    const type = detectPhaseType(current, previous)
    if (type) return true
  }
  return false
}

const getDividerPhaseType = (current, previous) => {
  return detectPhaseType(current, previous) || 'default'
}

const getDividerLabel = (current, previous) => {
  const phaseType = detectPhaseType(current, previous)
  if (phaseType && PHASE_DEFS[phaseType]) {
    return PHASE_DEFS[phaseType].label
  }
  return ''
}

// ===================== 工具函数 =====================

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
.ai-chat-page {
  display: flex;
  height: calc(100vh - 64px - 42px);
  background: #fff;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.035);
}

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

.chat-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
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

// ===================== 阶段分隔标题 =====================
.phase-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 18px 0 8px;
  padding: 10px 16px;
  border-left: 4px solid #1677ff;
  background: linear-gradient(135deg, #f0f7ff 0%, #f5f9ff 100%);
  border-radius: 0 10px 10px 0;
  box-shadow: 0 2px 8px rgba(22, 119, 255, 0.1);
  position: relative;

  &::before {
    content: '';
    position: absolute;
    left: -4px;
    top: -14px;
    bottom: auto;
    width: 0;
    height: 0;
  }

  &.phase-tool {
    border-left-color: #fa8c16;
    background: linear-gradient(135deg, #fffaf0 0%, #fff7e6 100%);
    box-shadow: 0 2px 8px rgba(250, 140, 22, 0.1);
  }

  &.phase-reply {
    border-left-color: #52c41a;
    background: linear-gradient(135deg, #f6ffed 0%, #f0faea 100%);
    box-shadow: 0 2px 8px rgba(82, 196, 26, 0.1);
  }

  &.phase-card {
    border-left-color: #13c2c2;
    background: linear-gradient(135deg, #e6fffb 0%, #f0fcfa 100%);
    box-shadow: 0 2px 8px rgba(19, 194, 194, 0.1);
  }
}

.phase-icon {
  display: inline-flex;
  align-items: center;
  font-size: 18px;
  flex-shrink: 0;
  color: #1677ff;

  .phase-tool & { color: #fa8c16; }
  .phase-reply & { color: #52c41a; }
  .phase-card & { color: #13c2c2; }
}

.phase-label {
  font-size: 14px;
  font-weight: 700;
  color: #1a3352;
  letter-spacing: 0.3px;

  .phase-tool & { color: #5c3a00; }
  .phase-reply & { color: #1f3a12; }
  .phase-card & { color: #00474f; }
}

.message-body {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.message-bubble {
  padding: 14px 20px;
  font-size: 15px;
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);

  .ai & {
    color: #1f2328;
    background: linear-gradient(135deg, #ffffff 0%, #f8f9fa 100%);
    border: 1px solid #e8e8e8;
    border-left: 3px solid #1677ff;
    border-radius: 0 16px 16px 16px;
  }

  .user & {
    color: #fff;
    background: linear-gradient(135deg, #1677ff 0%, #0958d9 100%);
    border-radius: 16px 0 16px 16px;
    font-weight: 500;
    box-shadow: 0 2px 6px rgba(22, 119, 255, 0.25);
  }
}

.ai .msg-style-greeting {
  background: linear-gradient(135deg, #e6f4ff 0%, #f0f5ff 100%);
  border: 1px solid #bae0ff;
  font-size: 15px;
  color: #1a3352;
}

.ai .msg-style-clarification {
  background: #fffbe6;
  border: 1px solid #ffe58f;
  border-left: 3px solid #faad14;
  color: #5c4a00;
}

.ai .msg-style-summary {
  background: #f6ffed;
  border: 1px solid #b7eb8f;
  color: #1f3a12;
}

.ai .msg-style-suggestion {
  background: #f0f5ff;
  border: 1px solid #adc6ff;
  border-left: 3px solid #597ef7;
  color: #1a2852;
  font-size: 14px;
}

.ai .msg-style-info {
  background: #e6f7ff;
  border: 1px solid #91d5ff;
  color: #1a3a52;
}

.ai .msg-style-success {
  background: #f6ffed;
  border: 1px solid #95de64;
  border-left: 3px solid #52c41a;
  color: #1f3a12;
}

.ai .msg-style-warning {
  background: #fff7e6;
  border: 1px solid #ffd591;
  border-left: 3px solid #fa8c16;
  color: #5c3a00;
}

.ai .msg-style-error {
  background: #fff2f0;
  border: 1px solid #ffccc7;
  border-left: 3px solid #ff4d4f;
  color: #5c0011;
}

// ===================== 阶段流水线卡片 =====================
.card-stage-pipeline {
  margin: 4px 0;
  background: linear-gradient(135deg, #f0f5ff 0%, #f5f9ff 100%);
  border: 1px solid #d6e4ff;
  border-left: 4px solid #1677ff;
  border-radius: 0 10px 10px 0;
  padding: 12px 16px;
  box-shadow: 0 1px 4px rgba(22, 119, 255, 0.06);
}

.stage-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px dashed #d6e4ff;
}

.stage-header-icon {
  font-size: 15px;
  color: #1677ff;
}

.stage-header-title {
  font-size: 13px;
  font-weight: 600;
  color: #1677ff;
}

.stage-steps {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stage-step {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 5px 0 5px 4px;
  font-size: 12px;
  line-height: 18px;
  color: #8c8c8c;
  transition: color 0.25s ease;

  &.active {
    color: #1677ff;
    font-weight: 600;
  }

  &.done {
    color: #52c41a;
  }

  .step-dot {
    width: 20px;
    height: 20px;
    border-radius: 50%;
    flex-shrink: 0;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: #e8e8e8;
    transition: background 0.25s ease, box-shadow 0.25s ease;
  }

  &.active .step-dot {
    background: #1677ff;
    box-shadow: 0 0 0 3px rgba(22, 119, 255, 0.2);
    animation: pulse-dot 1.6s infinite;
  }

  &.done .step-dot {
    background: #52c41a;
  }

  .step-spin {
    font-size: 12px;
    color: #fff;
  }

  .step-check {
    font-size: 12px;
    color: #fff;
  }
}

@keyframes pulse-dot {
  0%, 100% {
    box-shadow: 0 0 0 3px rgba(22, 119, 255, 0.2);
  }
  50% {
    box-shadow: 0 0 0 7px rgba(22, 119, 255, 0.05);
  }
}

// ===================== 工具执行卡片 =====================
.card-tool {
  margin: 4px 0;
  background: linear-gradient(135deg, #fffdf0 0%, #fffbe6 100%);
  border: 1px solid #ffe58f;
  border-left: 4px solid #fa8c16;
  border-radius: 0 10px 10px 0;
  padding: 0;
  box-shadow: 0 1px 4px rgba(250, 140, 22, 0.06);
  overflow: hidden;

  &.done {
    background: linear-gradient(135deg, #f6ffed 0%, #f0faea 100%);
    border-color: #b7eb8f;
    border-left-color: #52c41a;
    box-shadow: 0 1px 4px rgba(82, 196, 26, 0.06);
  }
}

.tool-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: rgba(250, 140, 22, 0.05);

  .card-tool.done & {
    background: rgba(82, 196, 26, 0.05);
  }
}

.tool-card-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.tool-spin {
  font-size: 14px;
  color: #fa8c16;
}

.tool-done-icon {
  font-size: 14px;
  color: #52c41a;
}

.tool-agent-name {
  font-weight: 600;
  color: #5c3a00;

  .card-tool.done & {
    color: #3f6600;
  }
}

.tool-badge {
  padding: 2px 10px;
  font-size: 11px;
  font-weight: 600;
  border-radius: 12px;
  flex-shrink: 0;

  &.running {
    color: #fff;
    background: #fa8c16;
    animation: badge-pulse 1.5s infinite;
  }

  &.done {
    color: #fff;
    background: #52c41a;
  }
}

@keyframes badge-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}

.tool-card-body {
  padding: 10px 16px 12px;
  font-size: 12px;
  line-height: 1.6;
  color: #8b6914;
  background: rgba(250, 173, 20, 0.04);
  white-space: pre-wrap;
  word-break: break-word;

  .card-tool.done & {
    color: #5b7a2e;
    background: rgba(82, 196, 26, 0.03);
  }
}

.waiting-row {
  align-self: flex-start;
  padding: 8px 0;
}

.waiting-indicator {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 10px 18px;
}

.card-reasoning {
  margin: 4px 0;
  background: linear-gradient(135deg, #fafbfc 0%, #f5f7fa 100%);
  border: 1px solid #d9d9d9;
  border-left: 4px solid #8b9199;
  border-radius: 0 10px 10px 0;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.reasoning-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 9px 14px;
  cursor: pointer;
  user-select: none;
  font-size: 12px;
  font-weight: 500;
  color: #8b9199;
  background: rgba(0, 0, 0, 0.02);
  transition: background-color 0.15s ease;

  &:hover {
    background: rgba(0, 0, 0, 0.05);
  }

  .reasoning-chevron {
    font-size: 10px;
    color: #999;
  }

  .reasoning-bulb {
    font-size: 13px;
    color: #8b9199;
    margin-left: 2px;
  }
}

.reasoning-badge {
  margin-left: auto;
  padding: 1px 8px;
  font-size: 10px;
  font-weight: 600;
  border-radius: 10px;
  flex-shrink: 0;

  &.live {
    color: #1677ff;
    background: #e6f4ff;
    animation: badge-pulse 1.5s infinite;
  }

  &.done {
    color: #8c8c8c;
    background: #f0f0f0;
  }
}

.reasoning-content {
  padding: 10px 14px 12px 28px;
  border-top: 1px solid #e8e8e8;
  color: #6b7280;
  font-size: 13px;
  line-height: 1.7;
  font-style: italic;
  white-space: pre-wrap;
  word-break: break-word;
  background: rgba(255, 255, 255, 0.5);
}

.message-text {
  white-space: pre-wrap;
  word-break: break-word;
}

.streaming-raw {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 14px;
  line-height: 1.7;
  color: #374151;
  background: rgba(22, 119, 255, 0.02);
  padding: 4px 0;
}

.markdown-body {
  word-break: break-word;
  line-height: 1.7;
  min-height: 0;

  p {
    margin: 0 0 8px 0;
    &:last-child { margin-bottom: 0; }
  }

  strong { font-weight: 600; color: #1a1a1a; }
  em { font-style: italic; }

  s, del { text-decoration: line-through; color: #999; }

  a { color: #1677ff; text-decoration: none;
    &:hover { text-decoration: underline; }
  }

  h1, h2, h3, h4, h5, h6 {
    margin: 12px 0 6px 0;
    font-weight: 600;
    line-height: 1.4;
    color: #1a1a1a;
    &:first-child { margin-top: 0; }
  }

  h1 { font-size: 1.4em; }
  h2 { font-size: 1.25em; }
  h3 { font-size: 1.1em; }
  h4 { font-size: 1.05em; }

  ul, ol {
    margin: 4px 0;
    padding-left: 20px;
    li { margin-bottom: 2px; }
  }

  ul { list-style: disc;
    ul { list-style: circle; }
  }

  ol { list-style: decimal; }

  blockquote {
    margin: 8px 0;
    padding: 6px 12px;
    border-left: 3px solid #e0e0e0;
    background: #f9fafb;
    color: #666;
    font-size: 13px;
    p { margin: 0; }
  }

  code {
    padding: 2px 6px;
    background: #f0f2f5;
    border-radius: 4px;
    font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
    font-size: 0.9em;
    color: #d63384;
  }

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

  hr { border: 0; border-top: 1px solid #eee; margin: 12px 0; }

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
      &:nth-child(even) { background: #fafbfc; }
      &:hover { background: #f0f5ff; }
    }
    td { padding: 7px 12px; color: #555; border-bottom: 1px solid #f0f0f0; }
  }

  th, td {
    text-align: left;
    &:first-child { padding-left: 14px; }
    &:last-child { padding-right: 14px; }
  }

  img { max-width: 100%; height: auto; border-radius: 4px; }

  mark { background: #fff3cd; padding: 1px 4px; border-radius: 2px; }
}

.typing-cursor {
  display: inline;
  animation: blink 0.8s infinite;
  color: #1677ff;
  font-weight: 100;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

.typing-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 6px;
  border-radius: 50%;
  background: #b0b8c1;
  animation: typing-pulse 1.2s infinite ease-in-out;

  &:nth-child(2) { animation-delay: 0.15s; }
  &:nth-child(3) { margin-right: 0; animation-delay: 0.3s; }
}

@keyframes typing-pulse {
  0%, 80%, 100% { opacity: 0.35; transform: translateY(0); }
  40% { opacity: 1; transform: translateY(-2px); }
}

.message-meta {
  margin: 6px 0 0 20px;
  font-size: 12px;
  line-height: 18px;
  color: #9ca3af;
}

.message-actions {
  display: flex;
  gap: 4px;
  padding: 0 4px;
  opacity: 0;
  transition: opacity 0.18s ease;

  .message-row:hover & { opacity: 1; }

  .ant-btn {
    color: #bbb;
    font-size: 13px;
    &:hover { color: #555; }
  }
}

.error-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 4px;
  .ant-alert { flex: 1; }
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

  &::placeholder { color: #8a8f98; }
  &:disabled { cursor: not-allowed; opacity: 0.6; }
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

  &:disabled { cursor: not-allowed; opacity: 0.35; }
}

@media (max-width: 768px) {
  .message-row { max-width: 95%; }
  .chat-messages { padding: 16px; }
  .chat-input-area { padding: 12px 16px; }
  .ai-chat-page {
    height: calc(100vh - 64px);
    margin: 0;
    border-radius: 0;
    box-shadow: none;
  }
}
</style>
