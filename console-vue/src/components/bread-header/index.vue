<template>
  <div class="bread-header">
    <Breadcrumb>
      <BreadcrumbItem>
        <IconFont type="icon-huochezhanxiao" />
        <span>首页</span></BreadcrumbItem
      >
      <BreadcrumbItem
        ><IconFont :type="currentPath?.icon" color="black" />
        <span>{{ currentPath?.label }}</span></BreadcrumbItem
      >
    </Breadcrumb>
  </div>
</template>

<script setup>
import { Breadcrumb, BreadcrumbItem } from 'ant-design-vue'
import { useRoute } from 'vue-router'
import { routes } from '@/router'
import { watch, ref } from 'vue'
import IconFont from '@/components/icon-font'
const route = useRoute()
const currentPath = ref(null)

watch(
  () => route.path,
  (newValue) => {
    currentPath.value = routes.find((item) => item?.path === newValue)
  },
  { immediate: true }
)
</script>

<style lang="scss" scoped>
.bread-header {
  display: flex;
  align-items: center;
  min-height: 34px;
  margin-bottom: 12px;
  color: #6b7280;
}
:deep(.ant-breadcrumb) {
  margin-bottom: 0;
  padding: 0 2px;
  font-size: 13px;
}
:deep(.ant-breadcrumb a),
:deep(.ant-breadcrumb span) {
  color: #6b7280;
}
:deep(.ant-breadcrumb li:last-child span) {
  color: #333;
  font-weight: 500;
}
</style>
