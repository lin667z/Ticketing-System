<script setup>
import { computed, onMounted, reactive, ref, toRaw, watch } from 'vue'
import { Button, DatePicker, Select, Spin, Table, Tooltip, message } from 'ant-design-vue'
import {
  CalendarOutlined,
  ClockCircleOutlined,
  SearchOutlined,
  SwapRightOutlined,
  UserOutlined
} from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import { getWeekNumber } from '@/utils'
import { fetchStationAll, fetchTicketSearch, fetchTrainStation } from '@/service/index'
import { SEAT_CLASS_TYPE_LIST, TRAIN_BRAND_LIST } from '@/constants'

const presaleDays = 15
const today = dayjs().startOf('day')
const maxTicketDate = today.add(presaleDays - 1, 'day')

const defaultStations = [
  { name: '北京', code: 'BJP' },
  { name: '杭州东', code: 'HZH' },
  { name: '上海', code: 'SHH' },
  { name: '广州', code: 'GZQ' },
  { name: '深圳', code: 'SZQ' },
  { name: '南京', code: 'NJH' },
  { name: '成都', code: 'CDW' },
  { name: '武汉', code: 'WHN' }
]

const tripTypeOptions = [
  { label: '单程', value: 'single' },
  { label: '往返', value: 'round' }
]

const passengerOptions = [
  { label: '普通', value: 0 },
  { label: '学生', value: 1 }
]

const carRangeTime = [
  { value: 0, label: '00:00-24:00', start: '00:00', end: '24:00' },
  { value: 1, label: '00:00-06:00', start: '00:00', end: '06:00' },
  { value: 2, label: '06:00-12:00', start: '06:00', end: '12:00' },
  { value: 3, label: '12:00-18:00', start: '12:00', end: '18:00' },
  { value: 4, label: '18:00-24:00', start: '18:00', end: '24:00' }
]

const headSearch = reactive({
  tripType: 'single',
  fromStation: 'BJP',
  toStation: 'HZH',
  departureDate: today,
  returnDate: today,
  car_type: [],
  departure: [],
  arrival: [],
  seat: [],
  departureTimeRange: 0,
  passengerType: 0,
  showDiscount: false,
  showPoint: false,
  showBookable: false
})

const state = reactive({
  seatClassTypeListSelect: [],
  trainBrandListSelect: [],
  trainList: [],
  stationList: [],
  trainStationList: [],
  loading: false,
  pageLoading: false,
  searchLoading: false
})

const rowState = reactive({
  rawTrainList: []
})

const currCityStations = ref([])
const currArrivalStations = ref([])

const cardInfoColumns = [
  { title: '站序', dataIndex: 'sequence', key: 'sequence' },
  { title: '车站', dataIndex: 'departure', key: 'departure' },
  { title: '到达时间', dataIndex: 'arrivalTime', key: 'arrivalTime' },
  { title: '出发时间', dataIndex: 'departureTime', key: 'departureTime' },
  { title: '停留时间', dataIndex: 'stopoverTime', key: 'stopoverTime' }
]

const days = new Array(presaleDays).fill(',').map((item, index) => today.add(index, 'day'))

const stationOptions = computed(() => {
  const source = state.stationList?.length ? state.stationList : defaultStations
  return source
    .map((item) => ({
      label: item.name || item.stationName || item.label || item.code,
      value: item.code || item.value
    }))
    .filter((item) => item.label && item.value)
})

const trainBrandOptions = computed(() => {
  const source = state.trainBrandListSelect?.length
    ? state.trainBrandListSelect
    : TRAIN_BRAND_LIST.map((item) => item.code)
  return source
    .map((code) => TRAIN_BRAND_LIST.find((item) => item.code === Number(code)))
    .filter(Boolean)
})

const seatFilterOptions = computed(() => {
  const source = state.seatClassTypeListSelect?.length
    ? state.seatClassTypeListSelect
    : SEAT_CLASS_TYPE_LIST.slice(0, 8).map((item) => item.code)
  return source
    .map((code) => SEAT_CLASS_TYPE_LIST.find((item) => item.code === Number(code)))
    .filter(Boolean)
})

const selectedFromStationName = computed(() => {
  return stationOptions.value.find((item) => item.value === headSearch.fromStation)?.label || headSearch.fromStation
})

const selectedToStationName = computed(() => {
  return stationOptions.value.find((item) => item.value === headSearch.toStation)?.label || headSearch.toStation
})

const summaryText = computed(() => {
  return `${selectedFromStationName.value} --> ${selectedToStationName.value} (${headSearch.departureDate.format(
    'M月D日'
  )} ${getWeekNumber(headSearch.departureDate.day())}) 共计${state.trainList?.length || 0}个车次`
})

const disabledTicketDate = (current) => {
  if (!current) return false
  const date = current.startOf('day')
  return date.isBefore(today) || date.isAfter(maxTicketDate)
}

const normalizeTicketDate = (date) => {
  if (!date) return today
  if (date.isBefore(today)) return today
  if (date.isAfter(maxTicketDate)) return maxTicketDate
  return date
}

const getAvailableSeats = (seatClassList) => {
  if (!seatClassList) return []
  return seatClassList.filter((item) => item.price !== undefined && item.price !== null)
}

const getSeatName = (type) => {
  const seat = SEAT_CLASS_TYPE_LIST.find((item) => item.code === Number(type))
  return seat ? seat.label : '其他'
}

const isNextDay = (dep, arr) => {
  if (!dep || !arr) return false
  return arr < dep
}

const isTimeInRange = (time, rangeValue) => {
  if (!time || !rangeValue) return true
  const range = carRangeTime.find((item) => item.value === rangeValue)
  if (!range) return true
  return time >= range.start && time < range.end
}

const toggleListValue = (list, value) => {
  const idx = list.indexOf(value)
  if (idx > -1) {
    list.splice(idx, 1)
  } else {
    list.push(value)
  }
}

const normalizeTrainList = (list = []) => {
  return list.map((item) => ({
    ...item,
    key: item.trainId
  }))
}

const hasBookableSeat = (train) => {
  return train.seatClassList?.some((seat) => seat.quantity > 0)
}

const applyFilters = () => {
  let list = [...rowState.rawTrainList]
  if (headSearch.car_type.length) {
    list = list.filter((item) => {
      const trainBrands = String(item.trainBrand || '')
        .split(',')
        .map((code) => Number(code))
      return trainBrands.some((code) => headSearch.car_type.includes(code))
    })
  }
  if (headSearch.departure.length) {
    list = list.filter((item) => headSearch.departure.includes(item.departure))
  }
  if (headSearch.arrival.length) {
    list = list.filter((item) => headSearch.arrival.includes(item.arrival))
  }
  if (headSearch.seat.length) {
    list = list.filter((item) => {
      return item.seatClassList?.some(
        (seat) => headSearch.seat.includes(Number(seat.type)) && seat.quantity
      )
    })
  }
  if (headSearch.departureTimeRange) {
    list = list.filter((item) => isTimeInRange(item.departureTime, headSearch.departureTimeRange))
  }
  if (headSearch.showBookable) {
    list = list.filter(hasBookableSeat)
  }
  state.trainList = list
}

watch(headSearch, applyFilters)

const setTicketSearchResult = (data) => {
  rowState.rawTrainList = normalizeTrainList(data.trainList || [])
  state.trainList = [...rowState.rawTrainList]
  state.trainBrandListSelect = data.trainBrandList || []
  state.seatClassTypeListSelect = data.seatClassTypeList || []
  currCityStations.value = data.departureStationList || []
  currArrivalStations.value = data.arrivalStationList || []
  applyFilters()
}

const handSubmit = () => {
  headSearch.departureDate = normalizeTicketDate(headSearch.departureDate)
  headSearch.returnDate = normalizeTicketDate(headSearch.returnDate)
  state.searchLoading = true
  const { fromStation, toStation, departure, arrival, departureDate } = toRaw(headSearch)
  fetchTicketSearch({
    fromStation,
    toStation,
    departure: departure[0],
    arrival: arrival[0],
    departureDate: departureDate.format('YYYY-MM-DD')
  })
    .then((res) => {
      if (!res.success) return message.error(res.message)
      setTicketSearchResult(res.data || {})
    })
    .finally(() => {
      state.searchLoading = false
    })
}

onMounted(() => {
  headSearch.departureDate = normalizeTicketDate(headSearch.departureDate)
  headSearch.returnDate = normalizeTicketDate(headSearch.returnDate)
  state.pageLoading = true
  Promise.all([
    fetchTicketSearch({
      fromStation: headSearch.fromStation,
      toStation: headSearch.toStation,
      departureDate: headSearch.departureDate.format('YYYY-MM-DD')
    }),
    fetchStationAll()
  ])
    .then(([ticketRes, stationRes]) => {
      if (!ticketRes.success) return message.error(ticketRes.message)
      setTicketSearchResult(ticketRes.data || {})
      state.stationList = stationRes?.data?.length ? stationRes.data : defaultStations
    })
    .finally(() => {
      state.pageLoading = false
    })
})

const exchangeCity = () => {
  const [fromStation, toStation] = [toRaw(headSearch.fromStation), toRaw(headSearch.toStation)]
  headSearch.fromStation = toStation
  headSearch.toStation = fromStation
}

const handleTrainClick = (trainId) => {
  state.loading = true
  fetchTrainStation({ trainId })
    .then((res) => {
      state.trainStationList = res.data || []
    })
    .finally(() => {
      state.loading = false
    })
}

const handleBook = (record) => {
  window.open(
    `buyTicket?trainNumber=${record.trainNumber}&&trainId=${record.trainId}&&${Object.entries(
      headSearch
    )
      .map((item) => {
        const value = item[1]
        return `${item[0]}=${item[0].includes('Date') ? value.format('YYYY-MM-DD') : value}`
      })
      .join('&&')}`
  )
}
</script>

<template>
  <div class="search-page-container">
    <Spin :spinning="state.pageLoading" tip="正在加载车票信息..." size="large" class="page-loading-spin">
      <div class="search-header-card">
        <div class="trip-type-group">
          <label
            v-for="item in tripTypeOptions"
            :key="item.value"
            class="radio-option"
            :class="{ active: headSearch.tripType === item.value }"
            @click="headSearch.tripType = item.value"
          >
            <span class="radio-dot"></span>
            <span>{{ item.label }}</span>
          </label>
        </div>

        <div class="station-section">
          <div class="station-block">
            <span class="label">出发地</span>
            <Select
              v-model:value="headSearch.fromStation"
              class="station-select"
              :show-arrow="false"
              :show-search="true"
              :bordered="false"
              :options="stationOptions"
              option-filter-prop="label"
            />
          </div>
          <div class="swap-icon-wrap" @click="exchangeCity">
            <SwapRightOutlined class="swap-icon" />
          </div>
          <div class="station-block">
            <span class="label">目的地</span>
            <Select
              v-model:value="headSearch.toStation"
              class="station-select"
              :show-arrow="false"
              :show-search="true"
              :bordered="false"
              :options="stationOptions"
              option-filter-prop="label"
            />
          </div>
        </div>

        <div class="date-section">
          <CalendarOutlined class="section-icon" />
          <div class="date-info">
            <span class="label">出发日</span>
            <DatePicker
              v-model:value="headSearch.departureDate"
              class="date-picker"
              :bordered="false"
              :show-arrow="false"
              :allow-clear="false"
              :disabled-date="disabledTicketDate"
              @change="handSubmit"
            />
          </div>
        </div>

        <div class="date-section return-section" :class="{ disabled: headSearch.tripType === 'single' }">
          <CalendarOutlined class="section-icon" />
          <div class="date-info">
            <span class="label">返程日</span>
            <DatePicker
              v-model:value="headSearch.returnDate"
              class="date-picker"
              :bordered="false"
              :show-arrow="false"
              :allow-clear="false"
              :disabled="headSearch.tripType === 'single'"
              :disabled-date="disabledTicketDate"
            />
          </div>
        </div>

        <div class="passenger-section">
          <UserOutlined class="section-icon" />
          <div class="passenger-info">
            <span class="label">票种</span>
            <Select
              v-model:value="headSearch.passengerType"
              class="passenger-select"
              :show-arrow="false"
              :bordered="false"
              :options="passengerOptions"
            />
          </div>
        </div>

        <Button type="primary" class="search-btn" :loading="state.searchLoading" @click="handSubmit">
          <SearchOutlined />
          <span>查询</span>
        </Button>
      </div>

      <div class="date-tabs-container">
        <div
          v-for="item in days"
          :key="item.format('MM-DD')"
          class="date-tab-item"
          :class="{ active: headSearch.departureDate.format('MM-DD') === item.format('MM-DD') }"
          @click="
            () => {
              headSearch.departureDate = item
              handSubmit()
            }
          "
        >
          <div class="date">{{ item.format('MM-DD') }}</div>
          <div class="week">{{ getWeekNumber(item.day()) }}</div>
        </div>
      </div>

      <div class="filters-card">
        <div class="filter-grid">
          <div class="filter-left">
            <div class="filter-row">
              <div class="filter-label">车次类型:</div>
              <div class="filter-options">
                <button
                  class="filter-all"
                  :class="{ active: headSearch.car_type.length === 0 }"
                  @click="headSearch.car_type = []"
                >
                  全部
                </button>
                <button
                  v-for="brand in trainBrandOptions"
                  :key="brand.code"
                  class="check-option"
                  :class="{ active: headSearch.car_type.includes(brand.code) }"
                  @click="toggleListValue(headSearch.car_type, brand.code)"
                >
                  <span class="check-box"></span>
                  <span>{{ brand.label }}</span>
                </button>
              </div>
            </div>

            <div class="filter-row">
              <div class="filter-label">出发车站:</div>
              <div class="filter-options">
                <button
                  class="filter-all"
                  :class="{ active: headSearch.departure.length === 0 }"
                  @click="headSearch.departure = []"
                >
                  全部
                </button>
                <button
                  v-for="item in currCityStations"
                  :key="item"
                  class="check-option"
                  :class="{ active: headSearch.departure.includes(item) }"
                  @click="toggleListValue(headSearch.departure, item)"
                >
                  <span class="check-box"></span>
                  <span>{{ item }}</span>
                </button>
              </div>
            </div>

            <div class="filter-row">
              <div class="filter-label">到达车站:</div>
              <div class="filter-options">
                <button
                  class="filter-all"
                  :class="{ active: headSearch.arrival.length === 0 }"
                  @click="headSearch.arrival = []"
                >
                  全部
                </button>
                <button
                  v-for="item in currArrivalStations"
                  :key="item"
                  class="check-option"
                  :class="{ active: headSearch.arrival.includes(item) }"
                  @click="toggleListValue(headSearch.arrival, item)"
                >
                  <span class="check-box"></span>
                  <span>{{ item }}</span>
                </button>
              </div>
            </div>

            <div class="filter-row">
              <div class="filter-label">车次席别:</div>
              <div class="filter-options">
                <button
                  class="filter-all"
                  :class="{ active: headSearch.seat.length === 0 }"
                  @click="headSearch.seat = []"
                >
                  全部
                </button>
                <button
                  v-for="seat in seatFilterOptions"
                  :key="seat.code"
                  class="check-option"
                  :class="{ active: headSearch.seat.includes(seat.code) }"
                  @click="toggleListValue(headSearch.seat, seat.code)"
                >
                  <span class="check-box"></span>
                  <span>{{ seat.label }}</span>
                </button>
              </div>
            </div>
          </div>

          <div class="filter-right">
            <span class="time-select-label">发车时间:</span>
            <Select
              v-model:value="headSearch.departureTimeRange"
              class="time-select"
              :options="carRangeTime.map((item) => ({ label: item.label, value: item.value }))"
            />
          </div>
        </div>
      </div>

      <div class="result-summary">
        <div class="route-summary">
          <strong>{{ summaryText }}</strong>
          <span>您可使用中转换乘功能，查询途中换乘一次的部分列车余票情况。</span>
        </div>
        <div class="quick-filters">
          <button class="check-option" :class="{ active: headSearch.showDiscount }" @click="headSearch.showDiscount = !headSearch.showDiscount">
            <span class="check-box"></span>
            <span>显示折扣车次</span>
          </button>
          <button class="check-option" :class="{ active: headSearch.showPoint }" @click="headSearch.showPoint = !headSearch.showPoint">
            <span class="check-box"></span>
            <span>显示积分兑换车次</span>
          </button>
          <button class="check-option" :class="{ active: headSearch.showBookable }" @click="headSearch.showBookable = !headSearch.showBookable">
            <span class="check-box"></span>
            <span>显示全部可预订车次</span>
          </button>
        </div>
      </div>

      <div class="train-list-container">
        <div v-for="train in state.trainList" :key="train.trainId" class="train-card">
          <div class="train-card-left">
            <div class="time-block">
              <div class="time">{{ train.departureTime }}</div>
              <div class="station">
                <span class="tag start" v-if="train.departureFlag">始</span>
                <span class="tag pass" v-else>过</span>
                {{ train.departure }}
              </div>
            </div>

            <div class="train-info-block">
              <div class="train-number" @click="handleTrainClick(train.trainId)">
                <Tooltip :get-popup-container="(node) => node.parentNode" placement="bottom" trigger="click">
                  {{ train.trainNumber }}
                  <template #title>
                    <Table
                      :columns="cardInfoColumns"
                      :data-source="state.trainStationList"
                      :pagination="false"
                      :loading="state.loading"
                    />
                  </template>
                </Tooltip>
              </div>
              <div class="duration-line">
                <span class="duration-text"><ClockCircleOutlined /> {{ train.duration }}</span>
                <div class="line-graphic">
                  <div class="dot"></div>
                  <div class="line"></div>
                  <div class="dot"></div>
                </div>
                <span class="arrival-day">{{ isNextDay(train.departureTime, train.arrivalTime) ? '次日到达' : '当日到达' }}</span>
              </div>
            </div>

            <div class="time-block right-align">
              <div class="time">{{ train.arrivalTime }}</div>
              <div class="station">
                <span class="tag end" v-if="train.arrivalFlag">终</span>
                <span class="tag pass" v-else>过</span>
                {{ train.arrival }}
              </div>
            </div>
          </div>

          <div class="train-card-right">
            <div v-for="seat in getAvailableSeats(train.seatClassList)" :key="seat.type" class="seat-block">
              <div class="seat-info">
                <span class="seat-name">{{ getSeatName(seat.type) }}</span>
                <span class="seat-price">￥{{ seat.price }}</span>
              </div>
              <div class="seat-action">
                <span class="seat-status" :class="{ 'has-ticket': seat.quantity > 0, 'no-ticket': seat.quantity === 0 }">
                  {{ seat.quantity > 0 ? `余${seat.quantity}张` : '无票' }}
                </span>
                <Button class="book-btn" @click="handleBook(train)" :disabled="seat.quantity === 0">预订</Button>
              </div>
            </div>
          </div>
        </div>
        <div v-if="state.trainList?.length === 0 && !state.pageLoading && !state.searchLoading" class="empty-state">
          暂无符合条件的车次
        </div>
      </div>
    </Spin>
  </div>
</template>

<style lang="scss" scoped>
.search-page-container {
  background-color: transparent;
  min-height: calc(100vh - 98px);
  padding: 0 0 20px;
  max-width: 1160px;
  margin: 0 auto;
}

.page-loading-spin {
  min-height: 70vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding-top: 10vh;
  :deep(.ant-spin-container) {
    min-height: 70vh;
  }
}

.search-header-card {
  background: #fff;
  border-radius: 10px;
  padding: 14px 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.04);
  margin-bottom: 10px;
  border: 1px solid rgba(238, 240, 244, 0.8);
  position: sticky;
  top: 76px;
  z-index: 8;
  gap: 12px;
}

.trip-type-group {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-right: 16px;
  border-right: 1px solid #d8e4f0;
  flex-shrink: 0;
}

.radio-option,
.check-option {
  appearance: none;
  border: 0;
  background: transparent;
  color: #333;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 0;
  font-size: 13px;
  line-height: 22px;
  cursor: pointer;
  white-space: nowrap;
}

.radio-dot,
.check-box {
  width: 14px;
  height: 14px;
  border: 1px solid #aeb7c2;
  background: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 14px;
}

.radio-dot {
  border-radius: 50%;
}

.radio-option.active .radio-dot::after {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #111;
}

.check-option.active .check-box {
  border-color: #111;
  background: #111;
}

.check-option.active .check-box::after {
  content: '';
  width: 6px;
  height: 9px;
  border: solid #fff;
  border-width: 0 2px 2px 0;
  transform: rotate(45deg) translateY(-1px);
}

.station-section {
  display: flex;
  align-items: center;
  background: #f8f9fb;
  border-radius: 8px;
  padding: 8px 16px;
  min-height: 62px;
  border: 1px solid transparent;
  transition: border-color 0.18s ease, background-color 0.18s ease;
  &:hover {
    background: #f6f7f9;
    border-color: #eef0f4;
  }
}

.station-block,
.date-info,
.passenger-info {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 42px;
  .label {
    color: #999;
    font-size: 13px;
    line-height: 18px;
    margin-bottom: 3px;
  }
}

.station-select {
  width: 110px;
  :deep(.ant-select-selector) {
    height: 26px !important;
    padding: 0 !important;
    background: transparent !important;
    display: flex;
    align-items: center;
    .ant-select-selection-item {
      color: #333;
      font-size: 24px !important;
      font-weight: 650;
      line-height: 26px !important;
    }
  }
}

.swap-icon-wrap {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 12px;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  transition: transform 0.18s ease, box-shadow 0.18s ease;
  .swap-icon {
    color: #999;
    font-size: 15px;
  }
  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 6px 16px rgba(15, 23, 42, 0.08);
    .swap-icon {
      color: #333;
    }
  }
}

.date-section,
.passenger-section {
  display: flex;
  align-items: center;
  background: #f8f9fb;
  border-radius: 8px;
  padding: 8px 16px;
  min-height: 62px;
  border: 1px solid transparent;
  transition: border-color 0.18s ease, background-color 0.18s ease;
  &:hover {
    background: #f6f7f9;
    border-color: #eef0f4;
  }
  .section-icon {
    color: #999;
    font-size: 18px;
    margin-right: 10px;
  }
}

.return-section.disabled {
  opacity: 0.5;
}

.date-picker {
  width: 134px;
  padding: 0 !important;
  background: transparent !important;
  :deep(.ant-picker-input) {
    align-items: center;
  }
  :deep(input) {
    color: #333;
    cursor: pointer;
    font-size: 18px !important;
    font-weight: 650;
    line-height: 26px;
  }
}

.passenger-select {
  width: 74px;
  :deep(.ant-select-selector) {
    height: 26px !important;
    padding: 0 !important;
    background: transparent !important;
    display: flex;
    align-items: center;
    .ant-select-selection-item {
      color: #333;
      font-size: 18px !important;
      font-weight: 650;
      line-height: 26px !important;
    }
  }
}

.search-btn {
  background: #000;
  color: #fff;
  border-radius: 8px;
  height: 62px;
  min-width: 116px;
  padding: 0 24px;
  font-size: 17px;
  font-weight: 650;
  border: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  &:hover,
  &:focus {
    background: #333;
    color: #fff;
    transform: translateY(-1px);
  }
}

.date-tabs-container {
  display: flex;
  gap: 0;
  margin-bottom: 0;
  overflow-x: auto;
  border: 1px solid #d9e6f2;
  border-bottom: 0;
  background: #f6f8fa;
  &::-webkit-scrollbar {
    height: 6px;
  }
  &::-webkit-scrollbar-thumb {
    background: #d5dbe3;
    border-radius: 3px;
  }
}

.date-tab-item {
  min-width: 76px;
  height: 44px;
  background: linear-gradient(180deg, #fff 0%, #f3f4f6 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  cursor: pointer;
  border-right: 1px solid #d5dbe3;
  transition: background-color 0.18s ease, color 0.18s ease;
  flex-shrink: 0;
  .week,
  .date {
    font-size: 13px;
    font-weight: 600;
    color: #333;
  }
  &.active {
    background: #fff;
    box-shadow: inset 0 2px 0 #111;
    .week,
    .date {
      color: #111;
    }
  }
}

.filters-card {
  background: #fff;
  border: 1px solid #d9e6f2;
  border-top: 0;
  border-radius: 0 0 8px 8px;
  padding: 10px 16px 12px;
  margin-bottom: 12px;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.018);
}

.filter-grid {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.filter-left {
  display: flex;
  flex-direction: column;
  gap: 7px;
  min-width: 0;
}

.filter-right {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  flex-shrink: 0;
}

.time-select-label {
  color: #333;
  font-size: 13px;
  line-height: 30px;
  white-space: nowrap;
}

.time-select {
  width: 140px;
}

.filter-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.filter-label {
  width: 70px;
  color: #111;
  font-size: 13px;
  font-weight: 650;
  line-height: 24px;
  flex-shrink: 0;
}

.filter-options {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 16px;
  min-width: 0;
}

.filter-all {
  appearance: none;
  border: 1px solid #d9e6f2;
  background: #f6f8fb;
  color: #6b7280;
  min-width: 40px;
  height: 22px;
  padding: 0 8px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  &.active {
    background: #111;
    border-color: #111;
    color: #fff;
  }
}

.result-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 0 0 10px;
}

.route-summary {
  display: flex;
  align-items: center;
  gap: 14px;
  color: #333;
  font-size: 13px;
  min-width: 0;
  strong {
    color: #111;
    font-size: 15px;
    white-space: nowrap;
  }
  span {
    color: #6b7280;
  }
}

.quick-filters {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.train-list-container {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.train-card {
  background: #fff;
  border-radius: 10px;
  padding: 14px 18px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.018);
  border: 1px solid rgba(238, 240, 244, 0.86);
  transition: box-shadow 0.18s ease, border-color 0.18s ease, transform 0.18s ease;
  &:hover {
    border-color: #dfe3ea;
    box-shadow: 0 12px 30px rgba(15, 23, 42, 0.06);
    transform: translateY(-1px);
  }
}

.train-card-left {
  display: flex;
  align-items: center;
  gap: 26px;
  flex: 1;
  min-width: 420px;
}

.time-block {
  display: flex;
  flex-direction: column;
  &.right-align {
    align-items: flex-end;
  }
  .time {
    font-size: 25px;
    font-weight: 650;
    color: #333;
    line-height: 1;
    margin-bottom: 5px;
  }
  .station {
    font-size: 13px;
    color: #666;
    display: flex;
    align-items: center;
    gap: 6px;
  }
  .tag {
    font-size: 10px;
    padding: 2px 4px;
    border-radius: 4px;
    color: #fff;
    line-height: 1;
    &.start {
      background: #ff9900;
    }
    &.end {
      background: #52c41a;
    }
    &.pass {
      background: #1890ff;
    }
  }
}

.train-info-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
  max-width: 170px;
  .train-number {
    font-size: 14px;
    color: #6b7280;
    cursor: pointer;
    margin-bottom: 4px;
    border-bottom: 1px dashed #c9ced6;
    &:hover {
      color: #333;
      border-color: #333;
    }
  }
  .duration-line {
    display: flex;
    flex-direction: column;
    align-items: center;
    width: 100%;
    .duration-text {
      font-size: 12px;
      color: #999;
      margin-bottom: 4px;
      display: flex;
      align-items: center;
      gap: 4px;
    }
    .line-graphic {
      width: 100%;
      display: flex;
      align-items: center;
      margin-bottom: 4px;
      .dot {
        width: 6px;
        height: 6px;
        border-radius: 50%;
        border: 1px solid #d9d9d9;
        background: #fff;
      }
      .line {
        flex: 1;
        height: 1px;
        background: #d9d9d9;
      }
    }
    .arrival-day {
      font-size: 12px;
      color: #999;
    }
  }
}

.train-card-right {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: nowrap;
  justify-content: flex-end;
  max-width: 56%;
  overflow-x: auto;
  padding-bottom: 1px;
}

.seat-block {
  background: #f8f9fb;
  border-radius: 8px;
  padding: 8px 10px;
  min-width: 132px;
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 10px;
  border: 1px solid transparent;
  transition: border-color 0.18s ease, background-color 0.18s ease;
  &:hover {
    background: #f6f7f9;
    border-color: #eef0f4;
  }
}

.seat-info {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  line-height: 1.35;
  .seat-name {
    font-size: 12px;
    color: #666;
  }
  .seat-price {
    font-size: 14px;
    font-weight: 650;
    color: #333;
  }
}

.seat-action {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: center;
  .seat-status {
    font-size: 12px;
    font-weight: 600;
    &.has-ticket {
      color: #52c41a;
    }
    &.no-ticket {
      color: #ff9900;
    }
  }
  .book-btn {
    background: #000;
    color: #fff;
    border: none;
    border-radius: 5px;
    height: 24px;
    padding: 0 12px;
    font-size: 12px;
    &:hover {
      background: #333;
      color: #fff;
      transform: none;
    }
    &:disabled {
      background: #ccc;
      cursor: not-allowed;
    }
  }
}

.empty-state {
  text-align: center;
  padding: 40px;
  color: #999;
}

@media (max-width: 1180px) {
  .search-header-card,
  .filter-grid,
  .result-summary {
    flex-wrap: wrap;
  }
  .search-header-card {
    position: static;
  }
  .train-card {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }
  .train-card-right {
    max-width: none;
    justify-content: flex-start;
    flex-wrap: wrap;
  }
}

@media (max-width: 760px) {
  .search-page-container {
    padding-top: 0;
  }
  .search-header-card,
  .filters-card,
  .train-card {
    padding: 12px;
  }
  .trip-type-group {
    width: 100%;
    flex-direction: row;
    border-right: 0;
    border-bottom: 1px solid #eef0f4;
    padding: 0 0 10px;
  }
  .station-section,
  .date-section,
  .passenger-section,
  .search-btn,
  .filter-right {
    width: 100%;
  }
  .station-section {
    justify-content: space-between;
  }
  .filter-row {
    flex-direction: column;
    gap: 4px;
  }
  .filter-label {
    width: auto;
  }
  .quick-filters {
    flex-wrap: wrap;
  }
  .train-card-left {
    gap: 14px;
    align-items: flex-start;
    min-width: 0;
  }
  .time-block .time {
    font-size: 21px;
  }
}
</style>
