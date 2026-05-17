<template>
  <Space class="edit-actions" direction="vertical" size="small">
    <Button @click="() => props.cancel(orderSn)" type="link">取消订单</Button>
    <Button type="link" @click="() => props.pay(orderSn)">去支付</Button>
  </Space>
</template>

<script setup>
import { Space, Button } from 'ant-design-vue'

const props = defineProps({
  orderSn: String,
  cancel: Function,
  pay: Function
})
</script>

<style lang="scss" scoped>
.edit-actions {
  align-items: flex-start;
}

.edit-actions :deep(.ant-btn-link) {
  height: auto;
  padding: 0;
  line-height: 1.5;
}
</style>
