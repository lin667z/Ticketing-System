// ===================== Enum Types =====================

export type AiStreamEventType =
  | 'CHAT_CHUNK'
  | 'ASK_USER'
  | 'ERROR'
  | 'DONE'
  | 'RETRYING'
  | 'TRACE'
  | 'TOOL_START'
  | 'TOOL_END'
  | 'STAGE'
  | 'COMPONENT'

export type AiMessageType = 'SYSTEM' | 'USER' | 'ASSISTANT' | 'TOOL'

export type ContentStyle =
  | 'greeting'
  | 'normal'
  | 'clarification'
  | 'info'
  | 'warning'
  | 'success'
  | 'error'
  | 'summary'
  | 'suggestion'

export type TraceType = 'STATUS' | 'REASONING' | 'RESULT' | 'CONTENT'

// ===================== Event-Type → UI Card Mapping =====================
//
// 每条 SSE 消息依据 eventType 做分类拆分，不同内容类型不可混在同一文本区块
//
// | eventType   | 主内容字段     | UI 卡片类型   | 渲染风格               |
// |-------------|---------------|--------------|----------------------|
// | STAGE       | traceLabel + delta | stage    | 蓝色流水线时间线          |
// | TRACE       | reasoningDelta    | reasoning  | 灰色可折叠推理框         |
// | TRACE       | delta (STATUS)    | tool       | 琥珀色工具执行卡片        |
// | TOOL_START  | delta + traceLabel| tool       | 琥珀色（运行中）          |
// | TOOL_END    | delta + traceLabel| tool       | 绿色（完成）             |
// | CHAT_CHUNK  | delta             | text       | Markdown 气泡 + contentStyle |
// | CHAT_CHUNK  | reasoningDelta    | reasoning  | 灰色可折叠推理框         |
// | COMPONENT   | componentData     | train_card/order_card | 结构化卡片    |
// | ERROR       | error             | text       | 错误样式气泡（一次性）     |
// | DONE        | answer, usage     | -          | 结束所有卡片             |
// | RETRYING    | delta             | tool       | 琥珀色（重试中）          |

// ===================== Raw SSE Chunk (from backend) =====================

export interface AiStreamChunk {
  /** 文本增量 — CHAT_CHUNK 中含 delta 时为主要文本内容 */
  delta: string | null
  /** 推理增量 — CHAT_CHUNK 含 reasoningDelta 或 TRACE(RESULT/REASONING) 时为推理内容 */
  reasoningDelta: string | null
  answer: string | null
  messageType: AiMessageType
  /** 事件类型，决定前端 UI 卡片分类与渲染方式 */
  eventType: AiStreamEventType
  sessionId: number | null
  modelName: string | null
  finishReason: string | null
  usage: Record<string, unknown> | null
  done: boolean
  error: string | null
  contentType: 'text' | 'trace' | 'component' | null
  componentType: string | null
  componentData: unknown | null
  traceStage: string | null
  traceType: TraceType | null
  agentType: string | null
  /** 追踪标签，用于 STAGE/TOOL 卡片显示 */
  traceLabel: string | null
  componentId: string | null
  status: string | null
  /** 内容渲染风格，驱动 text 卡片的差异化 CSS */
  contentStyle: ContentStyle | null
}

// ===================== Frontend Card Model =====================

export type CardType = 'stage' | 'tool' | 'reasoning' | 'text' | 'train_card' | 'order_card'

export interface StageStep {
  label: string
  status: 'pending' | 'active' | 'done'
}

export interface MessageCard {
  id: number
  role: 'user' | 'ai'
  cardType: CardType | null
  content: string
  data: unknown | null
  streaming: boolean
  timestamp: string
  collapsed: boolean
  label: string
  renderedHtml: string
  contentStyle: ContentStyle | null
  stageSteps?: StageStep[]
  toolStatus?: 'running' | 'done'
  reasoningContent?: string
  reasoningCollapsed?: boolean
}

// ===================== Conversation Types =====================

export interface ConversationData {
  id: number
  title: string
  updateTime: string
  createTime?: string
  messageCount?: number
}

export interface MessageHistoryItem {
  id: number
  role: string
  content: string
  reasoningContent?: string
  createTime?: string
}

export interface ConversationDetailData {
  id: number
  title: string
  messages: MessageHistoryItem[]
  createTime?: string
  updateTime?: string
}
