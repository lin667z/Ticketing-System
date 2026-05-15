<template>
  <div
    :style="{
      position: 'relative',
      minWidth: !state.collapse ? '200px' : '80px',
      height: 'calc(100vh - 64px)'
    }"
  >
    <Sider
      :collapsed="state.collapse"
      collapsible
      :trigger="null"
      @collapse="() => (state.collapse = !state.collapse)"
    >
      <Menu v-model:selectedKeys="selectedKeys" mode="inline">
        <SubMenu key="sub1" @titleClick="titleClick">
          <template #icon>
            <IconFont type="icon-icon_yingyongguanli" />
          </template>
          <template #title>车票管理 </template>
          <Item key="ticketSearch"
            ><RouterLink to="/ticketSearch">车票查询</RouterLink></Item
          >
        </SubMenu>
        <SubMenu key="sub2" @titleClick="titleClick">
          <template #icon>
            <IconFont type="icon-pingtaixinxiguanli" />
          </template>
          <template #title>常用信息管理</template>
          <Item key="userInfo"
            ><RouterLink to="/userInfo">个人信息</RouterLink></Item
          >
          <Item key="passenger"
            ><RouterLink to="/passenger">乘车人</RouterLink></Item
          >
        </SubMenu>
        <SubMenu key="sub4" @titleClick="titleClick">
          <template #icon>
            <IconFont type="icon-dingdan" />
          </template>
          <template #title>订单管理</template>
          <Item key="order"
            ><RouterLink to="/ticketList">车票订单</RouterLink>
          </Item>
          <Item key="personalTicket">
            <RouterLink to="/personalTicket">本人车票</RouterLink>
          </Item>
        </SubMenu>
      </Menu>
    </Sider>
    <div
      class="sider-footer"
      :style="{
        width: !state.collapse ? '200px' : '80px'
      }"
    >
      <Tooltip title="折起">
        <div
          :class="state.collapse && 'collaspe-icon'"
          @click="() => (state.collapse = !state.collapse)"
          class="icon-wrapper"
        >
          <IconFont type="icon-zhedie" /></div
      ></Tooltip>
      <Divider v-if="!state.collapse" type="vertical" />
      <Tooltip title="退出登录">
        <div @click="logout" v-if="!state.collapse" class="icon-wrapper">
          <IconFont type="icon-tuichudenglu" />
        </div>
      </Tooltip>
    </div>
  </div>
</template>

<script setup>
import { reactive } from 'vue'
import IconFont from '@/components/icon-font'
import { Layout, Menu, Divider, message, Tooltip } from 'ant-design-vue'
import { fetchLogout } from '@/service'
import { RouterLink } from 'vue-router'
import Cookie from 'js-cookie'
const { SubMenu, Item } = Menu
const { Sider } = Layout
const props = defineProps({
  isLogin: Boolean
})

const state = reactive({
  collapse: false
})
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
.ant-layout-sider {
  position: fixed;
  left: 0;
  top: 64px;
  height: calc(100vh - 64px);
  background-color: #fff;
  transition: width 0.2s ease, min-width 0.2s ease, max-width 0.2s ease;
  box-shadow: 1px 0 0 rgba(15, 23, 42, 0.06);
  z-index: 10;
}
.sider-footer {
  position: fixed;
  z-index: 100;
  bottom: 0;
  left: 0;
  width: 100%;
  display: flex;
  justify-content: space-around;
  padding: 14px 18px;
  align-items: center;
  background-color: #fff;
  border-top: 1px solid #eef0f4;
  .icon-wrapper {
    cursor: pointer;
    width: 32px;
    height: 32px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border-radius: 8px;
    color: #666;
    transition: background-color 0.18s ease, color 0.18s ease, transform 0.18s ease;
    &:hover {
      background: #f5f7fa;
      color: #111;
    }
  }
  .collaspe-icon {
    transform: rotate(180deg);
  }
}
:deep(.ant-menu) {
  background-color: #fff;
  color: #666;
  border-right: none;
  padding: 10px 8px 64px;
}
:deep(.ant-menu-sub.ant-menu-inline) {
  background: #fff;
}
:deep(.ant-menu-inline .ant-menu-item),
:deep(.ant-menu-inline .ant-menu-submenu-title) {
  height: 40px;
  line-height: 40px;
  margin: 3px 0;
  border-radius: 8px;
}
:deep(.ant-menu:not(.ant-menu-horizontal) .ant-menu-item-selected) {
  background: #f5f7fa;
  color: #333;
  font-weight: 600;
}
:deep(.ant-menu:not(.ant-menu-horizontal) .ant-menu-item-selected::before) {
  content: '';
  position: absolute;
  left: 0;
  top: 10px;
  width: 3px;
  height: 20px;
  border-radius: 2px;
  background: #111;
}
:deep(.ant-menu-submenu-selected) {
  color: #333;
}
:deep(.ant-menu-item-selected) {
  color: #333;
}
:deep(.ant-menu-item-active) {
  color: #333;
}
:deep(.ant-menu-submenu-active) {
  color: #333;
}
:deep(.ant-menu-light .ant-menu-item:hover) {
  color: #333;
}
:deep(.ant-menu-submenu:hover) {
  color: #333;
}
:deep(.ant-menu-inline .ant-menu-item::after) {
  border-right: none;
}
:deep(.ant-menu-light .ant-menu-submenu-title:hover) {
  color: #333;
}
:deep(.ant-menu-submenu:hover > .ant-menu-submenu-title > .ant-menu-submenu-arrow) {
  color: #333;
}
:deep(.ant-menu-submenu-arrow) {
  color: #666;
}
:deep(.ant-menu-title-content) {
  user-select: none;
}
:deep(.ant-menu-item-group-title) {
  user-select: none;
}
</style>
