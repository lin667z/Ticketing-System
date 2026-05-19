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
              <span style="margin-left: 4px;">AI客服</span>
            </li>
          </a>
          <a v-if="route.fullPath !== '/login'">
            <Dropdown :trigger="['click']">
              <li :style="{ padding: '0 0 0 30px' }">
                <Avatar shape="circle" style="background-color: #333"
                  >{{ state.username?.slice(0, 1)?.toUpperCase() }}
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

    <!-- AI 客服抽屉 -->
    <Drawer
      v-model:visible="isChatVisible"
      title="AI 客服"
      placement="right"
      :width="600"
      :bodyStyle="{ padding: 0, display: 'flex', flexDirection: 'column', background: '#ffffff' }"
    >
      <div class="chat-message-list">
        <div 
          v-for="(msg, index) in chatMessages" 
          :key="index"
          :class="['chat-bubble-wrapper', msg.role]"
        >
          <div class="chat-bubble">
            {{ msg.content }}
          </div>
        </div>
      </div>
      <div class="chat-input-wrapper">
        <div class="custom-input-container">
          <input 
            v-model="inputValue" 
            class="custom-input" 
            placeholder="您可以问我：怎么退票？ / 如何改签？" 
            @keydown.enter="sendMessage" 
          />
          <SendOutlined class="icon-btn send-btn" @click="sendMessage" />
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
  Drawer,
  Input
} from 'ant-design-vue'
import { CustomerServiceOutlined, SendOutlined, PlusOutlined, AudioOutlined } from '@ant-design/icons-vue'
import { useRouter, useRoute } from 'vue-router'
import { reactive, toRefs, watch, ref } from 'vue'
import { fetchLogout } from '@/service'
import Cookie from 'js-cookie'
const username = Cookie.get('username')

const { Header } = Layout
const props = defineProps({
  isLogin: Boolean
})

const { isLogin } = toRefs(props)

const state = reactive({
  username: username
})

const router = useRouter()
const route = useRoute()

// AI客服状态
const isChatVisible = ref(false)
const inputValue = ref('')
const chatMessages = ref([
  { role: 'ai', content: '您好，我是AI客服，关于铁路购票、退票、改签等问题，有什么可以帮您？' }
])

const openChat = () => {
  isChatVisible.value = true
}

const sendMessage = () => {
  const text = inputValue.value.trim()
  if (!text) return
  
  chatMessages.value.push({ role: 'user', content: text })
  inputValue.value = ''
  
  // 暂时只有AI回复，后续可在这里接入真实AI接口
}

watch(
  () => route.fullPath,
  (newValue) => {
    state.username = username
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
  padding: 0 15px !important;
  font-size: 15px;
  transition: color 0.3s;
  &:hover {
    color: #1890ff;
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

/* 聊天窗口样式 */
.chat-message-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px 40px;
  display: flex;
  flex-direction: column;
}

.chat-bubble-wrapper {
  display: flex;
  margin-bottom: 16px;
  
  &.ai {
    justify-content: flex-start;
    .chat-bubble {
      background-color: #f1f3f4;
      color: #333;
      border-radius: 0 12px 12px 12px;
      box-shadow: 0 2px 8px rgba(0,0,0,0.02);
    }
  }
  
  &.user {
    justify-content: flex-end;
    .chat-bubble {
      background-color: #1890ff;
      color: #fff;
      border-radius: 12px 0 12px 12px;
      box-shadow: 0 2px 8px rgba(24,144,255,0.2);
    }
  }
}

.chat-bubble {
  max-width: 80%;
  padding: 10px 14px;
  font-size: 14px;
  line-height: 1.5;
  word-wrap: break-word;
}

.chat-input-wrapper {
  padding: 20px 40px 40px;
  background: transparent;
  border-top: none;
}

.custom-input-container {
  display: flex;
  align-items: center;
  background-color: #fff;
  border-radius: 30px;
  padding: 8px 16px;
  box-shadow: 0 2px 10px rgba(0,0,0,0.08);
  border: 1px solid #eaeaea;
}

.custom-input {
  flex: 1;
  border: none;
  outline: none;
  padding: 8px 12px;
  font-size: 15px;
  background: transparent;
  
  &::placeholder {
    color: #999;
  }
}

.icon-btn {
  font-size: 18px;
  color: #5f6368;
  cursor: pointer;
  padding: 8px;
  border-radius: 50%;
  transition: background-color 0.2s;
  
  &:hover {
    background-color: #f1f3f4;
  }
}

.send-btn {
  color: #1a73e8;
}
</style>
