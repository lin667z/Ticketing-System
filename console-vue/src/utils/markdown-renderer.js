import MarkdownIt from 'markdown-it'

/**
 * 创建并配置 markdown-it 实例
 * 自定义 fenced block 规则：识别 ```json:train-card 和 ```json:order-card 代码块，
 * 返回特殊 HTML 注释标记，供 Vue 组件识别后渲染为业务卡片。
 */
function createMarkdownIt() {
  const md = new MarkdownIt({
    html: false, // 安全：禁用原始 HTML
    linkify: true, // 自动链接
    breaks: true, // 换行转 <br>
    typographer: true
  })

  // 保存默认的 fence 渲染规则
  const defaultFence = md.renderer.rules.fence

  // 自定义 fence 渲染规则
  md.renderer.rules.fence = (tokens, idx, options, env, self) => {
    const token = tokens[idx]
    const info = token.info ? token.info.trim() : ''
    const content = token.content

    // 检测 train-card 组件代码块: ```json:train-card 或 ```json:train_card
    if (info === 'json:train-card' || info === 'json:train_card') {
      try {
        const parsed = JSON.parse(content)
        const data = parsed.data || parsed
        // 收集组件信息到 env，供上层使用
        if (env) {
          env.components = env.components || []
          const compId = `comp-train-${env.components.length}`
          env.components.push({ id: compId, type: 'train_card', data })
          return `<!--AI_COMPONENT:train_card:${encodeURIComponent(JSON.stringify(data))}-->`
        }
        return `<!--AI_COMPONENT:train_card:${encodeURIComponent(JSON.stringify(data))}-->`
      } catch (e) {
        // JSON 解析失败，回退为普通代码块
      }
    }

    // 检测 order-card 组件代码块: ```json:order-card 或 ```json:order_card
    if (info === 'json:order-card' || info === 'json:order_card') {
      try {
        const parsed = JSON.parse(content)
        const data = parsed.data || parsed
        if (env) {
          env.components = env.components || []
          const compId = `comp-order-${env.components.length}`
          env.components.push({ id: compId, type: 'order_card', data })
          return `<!--AI_COMPONENT:order_card:${encodeURIComponent(JSON.stringify(data))}-->`
        }
        return `<!--AI_COMPONENT:order_card:${encodeURIComponent(JSON.stringify(data))}-->`
      } catch (e) {
        // JSON 解析失败，回退为普通代码块
      }
    }

    // 默认代码块渲染
    if (defaultFence) {
      return defaultFence(tokens, idx, options, env, self)
    }
    // 兜底渲染
    const lang = info || ''
    const escapedContent = md.utils.escapeHtml(content)
    return '<pre><code' + (lang ? ' class="language-' + md.utils.escapeHtml(lang) + '"' : '') + '>' +
      escapedContent +
      '</code></pre>\n'
  }

  return md
}

// 单例 markdown-it 实例
const mdInstance = createMarkdownIt()

/**
 * 解析 Markdown 内容中的组件占位标记
 * 返回 { components: [], cleanHtml: string }
 * cleanHtml 中将 <!--AI_COMPONENT:...--> 替换为占位 div
 */
function extractComponents(html) {
  const components = []
  const componentRegex = /<!--AI_COMPONENT:(train_card|order_card):(.+?)-->/g

  let match
  while ((match = componentRegex.exec(html)) !== null) {
    const type = match[1]
    const encodedData = match[2]
    try {
      const data = JSON.parse(decodeURIComponent(encodedData))
      const compId = `comp-${type}-${components.length}`
      components.push({ id: compId, type, data })
    } catch (e) {
      // 忽略解析失败
    }
  }

  // 将占位标记替换为带 data 属性的 div，方便后续 Vue 渲染
  let cleanHtml = html.replace(
    /<!--AI_COMPONENT:(train_card|order_card):(.+?)-->/g,
    (fullMatch, type, encodedData) => {
      try {
        const data = JSON.parse(decodeURIComponent(encodedData))
        const idx = components.findIndex(
          (c) => c.type === type && JSON.stringify(c.data) === JSON.stringify(data)
        )
        return `<div class="ai-card-placeholder" data-card-type="${type}" data-card-index="${idx}"></div>`
      } catch (e) {
        return ''
      }
    }
  )

  return { components, cleanHtml }
}

/**
 * 渲染 Markdown 内容为 HTML
 *
 * @param {string} content - Markdown 原始文本
 * @returns {{ html: string, components: Array<{id: string, type: string, data: object}> }}
 */
export function renderMarkdown(content) {
  if (!content || typeof content !== 'string') {
    return { html: '', components: [] }
  }

  const env = { components: [] }
  const html = mdInstance.render(content, env)

  // env.components 已在 fence 自定义规则中填充
  const components = env.components || []

  return { html, components }
}

/**
 * 流式场景下预处理：处理未闭合的 Markdown 语法，避免渲染异常和闪烁
 *
 * @param {string} content - 流式累积的 Markdown 文本
 * @returns {string} 安全的 Markdown 文本
 */
export function preprocessStreamingContent(content) {
  if (!content) return ''

  let result = content

  // 1. 处理未闭合的 fenced code block
  const allFences = []
  const fenceRegex = /^```/gm
  let match
  while ((match = fenceRegex.exec(result)) !== null) {
    allFences.push(match.index)
  }
  if (allFences.length % 2 === 1) {
    const lastOpenFence = allFences[allFences.length - 1]
    const lineStart = result.lastIndexOf('\n', lastOpenFence - 1)
    result = result.substring(0, lineStart > 0 ? lineStart : 0)
  }

  // 2. 处理最后一行未闭合的行内标记（bold/italic）
  const lastNewline = result.lastIndexOf('\n')
  const lastLine = result.substring(lastNewline + 1)

  // 检查未闭合的 ** (bold)
  const boldCount = (lastLine.match(/\*\*/g) || []).length
  if (boldCount % 2 === 1) {
    // 未闭合的 bold，截断到最后一个 ** 之前
    const lastBold = result.lastIndexOf('**')
    result = result.substring(0, lastBold)
  }

  // 检查未闭合的 ` (inline code) — 仅在最后一行
  const backtickCount = (lastLine.match(/`/g) || []).length
  if (backtickCount % 2 === 1) {
    const lastBacktick = result.lastIndexOf('`')
    result = result.substring(0, lastBacktick)
  }

  return result
}

export default { renderMarkdown, preprocessStreamingContent }
