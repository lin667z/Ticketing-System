<template>
  <div class="train-card-info">
    <div class="route-title">
      <span class="station">{{ departure }}</span>
      <span class="arrow">→</span>
      <span class="station">{{ arrival }}</span>
    </div>
    <div class="train-details">
      <span class="train-number">{{ props['trainNumber'] }}</span>
      <span class="departure-time">{{ props['departureTime'] }}</span>
      <span class="date-info">{{ props['ridingDate'] }} 开</span>
    </div>
    <div class="order-date">订票日期：{{ formattedOrderTime }}</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import dayjs from 'dayjs'

const props = defineProps({
  orderTime: String,
  departure: String,
  arrival: String,
  trainNumber: String,
  ridingDate: String,
  departureTime: String
})

const formattedOrderTime = computed(() => {
  if (!props.orderTime) return '--'
  return dayjs(props.orderTime).format('YYYY-MM-DD HH:mm:ss')
})
</script>

<style lang="scss" scoped>
.train-card-info {
  min-width: 280px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.route-title {
  display: flex;
  align-items: center;
  gap: 8px;
  .station {
    font-size: 18px;
    font-weight: bold;
    color: #111827;
  }
  .arrow {
    font-size: 16px;
    color: #6b7280;
    font-weight: bold;
  }
}

.train-details {
  display: flex;
  align-items: baseline;
  gap: 12px;
  .train-number {
    font-size: 20px;
    font-weight: 900;
    color: #1677ff;
  }
  .departure-time {
    font-size: 20px;
    font-weight: 900;
    color: #111827;
  }
  .date-info {
    font-size: 14px;
    color: #4b5563;
  }
}

.order-date {
  color: #8f9598;
  font-size: 13px;
}
</style>
