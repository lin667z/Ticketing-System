<template>
  <div class="order-card">
    <div class="order-card-header">
      <div class="order-train-number">{{ data.trainNumber }}</div>
      <div class="order-status" :class="statusClass">{{ data.status }}</div>
    </div>
    <div class="order-card-route">
      <div class="station departure">
        <div class="station-name">{{ data.departure }}</div>
        <div class="station-time">{{ data.departureTime }}</div>
      </div>
      <div class="route-line">
        <div class="route-arrow">
          <span class="line"></span>
          <span class="arrow-head"></span>
        </div>
      </div>
      <div class="station arrival">
        <div class="station-name">{{ data.arrival }}</div>
        <div class="station-time">{{ data.arrivalTime }}</div>
      </div>
    </div>
    <div class="order-card-details">
      <div class="detail-row" v-if="data.orderSn">
        <span class="detail-label">订单号</span>
        <span class="detail-value order-sn">{{ maskOrderSn(data.orderSn) }}</span>
      </div>
      <div class="detail-row" v-if="data.ridingDate">
        <span class="detail-label">乘车日期</span>
        <span class="detail-value">{{ data.ridingDate }}</span>
      </div>
      <div class="detail-row" v-if="data.passengers && data.passengers.length > 0">
        <span class="detail-label">乘车人</span>
        <span class="detail-value">{{ data.passengers.join('、') }}</span>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'OrderCard',
  props: {
    data: {
      type: Object,
      required: true
    }
  },
  computed: {
    statusClass() {
      const status = (this.data.status || '').toLowerCase()
      if (status.includes('已支付') || status.includes('已完成') || status.includes('paid')) {
        return 'status-success'
      }
      if (status.includes('已取消') || status.includes('已退款') || status.includes('cancelled')) {
        return 'status-cancelled'
      }
      if (status.includes('待支付') || status.includes('pending')) {
        return 'status-pending'
      }
      return 'status-default'
    }
  },
  methods: {
    maskOrderSn(sn) {
      if (!sn) return ''
      const str = String(sn)
      if (str.length <= 8) return str
      return str.substring(0, 4) + '****' + str.substring(str.length - 4)
    }
  }
}
</script>

<style lang="scss" scoped>
.order-card {
  background: #fff;
  border: 1px solid #e8edf2;
  border-radius: 10px;
  overflow: hidden;
  margin: 10px 0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  font-size: 13px;
}

.order-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: linear-gradient(135deg, #52c41a, #73d13d);
  color: #fff;
}

.order-train-number {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 1px;
}

.order-status {
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.25);
  font-weight: 500;

  &.status-success {
    background: rgba(255, 255, 255, 0.3);
  }

  &.status-cancelled {
    background: rgba(255, 77, 79, 0.3);
  }

  &.status-pending {
    background: rgba(250, 173, 20, 0.35);
    color: #fff;
  }

  &.status-default {
    background: rgba(255, 255, 255, 0.2);
  }
}

.order-card-route {
  display: flex;
  align-items: center;
  padding: 16px;
  gap: 0;
}

.station {
  flex: 0 0 auto;

  &.departure {
    text-align: right;
  }

  &.arrival {
    text-align: left;
  }
}

.station-name {
  font-size: 16px;
  font-weight: 600;
  color: #1f2328;
  margin-bottom: 4px;
}

.station-time {
  font-size: 13px;
  color: #888;
}

.route-line {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0 16px;
  min-width: 80px;
}

.route-arrow {
  display: flex;
  align-items: center;
  width: 100%;

  .line {
    flex: 1;
    height: 1px;
    background: #d9d9d9;
  }

  .arrow-head {
    width: 0;
    height: 0;
    border-top: 5px solid transparent;
    border-bottom: 5px solid transparent;
    border-left: 8px solid #d9d9d9;
  }
}

.order-card-details {
  border-top: 1px solid #f0f0f0;
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.detail-row {
  display: flex;
  align-items: center;
}

.detail-label {
  color: #999;
  font-size: 12px;
  width: 64px;
  flex-shrink: 0;
}

.detail-value {
  color: #333;
  font-size: 13px;

  &.order-sn {
    font-family: 'Courier New', monospace;
    font-size: 12px;
    color: #888;
  }
}
</style>
