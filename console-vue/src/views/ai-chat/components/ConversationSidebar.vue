<template>
  <div class="conversation-sidebar" :class="{ collapsed }">
    <!-- 顶部：新建对话 + 折叠按钮 -->
    <div class="sidebar-header">
      <Button type="primary" block @click="$emit('create')" v-if="!collapsed">
        <template #icon><PlusOutlined /></template>
        新对话
      </Button>
      <Button
        v-if="collapsed"
        type="primary"
        shape="circle"
        size="small"
        @click="$emit('create')"
        title="新建对话"
      >
        <template #icon><PlusOutlined /></template>
      </Button>
      <Button
        class="collapse-btn"
        type="text"
        :icon="collapsed ? h(MenuUnfoldOutlined) : h(MenuFoldOutlined)"
        @click="$emit('toggle-collapse')"
      />
    </div>

    <!-- 对话列表 -->
    <div class="conversation-list" ref="listRef">
      <!-- 加载中 -->
      <div v-if="loading" class="list-status">
        <Spin size="small" />
      </div>

      <!-- 空状态 -->
      <div v-else-if="conversations.length === 0" class="list-status">
        <span class="empty-text">暂无对话记录</span>
      </div>

      <!-- 对话项 -->
      <div v-else>
        <Dropdown
          v-for="conv in conversations"
          :key="conv.id"
          :trigger="['contextmenu']"
          :getPopupContainer="() => listRef"
        >
          <div
            :class="['conversation-item', { active: currentId === conv.id }]"
            @click="$emit('select', conv)"
          >
            <div class="conv-avatar">
              <MessageOutlined />
            </div>
            <div class="conv-info" v-if="!collapsed">
              <div class="conv-header">
                <span class="conv-title" :title="conv.title || '新对话'">
                  {{ conv.title || '新对话' }}
                </span>
                <span class="conv-count">{{ conv.messageCount || 0 }}</span>
              </div>
              <div class="conv-time">{{ formatTime(conv.updateTime) }}</div>
            </div>

            <!-- 操作按钮（hover 显示） -->
            <div class="conv-actions" v-if="!collapsed">
              <Button
                type="text"
                size="small"
                class="action-btn"
                title="重命名"
                @click.stop="startRename(conv)"
              >
                <template #icon><EditOutlined /></template>
              </Button>
              <Popconfirm
                title="确定要删除该对话吗？"
                ok-text="确定"
                cancel-text="取消"
                placement="right"
                @confirm="$emit('delete', conv)"
                :getPopupContainer="(trigger) => trigger.parentElement"
              >
                <Button
                  type="text"
                  size="small"
                  class="action-btn"
                  title="删除"
                  @click.stop
                >
                  <template #icon><DeleteOutlined /></template>
                </Button>
              </Popconfirm>
            </div>
          </div>

          <!-- 右键菜单 -->
          <template #overlay>
            <Menu @click="({ key }) => handleMenuClick(key, conv)">
              <Menu.Item key="rename">
                <EditOutlined />
                <span style="margin-left: 8px">重命名</span>
              </Menu.Item>
              <Menu.Divider />
              <Menu.Item key="delete" danger>
                <DeleteOutlined />
                <span style="margin-left: 8px">删除</span>
              </Menu.Item>
            </Menu>
          </template>
        </Dropdown>
      </div>
    </div>

    <!-- 重命名弹窗 -->
    <Modal
      v-model:visible="renameVisible"
      title="重命名对话"
      :ok-text="'确定'"
      :cancel-text="'取消'"
      @ok="confirmRename"
      @cancel="cancelRename"
      :maskClosable="true"
      :destroyOnClose="true"
    >
      <Input
        v-model:value="renameValue"
        placeholder="请输入新名称"
        :maxlength="50"
        @keydown.enter="confirmRename"
        ref="renameInputRef"
      />
    </Modal>

    <!-- 删除确认弹窗（右键菜单触发） -->
    <Modal
      v-model:visible="deleteVisible"
      title="删除对话"
      :ok-text="'确定删除'"
      :cancel-text="'取消'"
      ok-type="danger"
      @ok="confirmDelete"
      :maskClosable="true"
    >
      <p>确定要删除该对话吗？删除后不可恢复。</p>
    </Modal>
  </div>
</template>

<script setup>
import { ref, h, nextTick } from 'vue'
import {
  Button,
  Spin,
  Dropdown,
  Menu,
  Modal,
  Input,
  Popconfirm
} from 'ant-design-vue'
import {
  PlusOutlined,
  MessageOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  EditOutlined,
  DeleteOutlined
} from '@ant-design/icons-vue'
import dayjs from 'dayjs'

// ===================== Props & Emits =====================

defineProps({
  conversations: {
    type: Array,
    default: () => []
  },
  currentId: {
    type: [Number, String],
    default: null
  },
  loading: {
    type: Boolean,
    default: false
  },
  collapsed: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits([
  'select',
  'create',
  'rename',
  'delete',
  'toggle-collapse'
])

// ===================== 重命名状态 =====================

const renameVisible = ref(false)
const renameValue = ref('')
const renameTarget = ref(null)
const renameInputRef = ref(null)

const startRename = (conv) => {
  renameTarget.value = conv
  renameValue.value = conv.title || ''
  renameVisible.value = true
  nextTick(() => {
    renameInputRef.value?.focus?.()
  })
}

const confirmRename = () => {
  if (!renameTarget.value) return
  const newName = renameValue.value.trim()
  if (!newName) {
    return
  }
  emit('rename', { id: renameTarget.value.id, newName })
  renameVisible.value = false
  renameTarget.value = null
  renameValue.value = ''
}

const cancelRename = () => {
  renameVisible.value = false
  renameTarget.value = null
  renameValue.value = ''
}

// ===================== 删除状态（右键菜单触发） =====================

const deleteVisible = ref(false)
const deleteTarget = ref(null)

const confirmDelete = () => {
  if (!deleteTarget.value) return
  emit('delete', deleteTarget.value)
  deleteVisible.value = false
  deleteTarget.value = null
}

// ===================== 右键菜单 =====================

const listRef = ref(null)

const handleMenuClick = (key, conv) => {
  if (key === 'rename') {
    startRename(conv)
  } else if (key === 'delete') {
    deleteTarget.value = conv
    deleteVisible.value = true
  }
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

<style lang="less" scoped>
.conversation-sidebar {
  width: 280px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #f0f0f0;
  background: #fafbfc;
  transition: width 0.25s ease;

  &.collapsed {
    width: 72px;

    .conversation-item {
      justify-content: center;
      padding: 14px 0;
    }
  }
}

.sidebar-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px;
  border-bottom: 1px solid #f0f0f0;

  .ant-btn:not(.collapse-btn) {
    flex: 1;
    height: 40px;
    border-radius: 8px;
    font-weight: 500;
  }

  .collapse-btn {
    flex: 0 0 auto;
    width: 36px;
    min-width: 36px;
    height: 36px;
    padding: 0;
    color: #666;
    border-radius: 8px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
  }
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;

  &::-webkit-scrollbar {
    width: 4px;
  }

  &::-webkit-scrollbar-thumb {
    background: #d9d9d9;
    border-radius: 2px;
  }
}

.list-status {
  text-align: center;
  color: #999;
  padding: 32px 16px;
  font-size: 13px;

  .empty-text {
    color: #bbb;
  }
}

.conversation-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.18s ease;
  margin-bottom: 2px;
  position: relative;

  &:hover {
    background: #f0f2f5;

    .conv-actions {
      opacity: 1;
    }
  }

  &.active {
    background: #e8f0fe;

    .conv-title {
      color: #1677ff;
      font-weight: 600;
    }
  }
}

.conv-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #e8f0fe;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #1677ff;
  font-size: 16px;
  flex-shrink: 0;
}

.conv-info {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.conv-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.conv-title {
  font-size: 13px;
  color: #333;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.4;
  flex: 1;
}

.conv-count {
  font-size: 11px;
  color: #aaa;
  background: #f0f0f0;
  padding: 0 6px;
  border-radius: 10px;
  flex-shrink: 0;
  min-width: 18px;
  text-align: center;
  line-height: 18px;
}

.conv-time {
  font-size: 11px;
  color: #999;
  margin-top: 2px;
}

.conv-actions {
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.15s ease;
  flex-shrink: 0;

  .action-btn {
    color: #bbb;
    font-size: 12px;
    width: 24px;
    height: 24px;
    padding: 0;

    &:hover {
      color: #555;
      background: rgba(0, 0, 0, 0.04);
    }
  }
}
</style>
