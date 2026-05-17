<template>
  <div class="card-container">
    <Tabs type="card" v-model:active-key="state.activeKey">
      <TabPane :key="0" tab="未完成订单"></TabPane>
      <TabPane :key="1" tab="未出行订单"></TabPane>
      <TabPane :key="2" tab="历史订单"></TabPane>
    </Tabs>
  </div>
  <Card class="order-list-card" :bordered="false" :style="{ padding: '0 10px' }">
    <CheckboxGroup
      :style="{ width: '100%' }"
      v-model:value="state.checkList"
      @change="
        (e) => {
          state.checkList = e
        }
      "
    >
      <Table
        class="order-table"
        :columns="state.columns"
        :data-source="state.dataSource"
        :pagination="false"
        :loading="state.loading"
        :bordered="true"
        row-key="id"
        table-layout="fixed"
        :scroll="{ x: 1280 }"
      >
        <template #id="{ text, record }">
          <div :style="{ display: 'flex', flexDirection: 'column', gap: '8px' }">
            <div :style="{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }">
              <span :style="{ fontWeight: 500 }">{{ record?.realName }}</span>
              <Button
                v-if="state.activeKey !== 0"
                type="link"
                size="small"
                :style="{ padding: '0', fontSize: '13px' }"
              >
                <template #icon><PrinterOutlined /></template>
                打印信息单
              </Button>
            </div>
            <div :style="{ color: '#6b7280', fontSize: '13px' }">
              {{
                ID_CARD_TYPE.find((item) => item.value === record?.idType)
                  ?.label
              }}
            </div>
          </div>
        </template>
        <template #seatType="{ text, record }">
          <div>
            {{
              SEAT_CLASS_TYPE_LIST.find(
                (item) => item.code === record?.seatType
              )?.label
            }}
          </div>
          <div>
            <span>{{ record?.carriageNumber }}</span>
            <span>车</span>
            <span>{{ record?.seatNumber }}</span>
            <span>号</span>
          </div>
        </template>
        <template #amount="{ text, record }">
          <div>
            {{
              TICKET_TYPE_LIST.find((item) => item.value === record?.ticketType)
                ?.label
            }}
          </div>
          <div :style="{ color: 'orange' }">￥{{ record?.amount / 100 }}</div>
        </template>

        <template #status="{ text, record }">
          <div>
            {{
              TICKET_STATUS_LIST.find((item) => item.value === record?.status)
                ?.label ?? '--'
            }}
          </div>
          <div v-if="record?.status === 10">
            <Button
              type="link"
              @click="
                () => {
                  state.visible = true
                  state.currentOrder = record?.orderSn
                }
              "
              >退票</Button
            >
          </div>
        </template>
        <!-- <template #summary v-if="state.activeKey !== 0">
          <TableSummary :fixed="fixedTop ? 'top' : 'bottom'">
            <TableSummaryRow>
              <TableSummaryCell :index="0" :col-span="24">
                <div>
                  <Checkbox
                    v-model:checked="state.checkedAll"
                    @change="onCheckAllChange"
                    :indeterminate="state.indeterminate"
                    >全选</Checkbox
                  >
                </div>
              </TableSummaryCell>
            </TableSummaryRow>
          </TableSummary>
        </template> -->
      </Table>
    </CheckboxGroup>
    <div
      :style="{
        width: '100%',
        marginTop: '20px',
        display: 'flex',
        justifyContent: 'end'
      }"
    >
      <Pagination
        :show-total="(total) => `总共 ${state.data?.total} 条`"
        :current="state.current"
        :size="state.size"
        :total="state.data?.total"
        show-size-changer
        @change="handlePage"
      ></Pagination>
    </div>
    <div class="tips-txt">
      <div class="title">温馨提示：</div>
      <template v-if="state.activeKey === 0">
        <p>1. 席位已锁定，请在指定时间内完成网上支付。</p>
        <p>2. 逾期未支付，系统将取消本次交易。</p>
        <p>3. 在完成支付或取消订单之前，您将无法购买其他车票</p>
      </template>
      <template v-else-if="state.activeKey === 1">
        <p>1. 支付成功！请凭购票时所使用的有效身份证件原件进站乘车。</p>
        <p>2. 如需改签或退票，请在发车前通过订单详情页办理。</p>
      </template>
      <template v-else>
        <p>1. 历史订单仅供查询，无法办理改签或退票。</p>
        <p>2. 如需报销，请在乘车之日起规定时间内办理报销凭证。</p>
      </template>
      <p>
        {{ state.activeKey === 0 ? '4' : '3' }}. 未尽事宜详见《国铁集团铁路旅客运输规程》《广深港高铁铁路跨境旅客运输组织规则》《中老铁路跨境旅客联运组织规则》等有关规定和车站公告。
      </p>
    </div>
  </Card>
  <Modal
    width="40%"
    :visible="state.visible"
    title="退票申请"
    class="custom-modal"
    @cancel="state.visible = false"
    :footer="null"
  >
    <Alert
      message="您确认要退款吗？"
      type="warning"
      description="如有定餐饮或特产，请按规定到网站自行办理退订"
      show-icon
      style="background-color: #fff; border: none"
    >
      <template #icon><QuestionCircleFilled /></template>
    </Alert>
    <Divider :dashed="true" />
    <div style="padding: 0 30px">
      请选择要退票的订单：
      <CheckboxGroup
        v-model:value="state.refundOrder"
        @change="(value) => console.log(value, 'value')"
        :options="
          state.dataSource
            ?.find((item) => item.orderSn === state.currentOrder)
            .passengerDetails.filter((item) => item.status !== 40)
            .map((item) => ({
              label: item.realName,
              value: item.id
            }))
        "
      ></CheckboxGroup>
    </div>

    <Divider :dashed="true" />
    <div style="padding: 0 30px">
      共计退款：<a>{{
        '¥' +
        state.dataSource
          ?.find((item) => item.orderSn === state.currentOrder)
          ?.passengerDetails?.filter((item) =>
            state.refundOrder.includes(item.id)
          )
          ?.map((item) => item.amount)
          ?.reduce((after, pre) => after + pre, 0) /
          100
      }}</a>
    </div>
    <Divider :dashed="true" />
    <div style="padding: 0 30px">
      <div style="margin-bottom: 20px">
        车票票价：<a>{{
          '¥' +
          state.dataSource
            ?.find((item) => item.orderSn === state.currentOrder)
            ?.passengerDetails?.filter((item) =>
              state.refundOrder.includes(item.id)
            )
            ?.map((item) => item.amount)
            ?.reduce((after, pre) => after + pre, 0) /
            100
        }}</a>
      </div>
      <div>
        应退票款：<a>{{
          '¥' +
          state.dataSource
            ?.find((item) => item.orderSn === state.currentOrder)
            ?.passengerDetails?.filter((item) =>
              state.refundOrder.includes(item.id)
            )
            ?.map((item) => item.amount)
            ?.reduce((after, pre) => after + pre, 0) /
            100
        }}</a>
      </div>
    </div>
    <Divider :dashed="true" />
    <div style="color: #999999; padding: 0 30px">
      <QuestionCircleFilled />
      <span style="margin-left: 20px"
        >实际核收退票费及应退票款将按最终交易时间计算。</span
      >
    </div>
    <div style="color: #999999; padding: 0 30px">
      <QuestionCircleFilled />
      <span style="margin-left: 20px"
        >如你需要办理该次列车前续、后续退票业务，请于退票车次票面开车时间前办理。</span
      >
    </div>
    <Space style="width: 100%; justify-content: center; margin-top: 20px">
      <Button @click="state.visible = false">取消</Button>
      <Button
        @click="handleRefund"
        type="primary"
        :disabled="!state.refundOrder.length"
        >确定</Button
      >
    </Space>
  </Modal>
</template>

<script setup>
import {
  Tabs,
  TabPane,
  Table,
  Card,
  Pagination,
  message,
  // CheckboxGroup,
  // Checkbox,
  // TableSummary,
  // TableSummaryCell,
  // TableSummaryRow,
  CheckboxGroup,
  Modal,
  Alert,
  Divider,
  Space,
  Button
} from 'ant-design-vue'
import { QuestionCircleFilled, PrinterOutlined } from '@ant-design/icons-vue'

import CarInfo from './components/show-card-info'
import EditContent from './components/edit-content'
import RefundTicket from './components/refund-ticket'
import { fetchTicketList, fetchOrderCancel, fetchRefundTicket } from '@/service'
import { reactive, watch, h } from 'vue'
import {
  ID_CARD_TYPE,
  SEAT_CLASS_TYPE_LIST,
  TICKET_TYPE_LIST,
  TICKET_STATUS_LIST
} from '@/constants'
import Cookie from 'js-cookie'
import { useRouter } from 'vue-router'

const state = reactive({
  activeKey: 0,
  dataSource: [],
  data: null,
  current: 1,
  size: 10,
  loading: false,
  columns: [],
  checkList: [],
  checkedAll: false,
  visible: false,
  currentOrder: undefined,
  refundOrder: []
})
const userId = Cookie.get('userId')
const router = useRouter()

const columns = [
  {
    title: '车次信息',
    dataIndex: 'arrival',
    key: 'arrival',
    width: 360,
    slots: { customRender: 'info' },
    customRender: ({ text, record }) => {
      return {
        children: h(CarInfo, {
          trainNumber: record?.trainNumber,
          orderTime: record?.orderTime,
          arrival: record?.arrival,
          departure: record?.departure,
          ridingDate: record?.ridingDate,
          departureTime: record?.departureTime
        }),
        props: {
          rowSpan: record?.rowSpan
        }
      }
    }
  },
  {
    title: '旅客信息',
    dataIndex: 'id',
    key: 'id',
    width: 360,
    slots: { customRender: 'id' }
  },
  {
    title: '席位信息',
    dataIndex: 'seatType',
    key: 'seatType',
    width: 150,
    slots: { customRender: 'seatType' }
  },
  {
    title: '票价',
    dataIndex: 'amount',
    key: 'amount',
    width: 120,
    slots: { customRender: 'amount' }
  },
  {
    title: '总金额',
    dataIndex: 'totalAmount',
    key: 'totalAmount',
    width: 120,
    slots: { customRender: 'totalAmount' },
    customRender: ({ text, record }) => {
      const total = record?.passengerDetails?.reduce((sum, item) => sum + item.amount, 0) / 100 || 0
      return {
        children: h('div', { style: 'color: #f59e0b; font-weight: 500;' }, `￥${total}`),
        props: {
          rowSpan: record?.rowSpan
        }
      }
    }
  },
  {
    title: '车票状态',
    dataIndex: 'status',
    key: 'status',
    width: 120,
    slots: { customRender: 'status' },
    customRender: ({ text, record }) => {
      return {
        children: h(RefundTicket, {
          status: record?.status,
          refundClick: () => {
            state.visible = true
            state.currentOrder = record?.orderSn
            state.refundOrder = [record?.id]
          }
        }),
        props: {}
      }
    }
  }
]

watch(
  () => state.activeKey,
  (newValue) => {
    if (newValue === 0) {
      state.columns = [
        ...columns,
        {
          title: '操作',
          dataIndex: 'edit',
          key: 'edit',
          width: 150,
          slots: { customRender: 'edit' },
          customRender: ({ text, record }) => {
            return {
              children: h(EditContent, {
                orderSn: record?.orderSn,
                cancel,
                pay
              }),
              props: {
                rowSpan: record?.rowSpan
              }
            }
          }
        }
      ]
    } else {
      state.columns = columns
    }
  },
  { immediate: true }
)
watch(
  () => state.checkList,
  (val) => {
    state.indeterminate = !!val.length && val.length < state.dataSource.length
    state.checkAll = val.length === state.dataSource.length
  }
)

const handlePage = (page, pagesize) => {
  state.current = page
  state.size = pagesize
}
const cancel = (sn) => {
  fetchOrderCancel({ orderSn: sn }).then((res) => {
    if (res.success) {
      message.success('订单取消成功')
      getTicketList(state.current, state.size, state.activeKey)
    } else {
      message.error(res.message)
    }
  })
}

const pay = (sn) => {
  router.push(`/order?sn=${sn}`)
}
const getTicketList = (current, size, statusType) => {
  fetchTicketList({
    userId,
    current,
    size,
    statusType
  })
    .then((res) => {
      let dataSource = []
      res.data.records.map((info) => {
        info.passengerDetails?.map((item, index) => {
          dataSource.push({
            ...info,
            ...item,
            rowSpan: index === 0 ? info.passengerDetails.length : 0
          })
        })
      })
      state.dataSource = dataSource
      state.data = res.data
      state.loading = false
    })
    .catch((err) => {
      console.log(err)
      state.loading = false
    })
}
watch(
  () => [state.activeKey, state.current, state.size],
  (newValue) => {
    state.loading = true
    const [statusType, current, size] = newValue
    getTicketList(current, size, statusType)
  },
  { immediate: true }
)
const onCheckAllChange = (e) => {
  const a = state.dataSource.map(
    (item) => String(item.idCard) + String(item.orderSn)
  )
  Object.assign(state, {
    checkList: e.target.checked ? a : [],
    indeterminate: false
  })
}
const handleRefund = () => {
  fetchRefundTicket({
    orderSn: state.currentOrder,
    type: 0,
    subOrderRecordIdReqList: state.refundOrder
  }).then((res) => {
    if (res.success) {
      state.visible = false
      message.success('退款成功')
      getTicketList(state.current, state.size, state.activeKey)
    } else {
      message.error(res.message || '退款失败')
    }
  })
}
</script>

<style lang="scss" scoped>
.card-container {
  overflow: hidden;
}

.card-container > .ant-tabs-card > .ant-tabs-content {
  height: 120px;
  margin-top: -16px;
}

.card-container > .ant-tabs-card > .ant-tabs-content > .ant-tabs-tabpane {
  background: #fff;
  /* //   padding: 16px; */
}

.card-container > .ant-tabs-card > .ant-tabs-bar {
  border-color: #fff;
}

.card-container > .ant-tabs-card > .ant-tabs-bar .ant-tabs-tab {
  border-color: transparent;
  background: transparent;
}

.card-container > .ant-tabs-card > .ant-tabs-bar .ant-tabs-tab-active {
  border-color: #fff;
  background: #fff;
}

:deep(.ant-table-thead > tr > th) {
  background-color: #f8f8f8;
}

:deep(.ant-table-thead .ant-table-cell) {
  background-image: none;
}

:deep(.ant-tabs-top > .ant-tabs-nav) {
  margin: 0;
}
:deep(.ant-tabs-content-holder) {
  padding: 12px;
  background-color: #fff;
  box-sizing: border-box;
  background-image: none;
}

.order-list-card {
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
}

.order-list-card :deep(.ant-card-body) {
  padding: 14px 12px 16px;
}

.order-table :deep(.ant-table) {
  border-radius: 8px;
  overflow: hidden;
}

.order-table :deep(.ant-table-thead > tr > th) {
  height: 54px;
  padding: 14px 20px !important;
  color: #111827;
  font-size: 15px;
  font-weight: 700;
  background-color: #f6f7f9 !important;
}

.order-table :deep(.ant-table-tbody > tr > td) {
  padding: 18px 20px !important;
  color: #111827;
  font-size: 15px;
  line-height: 1.55;
  vertical-align: middle;
}

.order-table :deep(.ant-table-tbody > tr > td:nth-child(2) > div) {
  flex-wrap: wrap;
  gap: 8px 10px;
}

.order-table :deep(.ant-table-tbody > tr > td:nth-child(3) > div + div),
.order-table :deep(.ant-table-tbody > tr > td:nth-child(4) > div + div) {
  margin-top: 4px;
}

.order-table :deep(.ant-table-tbody > tr > td:nth-child(4) > div:last-child) {
  color: #f59e0b !important;
  font-weight: 500;
}

.order-table :deep(.ant-table-tbody > tr:hover > td) {
  background: #fbfcff !important;
}

.order-table :deep(.ant-btn-link),
.order-table :deep(a) {
  padding: 0;
  color: #1677ff;
  font-size: 15px;
}

.order-table :deep(.ant-btn-link:hover),
.order-table :deep(a:hover) {
  color: #0958d9;
}

.order-table :deep(.ant-table-placeholder .ant-table-cell) {
  padding: 44px 20px !important;
}

:deep(.ant-pagination) {
  margin-right: 4px;
}

:deep(.ant-pagination-total-text) {
  color: #6b7280;
}

:deep(.ant-modal-content) {
  overflow: hidden;
}

:deep(.custom-modal) {
  .ant-alert-warning {
    background-color: #fff !important;
    border: none !important;
  }
}
</style>
