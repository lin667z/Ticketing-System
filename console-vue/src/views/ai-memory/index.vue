<template>
  <div class="ai-memory-container">
    <Card>
      <div class="title-wrapper">
        <TypographyTitle :level="4">AI 记忆偏好管理</TypographyTitle>
        <Button type="primary" @click="showCreateModal">新增偏好</Button>
      </div>
      <Divider />
      <Table
        :columns="columns"
        :dataSource="memories"
        :loading="loading"
        rowKey="id"
        :pagination="false"
      >
        <template #preference="{ record }">
          <Tag v-if="record.preferenceType" :color="typeColor(record.preferenceType)">
            {{ typeLabel(record.preferenceType) }}
          </Tag>
          <span v-else>--</span>
        </template>
        <template #action="{ record }">
          <Space>
            <Button size="small" @click="handleEdit(record)">编辑</Button>
            <Popconfirm title="确定要删除该偏好吗？" @confirm="handleDelete(record.id)">
              <Button size="small" danger>删除</Button>
            </Popconfirm>
          </Space>
        </template>
      </Table>
      <div v-if="!loading && memories.length === 0" :style="{ textAlign: 'center', padding: '40px', color: '#999' }">
        暂无记忆偏好，点击"新增偏好"添加
      </div>
    </Card>

    <Modal
      v-model:visible="modalVisible"
      :title="editingId ? '编辑偏好' : '新增偏好'"
      @ok="handleSubmit"
      @cancel="resetForm"
    >
      <Form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <FormItem label="偏好类型" required>
          <Select v-model:value="form.preferenceType" placeholder="请选择偏好类型">
            <SelectOption v-for="item in preferenceTypes" :key="item.value" :value="item.value">
              {{ item.label }}
            </SelectOption>
          </Select>
        </FormItem>
        <FormItem label="偏好键" required>
          <Input v-model:value="form.memoryKey" placeholder="如 morning_departure" />
        </FormItem>
        <FormItem label="偏好内容" required>
          <Input v-model:value="form.memoryContent" placeholder="如 偏好06:00-09:00的早班列车" />
        </FormItem>
      </Form>
    </Modal>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive } from 'vue'
import {
  Card, Table, Button, Modal, Form, FormItem, Input, Select, SelectOption,
  Tag, Space, Popconfirm, Divider, TypographyTitle, message
} from 'ant-design-vue'
import { listMemories, createMemory, updateMemory, deleteMemory } from '@/service'

const loading = ref(false)
const memories = ref([])
const modalVisible = ref(false)
const editingId = ref(null)

const form = reactive({
  preferenceType: '',
  memoryKey: '',
  memoryContent: ''
})

const preferenceTypes = [
  { value: 'ROUTE', label: '出行路线' },
  { value: 'TIME_WINDOW', label: '时间窗口' },
  { value: 'SEAT_CLASS', label: '座位等级' },
  { value: 'BUDGET', label: '预算约束' },
  { value: 'TRAIN_TYPE', label: '列车类型' },
  { value: 'STATION', label: '站点偏好' },
  { value: 'CUSTOM', label: '自定义' }
]

const columns = [
  { title: '偏好键', dataIndex: 'memoryKey', key: 'memoryKey' },
  { title: '偏好内容', dataIndex: 'memoryContent', key: 'memoryContent', ellipsis: true },
  { title: '类型', key: 'preference', slots: { customRender: 'preference' } },
  { title: '操作', key: 'action', slots: { customRender: 'action' }, width: 160 }
]

const typeColor = (type) => {
  const colors = {
    ROUTE: 'blue', TIME_WINDOW: 'green', SEAT_CLASS: 'orange',
    BUDGET: 'red', TRAIN_TYPE: 'purple', STATION: 'cyan', CUSTOM: 'default'
  }
  return colors[type] || 'default'
}

const typeLabel = (type) => {
  const found = preferenceTypes.find(item => item.value === type)
  return found ? found.label : type
}

const fetchMemories = async () => {
  loading.value = true
  try {
    const res = await listMemories()
    memories.value = res.data || []
  } catch (err) {
    message.error('获取偏好列表失败')
  } finally {
    loading.value = false
  }
}

const showCreateModal = () => {
  editingId.value = null
  form.preferenceType = ''
  form.memoryKey = ''
  form.memoryContent = ''
  modalVisible.value = true
}

const handleEdit = (record) => {
  editingId.value = record.id
  form.preferenceType = record.preferenceType || ''
  form.memoryKey = record.memoryKey || ''
  form.memoryContent = record.memoryContent || ''
  modalVisible.value = true
}

const handleSubmit = async () => {
  if (!form.memoryKey || !form.memoryContent) {
    message.error('请填写完整的偏好信息')
    return
  }
  try {
    const body = {
      memoryKey: form.memoryKey,
      memoryContent: form.memoryContent
    }
    if (editingId.value) {
      await updateMemory(editingId.value, body)
      message.success('更新成功')
    } else {
      await createMemory(body)
      message.success('创建成功')
    }
    modalVisible.value = false
    resetForm()
    await fetchMemories()
  } catch (err) {
    message.error('操作失败')
  }
}

const handleDelete = async (id) => {
  try {
    await deleteMemory(id)
    message.success('删除成功')
    await fetchMemories()
  } catch (err) {
    message.error('删除失败')
  }
}

const resetForm = () => {
  form.preferenceType = ''
  form.memoryKey = ''
  form.memoryContent = ''
  editingId.value = null
}

onMounted(() => {
  fetchMemories()
})
</script>

<style scoped>
.ai-memory-container {
  padding: 24px;
}
.title-wrapper {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
