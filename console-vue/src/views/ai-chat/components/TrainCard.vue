<template>
  <div class="train-card">
    <div class="train-card-header">
      <div class="train-number">{{ data.trainNumber }}</div>
      <div class="train-date" v-if="data.date">{{ data.date }}</div>
    </div>
    <div class="train-card-route">
      <div class="station departure">
        <div class="station-name">{{ data.departure }}</div>
        <div class="station-time">{{ data.departureTime }}</div>
      </div>
      <div class="route-line">
        <div class="route-duration">{{ data.duration }}</div>
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
    <div class="train-card-seats" v-if="data.seats && data.seats.length > 0">
      <div class="seats-title">席别信息</div>
      <div class="seats-list">
        <div
          class="seat-item"
          v-for="(seat, idx) in data.seats"
          :key="idx"
        >
          <span class="seat-type">{{ seat.type }}</span>
          <span class="seat-price">¥{{ seat.price }}</span>
          <span class="seat-remaining" :class="{ low: seat.remaining < 20 }">
            余{{ seat.remaining }}张
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'TrainCard',
  props: {
    data: {
      type: Object,
      required: true
    }
  }
}
</script>

<style lang="scss" scoped>
.train-card {
  background: #fff;
  border: 1px solid #e8edf2;
  border-radius: 10px;
  overflow: hidden;
  margin: 10px 0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  font-size: 13px;
}

.train-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: linear-gradient(135deg, #1677ff, #4096ff);
  color: #fff;
}

.train-number {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 1px;
}

.train-date {
  font-size: 12px;
  opacity: 0.85;
}

.train-card-route {
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

.route-duration {
  font-size: 11px;
  color: #999;
  margin-bottom: 6px;
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

.train-card-seats {
  border-top: 1px solid #f0f0f0;
  padding: 12px 16px;
}

.seats-title {
  font-size: 12px;
  color: #999;
  margin-bottom: 8px;
}

.seats-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.seat-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: #fafbfc;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  font-size: 13px;
}

.seat-type {
  color: #333;
  font-weight: 500;
}

.seat-price {
  color: #ff6b35;
  font-weight: 600;
}

.seat-remaining {
  color: #1677ff;
  font-size: 12px;

  &.low {
    color: #ff4d4f;
  }
}
</style>
