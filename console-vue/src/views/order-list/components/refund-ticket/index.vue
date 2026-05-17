<template>
  <div class="status-label">
    {{
      TICKET_STATUS_LIST.find((item) => item.value === props?.status)?.label ??
      '--'
    }}
  </div>
  <div v-if="props?.status === 10" class="refund-action">
    <a @click="props.refundClick">退票</a>
  </div>
</template>

<script setup>
import { TICKET_STATUS_LIST } from '@/constants'
const props = defineProps({
  status: Number,
  refundClick: Function
})
</script>

<style lang="scss" scoped>
.status-label {
  color: #111827;
  font-size: 15px;
  font-weight: 500;
}

.refund-action {
  margin-top: 6px;
  line-height: 1.4;
}
</style>
