<template>
  <Header :class="{ isLogin }">
    <div class="header-wrapper">
      <div>
        <div class="logo">高并发票务系统</div>
      </div>
      <div>
        <ul class="nav-list-wrapper">
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
  </Header>
</template>

<script setup>
import {
  Layout,
  Avatar,
  Dropdown,
  Menu,
  MenuItem,
  message
} from 'ant-design-vue'
import { useRouter, useRoute } from 'vue-router'
import { reactive, toRefs, watch } from 'vue'
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

    li {
      padding: 0 30px;
    }
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
</style>

