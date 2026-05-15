<script setup>
import {reactive, toRaw, ref, watch, onMounted} from 'vue'
import {
  Select,
  Button,
  DatePicker,
  Table,
  Tooltip,
  Spin,
  message
} from 'ant-design-vue'
import {
  SwapRightOutlined,
  CalendarOutlined,
  UserOutlined,
  SearchOutlined,
  SwapOutlined,
  ClockCircleOutlined
} from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import {getWeekNumber, getTicketNumber} from '@/utils'
import {
  fetchTicketSearch,
  fetchStationAll,
  fetchTrainStation
} from '@/service/index'
import {SEAT_CLASS_TYPE_LIST, TRAIN_BRAND_LIST, TRAIN_TAG} from '@/constants'

const carRangeTime = [
  {value: 0, label: '00:00-24:00'},
  {value: 1, label: '00:00-06:00'},
  {value: 2, label: '06:00-12:00'},
  {value: 3, label: '12:00-18:00'},
  {value: 4, label: '18:00-24:00'}
]

const headSearch = reactive({
  fromStation: 'BJP',
  toStation: 'HZH',
  departureDate: dayjs(),
  arrival_date: '',
  car_type: [],
  departure_stations: [],
  arrival_stations: [],
  departure: [],
  arrival: [],
  seat: [],
  passengerType: 0
})

const state = reactive({
  seatClassTypeListSelect: null,
  trainBrandListSelect: null,
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

watch(headSearch, (newValue) => {
  state.trainList = rowState.rawTrainList
  if (newValue?.car_type?.length) {
    state.trainList = state.trainList.filter((item) => {
      const list = item.trainBrand?.split(',')
      let hasInculude = false
      for (let i of list) {
        if (newValue?.car_type?.includes(i * 1)) {
          hasInculude = true
        }
      }
      return hasInculude
    })
  }
  if (newValue?.departure?.length) {
    state.trainList = state.trainList.filter((item) => {
      return newValue?.departure?.includes(item.departure)
    })
  }
  if (newValue?.arrival?.length) {
    state.trainList = state.trainList.filter((item) => {
      return newValue?.arrival?.includes(item.arrival)
    })
  }
  if (newValue?.seat?.length) {
    state.trainList = state.trainList.filter((item) => {
      const list =
          item.seatClassList?.filter(
              (item) => newValue.seat.includes(item.type) && item.quantity
          ) ?? []
      return list.length
    })
  }
})

const currCityStations = ref([])
const currArrivalStations = ref([])

const cardInfoColumns = [
  { title: '站序', dataIndex: 'sequence', key: 'sequence' },
  { title: '站名', dataIndex: 'departure', key: 'departure' },
  { title: '到站时间', dataIndex: 'arrivalTime', key: 'arrivalTime' },
  { title: '出发时间', dataIndex: 'departureTime', key: 'departureTime' },
  { title: '停留时间', dataIndex: 'stopoverTime', key: 'stopoverTime' }
]

const days = new Array(15)
    .fill(',')
    .map((item, index) => dayjs().add(index, 'days'))

const handSubmit = () => {
  state.searchLoading = true
  const {fromStation, toStation, departure, arrival, departureDate} = toRaw(headSearch)
  fetchTicketSearch({
    fromStation,
    toStation,
    departure: departure[0],
    arrival: arrival[0],
    departureDate: departureDate.format('YYYY-MM-DD')
  }).then((res) => {
    if (!res.success) return message.error(res.message)
    if (res.data.trainList) {
      state.trainList = res.data.trainList?.map((item) => ({
        ...item,
        key: item.trainId
      }))
      rowState.rawTrainList = res.data.trainList?.map((item) => ({
        ...item,
        key: item.trainId
      }))
    }
    state.trainBrandListSelect = res.data.trainBrandList
    state.seatClassTypeListSelect = res.data.seatClassTypeList
    currCityStations.value = res.data.departureStationList
    currArrivalStations.value = res.data.arrivalStationList
  }).finally(() => {
    state.searchLoading = false
  })
}

onMounted(() => {
  state.pageLoading = true
  Promise.all([
    fetchTicketSearch({
      fromStation: headSearch.fromStation,
      toStation: headSearch.toStation,
      departureDate: dayjs(new Date()).format('YYYY-MM-DD')
    }),
    fetchStationAll()
  ]).then(([ticketRes, stationRes]) => {
    if (!ticketRes.success) return message.error(ticketRes.message)
    if (ticketRes.data.trainList) {
      state.trainList = ticketRes.data.trainList?.map((item) => ({
        ...item,
        key: item.trainId
      }))
      rowState.rawTrainList = ticketRes.data.trainList?.map((item) => ({
        ...item,
        key: item.trainId
      }))
    }
    state.trainBrandListSelect = ticketRes.data.trainBrandList
    state.seatClassTypeListSelect = ticketRes.data.seatClassTypeList
    currCityStations.value = ticketRes.data.departureStationList
    currArrivalStations.value = ticketRes.data.arrivalStationList
    state.stationList = stationRes.data
  }).finally(() => {
    state.pageLoading = false
  })
})

const exchangeCity = () => {
  const [a, b] = [toRaw(headSearch.fromStation), toRaw(headSearch.toStation)]
  headSearch.fromStation = b
  headSearch.toStation = a
}

const handleTrainClick = (trainId) => {
  fetchTrainStation({trainId}).then((res) => {
    state.trainStationList = res.data
    state.loading = false
  })
}

const handleBook = (record) => {
  window.open(
      `buyTicket?trainNumber=${record.trainNumber}&&trainId=${
          record.trainId
      }&&${Object.entries(headSearch)
          ?.map((item) => {
            return `${item[0]}=${
                item[0] === 'departureDate' ? item[1].format('YYYY-MM-DD') : item[1]
            }`
          })
          .join('&&')}`
  )
}

const getAvailableSeats = (seatClassList) => {
  if (!seatClassList) return [];
  return seatClassList.filter(item => item.price !== undefined && item.price !== null);
}

const getSeatName = (type) => {
  const seat = SEAT_CLASS_TYPE_LIST.find(item => item.code === type);
  return seat ? seat.label : '其他';
}

const isNextDay = (dep, arr) => {
  if (!dep || !arr) return false;
  return arr < dep; 
}
</script>
<template>
  <div class="search-page-container">
    <Spin :spinning="state.pageLoading" tip="正在加载车票信息..." size="large" class="page-loading-spin">
      
      <!-- Top Search Card -->
      <div class="search-header-card">
        <div class="station-section">
          <div class="station-block">
            <span class="label">出发站</span>
            <Select
              v-model:value="headSearch.fromStation"
              class="station-select"
              :show-arrow="false"
              :show-search="true"
              :bordered="false"
              :options="state.stationList.map(item => ({ label: item.name, value: item.code }))"
            ></Select>
          </div>
          <div class="swap-icon-wrap" @click="exchangeCity">
            <SwapRightOutlined class="swap-icon" />
          </div>
          <div class="station-block">
            <span class="label">到达站</span>
            <Select
              v-model:value="headSearch.toStation"
              class="station-select"
              :show-arrow="false"
              :show-search="true"
              :bordered="false"
              :options="state.stationList.map(item => ({ label: item.name, value: item.code }))"
            ></Select>
          </div>
        </div>

        <div class="date-section">
          <CalendarOutlined class="section-icon" />
          <div class="date-info">
            <span class="label">出发日期</span>
            <DatePicker
              v-model:value="headSearch.departureDate"
              class="date-picker"
              :bordered="false"
              :show-arrow="false"
              :allow-clear="false"
            />
          </div>
        </div>

        <div class="passenger-section">
          <UserOutlined class="section-icon" />
          <div class="passenger-info">
            <span class="label">乘客 <SwapOutlined style="font-size: 10px; margin-left: 2px" /></span>
            <Select
              v-model:value="headSearch.passengerType"
              class="passenger-select"
              :show-arrow="false"
              :bordered="false"
              :options="[{label: '成人', value: 0}, {label: '学生', value: 1}]"
            ></Select>
          </div>
        </div>

        <Button type="primary" class="search-btn" :loading="state.searchLoading" @click="handSubmit">
          <SearchOutlined /> 查询
        </Button>
      </div>

      <!-- Date Tabs -->
      <div class="date-tabs-container">
        <div 
          v-for="item in days" 
          :key="item.format('MM-DD')"
          class="date-tab-item"
          :class="{ 'active': headSearch.departureDate.format('MM-DD') === item.format('MM-DD') }"
          @click="() => {
            const year = dayjs().format('YYYY')
            const date = year + '-' + item.format('MM-DD')
            headSearch.departureDate = dayjs(date)
            handSubmit()
          }"
        >
          <div class="week">{{ getWeekNumber(item.day()) }}</div>
          <div class="date">{{ item.format('MM-DD') }}</div>
        </div>
      </div>

      <!-- Filters -->
      <div class="filters-card">
        <div class="filter-title">共找到 {{ state.trainList?.length || 0 }} 个车次</div>
        
        <div class="filter-row">
          <div class="filter-label">车次类型:</div>
          <div class="filter-options">
            <div 
              class="filter-pill"
              :class="{ 'active': headSearch.car_type.length === state.trainBrandListSelect?.length || headSearch.car_type.length === 0 }"
              @click="() => {
                if (headSearch.car_type.length !== state.trainBrandListSelect?.length) {
                  headSearch.car_type = state.trainBrandListSelect?.map(item => TRAIN_BRAND_LIST.find(i => i.code === item)?.code) || []
                } else {
                  headSearch.car_type = []
                }
              }"
            >全部</div>
            <div 
              v-for="seatItem in state.trainBrandListSelect" 
              :key="seatItem"
              class="filter-pill"
              :class="{ 'active': headSearch.car_type.includes(TRAIN_BRAND_LIST.find(i => i.code === seatItem)?.code) }"
              @click="() => {
                const code = TRAIN_BRAND_LIST.find(i => i.code === seatItem)?.code;
                const idx = headSearch.car_type.indexOf(code);
                if (idx > -1) {
                  headSearch.car_type.splice(idx, 1);
                } else {
                  headSearch.car_type.push(code);
                }
              }"
            >
              {{ TRAIN_BRAND_LIST.find(i => i.code === seatItem)?.label }}
            </div>
          </div>
        </div>

        <div class="filter-row">
          <div class="filter-label">出发时段:</div>
          <div class="filter-options">
            <div class="filter-pill active">00:00-24:00</div>
            <div class="filter-pill" v-for="time in carRangeTime.slice(1)" :key="time.value">{{ time.label }}</div>
          </div>
        </div>

        <div class="filter-row">
          <div class="filter-label">出发车站:</div>
          <div class="filter-options">
            <div 
              class="filter-pill"
              :class="{ 'active': headSearch.departure.length === currCityStations?.length || headSearch.departure.length === 0 }"
              @click="() => {
                if (headSearch.departure.length !== currCityStations?.length) {
                  headSearch.departure = [...currCityStations]
                } else {
                  headSearch.departure = []
                }
              }"
            >全部</div>
            <div 
              v-for="item in currCityStations" 
              :key="item"
              class="filter-pill"
              :class="{ 'active': headSearch.departure.includes(item) }"
              @click="() => {
                const idx = headSearch.departure.indexOf(item);
                if (idx > -1) {
                  headSearch.departure.splice(idx, 1);
                } else {
                  headSearch.departure.push(item);
                }
              }"
            >
              {{ item }}
            </div>
          </div>
        </div>

        <div class="filter-row">
          <div class="filter-label">到达车站:</div>
          <div class="filter-options">
            <div 
              class="filter-pill"
              :class="{ 'active': headSearch.arrival.length === currArrivalStations?.length || headSearch.arrival.length === 0 }"
              @click="() => {
                if (headSearch.arrival.length !== currArrivalStations?.length) {
                  headSearch.arrival = [...currArrivalStations]
                } else {
                  headSearch.arrival = []
                }
              }"
            >全部</div>
            <div 
              v-for="item in currArrivalStations" 
              :key="item"
              class="filter-pill"
              :class="{ 'active': headSearch.arrival.includes(item) }"
              @click="() => {
                const idx = headSearch.arrival.indexOf(item);
                if (idx > -1) {
                  headSearch.arrival.splice(idx, 1);
                } else {
                  headSearch.arrival.push(item);
                }
              }"
            >
              {{ item }}
            </div>
          </div>
        </div>
      </div>

      <!-- Train List -->
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
                    <Table :columns="cardInfoColumns" :data-source="state.trainStationList" :pagination="false" :loading="state.loading"></Table>
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
            <div 
              v-for="seat in getAvailableSeats(train.seatClassList)" 
              :key="seat.type"
              class="seat-block"
            >
              <div class="seat-info">
                <span class="seat-name">{{ getSeatName(seat.type) }}</span>
                <span class="seat-price">¥{{ seat.price }}</span>
              </div>
              <div class="seat-action">
                <span class="seat-status" :class="{'has-ticket': seat.quantity > 0, 'no-ticket': seat.quantity === 0}">
                  {{ seat.quantity > 0 ? `余${seat.quantity}张` : '候补' }}
                </span>
                <Button class="book-btn" @click="handleBook(train)" :disabled="seat.quantity === 0">预订</Button>
              </div>
            </div>
          </div>
        </div>
        <div v-if="state.trainList?.length === 0 && !state.pageLoading && !state.searchLoading" style="text-align: center; padding: 40px; color: #999;">
          没有找到符合条件的车次
        </div>
      </div>

    </Spin>
  </div>
</template>
<style lang="scss" scoped>
.search-page-container {
  background-color: transparent;
  min-height: calc(100vh - 98px);
  padding: 4px 0 24px;
  max-width: 1200px;
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
  border-radius: 12px;
  padding: 22px 28px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.05);
  margin-bottom: 14px;
  border: 1px solid rgba(238, 240, 244, 0.8);
  position: sticky;
  top: 76px;
  z-index: 8;
  gap: 14px;
}

.station-section {
  display: flex;
  align-items: center;
  background: #f8f9fb;
  border-radius: 10px;
  padding: 12px 24px;
  border: 1px solid transparent;
  transition: border-color 0.18s ease, background-color 0.18s ease;
  &:hover {
    background: #f6f7f9;
    border-color: #eef0f4;
  }
}

.station-block {
  display: flex;
  flex-direction: column;
  .label {
    font-size: 12px;
    color: #999;
    margin-bottom: 4px;
    padding-left: 11px;
  }
}

.station-select {
  width: 120px;
  :deep(.ant-select-selector) {
    padding: 0 !important;
    background: transparent !important;
    .ant-select-selection-item {
      font-size: 23px !important;
      font-weight: 650;
      color: #333;
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
  margin: 0 16px;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
  transition: transform 0.18s ease, box-shadow 0.18s ease;
  .swap-icon {
    color: #999;
    font-size: 16px;
  }
  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 6px 16px rgba(15, 23, 42, 0.08);
    .swap-icon {
      color: #333;
    }
  }
}

.date-section, .passenger-section {
  display: flex;
  align-items: center;
  background: #f8f9fb;
  border-radius: 10px;
  padding: 12px 24px;
  height: 72px;
  border: 1px solid transparent;
  transition: border-color 0.18s ease, background-color 0.18s ease;
  &:hover {
    background: #f6f7f9;
    border-color: #eef0f4;
  }
  .section-icon {
    font-size: 20px;
    color: #999;
    margin-right: 12px;
  }
}

.date-info, .passenger-info {
  display: flex;
  flex-direction: column;
  .label {
    font-size: 12px;
    color: #999;
    margin-bottom: 4px;
    padding-left: 11px;
  }
}

.date-picker {
  width: 140px;
  padding: 0 !important;
  background: transparent !important;
  :deep(input) {
    font-size: 20px !important;
    font-weight: bold;
    color: #333;
    cursor: pointer;
  }
}

.passenger-select {
  width: 100px;
  :deep(.ant-select-selector) {
    padding: 0 !important;
    background: transparent !important;
    .ant-select-selection-item {
      font-size: 20px !important;
      font-weight: bold;
      color: #333;
    }
  }
}

.search-btn {
  background: #000;
  color: #fff;
  border-radius: 10px;
  height: 72px;
  padding: 0 36px;
  font-size: 17px;
  font-weight: 600;
  border: none;
  display: flex;
  align-items: center;
  gap: 8px;
  &:hover, &:focus {
    background: #333;
    color: #fff;
    transform: translateY(-1px);
  }
}

.date-tabs-container {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
  overflow-x: auto;
  padding: 10px 2px 12px;
  &::-webkit-scrollbar {
    height: 6px;
  }
  &::-webkit-scrollbar-thumb {
    background: #e0e0e0;
    border-radius: 3px;
  }
}

.date-tab-item {
  min-width: 76px;
  height: 64px;
  background: #fff;
  border-radius: 10px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  border: 1px solid #eee;
  transition: background-color 0.18s ease, border-color 0.18s ease, transform 0.18s ease;
  flex-shrink: 0;
  
  .week {
    font-size: 12px;
    color: #999;
    margin-bottom: 4px;
  }
  .date {
    font-size: 16px;
    font-weight: 600;
    color: #333;
  }
  
  &.active {
    background: #000;
    border-color: #000;
    .week, .date {
      color: #fff;
    }
  }
  
  &:hover:not(.active) {
    border-color: #c9ced6;
    transform: translateY(-1px);
  }
}

.filters-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px 28px;
  margin-bottom: 14px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.02);
  border: 1px solid rgba(238, 240, 244, 0.8);
}

.filter-title {
  font-size: 18px;
  font-weight: 650;
  color: #333;
  margin-bottom: 18px;
  margin-top: 0;
}

.filter-row {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
  &:last-child {
    margin-bottom: 0;
  }
}

.filter-label {
  width: 96px;
  color: #6b7280;
  font-size: 14px;
  flex-shrink: 0;
}

.filter-options {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.filter-pill {
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 14px;
  color: #666;
  background: #fff;
  border: 1px solid #eee;
  cursor: pointer;
  transition: all 0.2s;
  user-select: none;
  
  &.active {
    background: #000;
    color: #fff;
    border-color: #000;
  }
  
  &:hover:not(.active) {
    border-color: #c9ced6;
    color: #333;
  }
}

.train-list-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.train-card {
  background: #fff;
  border-radius: 12px;
  padding: 22px 28px;
  display: flex;
  justify-content: space-between;
  align-items: stretch;
  box-shadow: 0 4px 16px rgba(0,0,0,0.02);
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
  gap: 40px;
  flex: 1;
}

.time-block {
  display: flex;
  flex-direction: column;
  &.right-align {
    align-items: flex-end;
  }
  
  .time {
    font-size: 31px;
    font-weight: 650;
    color: #333;
    line-height: 1;
    margin-bottom: 8px;
  }
  
  .station {
    font-size: 14px;
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
    
    &.start { background: #ff9900; }
    &.end { background: #52c41a; }
    &.pass { background: #1890ff; }
  }
}

.train-info-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
  max-width: 200px;
  
  .train-number {
    font-size: 14px;
    color: #6b7280;
    cursor: pointer;
    margin-bottom: 4px;
    border-bottom: 1px dashed #c9ced6;
    &:hover { color: #333; border-color: #333; }
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
  gap: 16px;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  max-width: 50%;
}

.seat-block {
  background: #f8f9fb;
  border-radius: 10px;
  padding: 12px 16px;
  min-width: 140px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  border: 1px solid transparent;
  transition: border-color 0.18s ease, background-color 0.18s ease;
  &:hover {
    background: #f6f7f9;
    border-color: #eef0f4;
  }
}

.seat-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  
  .seat-name {
    font-size: 14px;
    color: #666;
  }
  .seat-price {
    font-size: 16px;
    font-weight: 650;
    color: #333;
  }
}

.seat-action {
  display: flex;
  justify-content: space-between;
  align-items: center;
  
  .seat-status {
    font-size: 12px;
    font-weight: 600;
    &.has-ticket { color: #52c41a; }
    &.no-ticket { color: #ff9900; }
  }
  
  .book-btn {
    background: #000;
    color: #fff;
    border: none;
    border-radius: 6px;
    height: 28px;
    padding: 0 16px;
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

@media (max-width: 1180px) {
  .search-header-card {
    flex-wrap: wrap;
    position: static;
  }
  .train-card {
    flex-direction: column;
    gap: 18px;
  }
  .train-card-right {
    max-width: none;
    justify-content: flex-start;
  }
}

@media (max-width: 760px) {
  .search-page-container {
    padding-top: 0;
  }
  .search-header-card,
  .filters-card,
  .train-card {
    padding: 18px;
  }
  .station-section,
  .date-section,
  .passenger-section,
  .search-btn {
    width: 100%;
  }
  .station-section {
    justify-content: space-between;
  }
  .train-card-left {
    gap: 18px;
    align-items: flex-start;
  }
  .time-block .time {
    font-size: 26px;
  }
}
</style>
