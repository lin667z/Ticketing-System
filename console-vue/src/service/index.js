import http from './axios'
import Cookie from 'js-cookie'

const fetchLogin = async (body) => {
  const { data } = await http({
    method: 'POST',
    url: '/api/user-service/v1/login',
    data: body
  })
  http.defaults.headers.common['Authorization'] = data.data?.accessToken
  return data
}

const fetchRegister = async (body) => {
  const { data } = await http({
    method: 'POST',
    url: '/api/user-service/register',
    data: body
  })
  return data
}

const fetchTicketSearch = async (params) => {
  const { data } = await http({
    method: 'GET',
    url: '/api/ticket-service/ticket/query',
    params
  })
  return data
}

const fetchRegionStation = async (params) => {
  const { data } = await http({
    method: 'GET',
    url: '/api/ticket-service/region-station/query',
    params
  })
  return data
}

const fetchPassengerList = async (params) => {
  const { data } = await http({
    method: 'GET',
    url: '/api/user-service/passenger/query',
    params
  })
  return data
}
const fetchDeletePassenger = async (body) => {
  const { data } = await http({
    method: 'POST',
    url: '/api/user-service/passenger/remove',
    data: body
  })
  return data
}

const fetchAddPassenger = async (body) => {
  const { data } = await http({
    method: 'POST',
    url: '/api/user-service/passenger/save',
    data: body
  })
  return data
}

const fetchEditPassenger = async (body) => {
  const { data } = await http({
    method: 'POST',
    url: '/api/user-service/passenger/update',
    data: body
  })
  return data
}
const fetchLogout = async (body) => {
  const { data } = await http({
    method: 'GET',
    url: '/api/user-service/logout',
    data: body
  })
  http.defaults.headers.common['Authorization'] = null
  return data
}

const fetchBuyTicket = async (body) => {
  const { data } = await http({
    method: 'POST',
    url: '/api/ticket-service/ticket/purchase',
    data: body
  })

  return data
}

const fetchOrderBySn = async (params) => {
  const { data } = await http({
    method: 'GET',
    url: '/api/order-service/order/ticket/query',
    params
  })
  return data
}

const fetchPay = async (body) => {
  const { data } = await http({
    method: 'POST',
    url: '/api/pay-service/pay/create',
    data: body
  })
  return data
}

const fetchStationAll = async () => {
  const { data } = await http({
    method: 'GET',
    url: '/api/ticket-service/station/all'
  })
  return data
}

const fechUserInfo = async (params) => {
  const { data } = await http({
    method: 'GET',
    url: '/api/user-service/query',
    params
  })
  return data
}

const fetchTrainStation = async (params) => {
  const { data } = await http({
    method: 'GET',
    url: '/api/ticket-service/train-station/query',
    params
  })
  return data
}

const fetchTicketList = async (params) => {
  const { data } = await http({
    method: 'GET',
    url: '/api/order-service/order/ticket/page',
    params
  })
  return data
}

const fetchOrderCancel = async (body) => {
  const { data } = await http({
    method: 'POST',
    url: '/api/ticket-service/ticket/cancel',
    data: body
  })
  return data
}

const fetchUserUpdate = async (body) => {
  const { data } = await http({
    method: 'POST',
    url: '/api/user-service/update',
    data: body
  })
  return data
}

const fetchOrderStatus = async (params) => {
  const { data } = await http({
    method: 'GET',
    url: '/api/pay-service/pay/query/order-sn',
    params
  })
  return data
}

const fetchMyTicket = async (params) => {
  const { data } = await http({
    method: 'GET',
    url: '/api/order-service/order/ticket/self/page',
    params
  })
  return data
}

const fetchRefundTicket = async (body) => {
  const { data } = await http({
    method: 'POST',
    url: '/api/ticket-service/ticket/refund',
    data: body
  })
  return data
}

const fetchAiChatStream = async (body, options = {}) => {
  const { onChunk, signal } = options
  const headers = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream'
  }
  const token = Cookie.get('token') || http.defaults.headers.common.Authorization
  if (token) {
    headers.Authorization = token
  }

  const response = await fetch('/api/ai-service/chat/stream', {
    method: 'POST',
    headers,
    body: JSON.stringify(body),
    signal
  })

  if (!response.ok) {
    const errorText = await response.text()
    let errorMessage = errorText || `AI stream request failed: ${response.status}`
    try {
      const errorBody = JSON.parse(errorText)
      errorMessage = errorBody.message || errorBody.error || errorMessage
    } catch (ignore) { }
    throw new Error(errorMessage)
  }
  if (!response.body) {
    throw new Error('ReadableStream is not supported by this browser.')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let finalChunk = null

  const handlePayload = (payload) => {
    const data = payload.trim()
    if (!data || data === '[DONE]') return
    let chunk
    try {
      chunk = JSON.parse(data)
    } catch (error) {
      throw new Error(data)
    }
    finalChunk = chunk
    if (onChunk) {
      onChunk(chunk)
    }
  }

  const parseEvent = (eventText) => {
    const dataLines = eventText
      .split('\n')
      .map((line) => line.trimEnd())
      .filter((line) => line.startsWith('data:'))
      .map((line) => line.slice(5).trimStart())
    if (dataLines.length > 0) {
      handlePayload(dataLines.join('\n'))
      return
    }
    const trimmed = eventText.trim()
    if (trimmed.startsWith('{')) {
      handlePayload(trimmed)
    }
  }

  const drainBuffer = (flush = false) => {
    let separatorIndex = buffer.search(/\n{2,}/)
    while (separatorIndex !== -1) {
      const eventText = buffer.slice(0, separatorIndex)
      const separatorLength = buffer.slice(separatorIndex).match(/^\n{2,}/)[0].length
      buffer = buffer.slice(separatorIndex + separatorLength)
      parseEvent(eventText)
      separatorIndex = buffer.search(/\n{2,}/)
    }

    if (flush && buffer.trim()) {
      const pending = buffer.trim()
      buffer = ''
      parseEvent(pending)
    }
  }

  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
    drainBuffer()
  }
  buffer += decoder.decode()
  drainBuffer(true)
  return finalChunk
}

// AI 客服对话管理相关 API

// 创建新对话
const createConversation = async () => {
  const { data } = await http({
    method: 'POST',
    url: '/api/ai-service/conversation/create'
  })
  return data
}

// 删除对话
const deleteConversation = async (id) => {
  const { data } = await http({
    method: 'DELETE',
    url: `/api/ai-service/conversation/${id}`
  })
  return data
}

// 重命名对话
const renameConversation = async (id, newName) => {
  const { data } = await http({
    method: 'PUT',
    url: `/api/ai-service/conversation/${id}/rename`,
    data: { newName }
  })
  return data
}

// 获取对话列表
const getConversationList = async () => {
  const { data } = await http({
    method: 'GET',
    url: '/api/ai-service/conversation/list'
  })
  return data
}

// 获取对话详情（含消息历史）
const getConversationDetail = async (id) => {
  const { data } = await http({
    method: 'GET',
    url: `/api/ai-service/conversation/${id}`
  })
  return data
}

export {
  fetchLogin,
  fetchRegister,
  fetchTicketSearch,
  fetchRegionStation,
  fetchPassengerList,
  fetchDeletePassenger,
  fetchAddPassenger,
  fetchEditPassenger,
  fetchLogout,
  fetchBuyTicket,
  fetchOrderBySn,
  fetchPay,
  fetchStationAll,
  fechUserInfo,
  fetchTrainStation,
  fetchTicketList,
  fetchOrderCancel,
  fetchOrderStatus,
  fetchUserUpdate,
  fetchMyTicket,
  fetchRefundTicket,
  fetchAiChatStream,
  createConversation,
  deleteConversation,
  renameConversation,
  getConversationList,
  getConversationDetail
}
