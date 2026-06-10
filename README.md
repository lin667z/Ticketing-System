# Ticketing-System

**铁路售票微服务平台** — 一个面向高并发场景、具备 AI 对话能力的全栈分布式票务系统，对标 12306 核心业务流程。

---

## 项目概览

本系统以 B2C 铁路售票为核心业务场景，涵盖列车查询、选座购票、订单管理、支付宝支付、退改签的全链路实现，同时内建一套可插拔的 AI 智能问答 Agent 框架。

**代码规模**：6 个微服务 + 1 个前端应用 + 11 个自定义基础设施模块，总数约 300+ Java 类。

---

## 系统架构

```
┌──────────────────────────────────────┐
│          console-vue (Vue 3)         │  前端：Ant Design Vue + Axios
└──────────────┬───────────────────────┘
               │
┌──────────────▼───────────────────────┐
│      gateway-service (Spring Cloud   │  JWT 鉴权 + 路由转发
│             Gateway)                 │
└──────────────┬───────────────────────┘
               │
   ┌───────────┼───────────┬───────────────┬──────────────┐
   ▼           ▼           ▼               ▼              ▼
┌──────┐  ┌────────┐  ┌───────┐  ┌──────────────┐  ┌──────────┐
│ticket│  │ order  │  │  pay  │  │    user      │  │    ai    │
│service│ │service │  │service│  │   service    │  │ service  │
└──┬───┘  └───┬────┘  └──┬────┘  └──────┬───────┘  └────┬─────┘
   │          │          │              │               │
   └──────────┼──────────┼──────────────┼───────────────┘
              │          │              │
   ┌──────────▼──────────▼──────────────▼───────────────┐
   │              frameworks (11 modules)               │
   │  base │ cache │ common │ convention │ database     │
   │  designpattern │ distributedid │ idempotent        │
   │  log │ web │ bizs/user                             │
   └─────────────────────┬──────────────────────────────┘
                         │
   ┌─────────────────────┼──────────────────────────────┐
   │                     ▼                              │
   │  MySQL (ShardingSphere 分库分表 + 列加密)            │
   │  Redis (Redisson 分布式锁 + 缓存 + Lua 原子操作)      │
   │  RocketMQ (异步消息：支付回调、Canal binlog)          │
   │  Canal (MySQL binlog → 实时缓存同步)                 │
   │  XXL-Job (定时任务：站点数据同步、余票刷新)             │
   │  Alipay SDK (支付渠道)                              │
   └────────────────────────────────────────────────────┘
```

**模块职责**：

| 微服务 | 职责 | 是否分库分表 |
|--------|------|:---:|
| `ticket-service` | 列车查询、选座购票、退改签 | × |
| `order-service` | 订单 CRUD、订单关闭/取消 | ✓ (2库×32表) |
| `pay-service` | 支付宝支付/退款/回调 | ✓ |
| `user-service` | 用户管理、乘车人管理 | ✓ |
| `ai-service` | AI 智能票务问答 (Multi-Agent) | — |
| `gateway-service` | API 网关、JWT 鉴权 | — |

---

## 技术栈与选型理由

| 技术 | 版本 | 选型理由 |
|------|------|----------|
| **Java** | 17 | LTS 版本，Record/ZGC/G1 等特性支撑高吞吐 |
| **Spring Boot** | 3.2.5 | 生态成熟，`-parameters` 编译保留参数名用于 SpEL |
| **Spring Cloud** | 2023.0.1 | 微服务治理（GateWay 路由 + Feign 调用） |
| **Spring Cloud Alibaba** | 2023.0.1.0 | Nacos 注册/配置中心一体化方案 |
| **ShardingSphere-JDBC** | 5.5.2 | 分库分表 + 数据加密一体化，无需代理层 |
| **MyBatis-Plus** | 3.5.7 | Lambda 条件构造器 + 自动填充，兼容 ShardingSphere |
| **RocketMQ** | 4.9.4 | 高吞吐异步解耦，支持延迟消息（订单超时关闭） |
| **Redisson** | 3.21.3 | 分布式锁 + Lua 脚本执行，比 Jedis 更丰富的并发原语 |
| **Canal** | — | MySQL binlog 订阅，实现缓存与 DB 最终一致性 |
| **XXL-Job** | 2.3.1 | 轻量分布式任务调度，支持分片、失败重试 |
| **Reactor** | — | 响应式编程模型，驱动 AI Agent 异步流式执行 |
| **Vue 3** | 3.2 + Ant Design | Composition API + 企业级 UI 组件 |
| **Alipay SDK** | 4.38.10 | 支付宝官方 SDK，对接扫码支付、退款、回调 |

---

## 核心技术难点与解决方案

### 1. 高并发抢票：令牌桶 + 分布式锁 + Lua 原子操作

**问题规模**：一趟列车有多个经停站，每段区间有若干座位。同一列车同一区间的余票在并发场景下需保证不超卖，同时不能过度锁竞争降低吞吐。

**三段式控制流**：

```
用户请求 ──► [令牌桶预扣 (Lua)] ──► [分布式锁保护库存初始化] ──► [责任链执行购票]
                   │                           │
                   ▼                           ▼
              Redis Hash              Redisson Lock + Double Check
              原子 decrement               (仅冷启动时)
```

**关键设计**：

- **令牌桶**：Redis Hash 存储每趟列车每段区间的每种座位类型余量。购票时通过 **Lua 脚本** 原子比对请求量 ≤ 可用量后扣减，否则返回失败。回滚（订单取消/超时）同样通过 Lua 脚本原子回补。
  - 源码：[`TicketAvailabilityTokenBucket.java`](services/ticket-service/src/main/java/org/ticketing_system/biz/ticketservice/service/handler/ticket/tokenbucket/TicketAvailabilityTokenBucket.java)
  - Lua 脚本路径：`lua/ticket_availability_token_bucket.lua` / `ticket_availability_rollback_token_bucket.lua`

- **分布式锁保护库存初始化**：当 Redis 中某趟列车的令牌桶 Hash 不存在时（即首次访问或缓存过期），使用 Redisson `tryLock` + Double Check 保证只有一个线程执行 MySQL → Redis 的库存加载，其他线程等待加载完成。

- **责任链执行**：购票流程拆分为多个 ChainHandler（参数校验 → 库存校验 → 座位选择 → 订单创建），通过 `AbstractChainContext` 编排。三种座位等级（商务座/一等座/二等座）分别对应不同的 `TrainPurchaseTicketHandler` 策略实现。

**技术要点**：
- Lua 脚本保证令牌操作原子性，避免 Redis 主从延迟导致超卖
- Double Check 确保分布式锁内只加载一次库存
- 责任链 + 策略模式隔离不同座位等级的选座逻辑

---

### 2. 数据库分库分表：ShardingSphere 复合分片 + 列级加密

**分片规模**：2 个物理库 × 32 张逻辑表 = 64 个物理分片。

**分片策略（Order 服务为例）**：

```yaml
# shardingsphere-config.yaml
t_order:
  actualDataNodes: ds_${0..1}.t_order_${0..32}
  databaseStrategy:
    complex:  # 复合分片
      shardingColumns: user_id, order_sn
      algorithmClassName: OrderCommonDataBaseComplexAlgorithm
  tableStrategy:
    complex:
      shardingColumns: user_id, order_sn
      algorithmClassName: OrderCommonTableComplexAlgorithm
```

**为什么选择复合分片（Complex）而非简单的 Standard 分片？**

- 订单表有两种高频查询路径：用户查询自己的订单（`user_id`） 和 系统根据订单号查单笔详情（`order_sn`）。
- Standard 分片只支持单列，Inline 表达式无法满足"两列都能定位分片"的需求。
- **自定义 Algorithm 类** `OrderCommonDataBaseComplexAlgorithm` 内部实现：`shardingValue = user_id 存在 ? user_id % 32 : hash(order_sn) % 32`，在 Spring 上下文中查询时的 `where user_id = ?` 和 `where order_sn = ?` 都能精确定位到单个分片。

**列级加密**：

```yaml
- !ENCRYPT
  t_order_item:
    columns:
      id_card:
        cipher: { encryptorName: common_encryptor }
      phone:
        cipher: { encryptorName: common_encryptor }
  encryptors:
    common_encryptor:
      type: AES
      props:
        aes-key-value: <密钥>
```

`id_card`、`phone` 等敏感字段在写入时自动 AES 加密，读取时自动解密。对业务代码完全透明，不需要额外处理。

**分片键设计哲学**：以**查询模式反推分片键**，确保 100% 查询都能路由到单一分片，避免全分片扫描。

---

### 3. 多 Agent AI 框架：主从规划 + 响应式流式聚合

**系统定位**：在售票平台内嵌入 AI 助手，用户可自然语言查询车次余票、历史订单、或闲聊。

**Agent 架构**：

```
用户输入 ──► [MasterAgent] ──► [AgentPlan]
                                    │
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
            TICKET_INFO      ORDER_QUERY      GENERAL_CHAT
                 │               │               │
                 ▼               ▼               ▼
         [AgentDispatcher: Reactor Flux 并行执行]
                 │               │               │
                 └───────────────┼───────────────┘
                                 ▼
                    [AgentResponseAggregator]
                          (LLM 聚合 / 直拼接)
                                 │
                                 ▼
                        SSE 流式输出到前端
```

**关键模块**：

- **MasterAgent**（路由层）：收到用户输入后，构造 System Prompt + 会话上下文（Working Memory 中的已知槽位），调用 LLM 生成执行计划 `AgentPlan`（JSON 格式）。支持：纯就绪任务、纯待补全任务、混合任务三种计划形态。失败时降级到关键词正则回退路由。

- **AgentDispatcher**（调度层）：基于 `Flux.flatMap` 并行执行就绪任务，每个 Agent 独立超时管控（默认 30s）、异常隔离（一个失败不影响其他）。

- **Worker Agent**（执行层）：`TicketInfoAgent` 调用 ticket-service Feign 获取车次/余票；`OrderQueryAgent` 调用 order-service 查询用户订单。

- **AgentResponseAggregator**（聚合层）：多任务结果（含并发失败）统一经 LLM 二次改写为用户友好的自然语言回复；单任务直接输出。支持 SSE 流式推送到前端。

**设计亮点**：
- 槽位记忆（SessionSlotState）：多轮对话中自动填充并记忆"出发地/目的地/日期"，用户只需说"那帮我查明天的"即可
- 计划重放（Resume Key）：待补全任务通过 `sessionId:agentType:missingFields` 标识，下一轮补全后精确恢复
- 前端交互式卡片：结构化组件（车次卡片/订单卡片） + Markdown 正文分别渲染

---

### 4. 幂等性基础设施：Token + Param + SpEL 三模态

**应用场景**：

| 场景 | 防重模式 | 实现 |
|------|----------|------|
| 用户重复点击"提交订单" | Token 令牌 | `@RestAPIIdempotent` → 先获取 Token，提交时校验并消费 |
| MQ 消息重复投递（支付回调） | SpEL 表达式 | `@MQIdempotent` → 根据消息体中 `orderSn` 幂等 |
| 通用接口参数级防重 | 参数 MD5 | `@Idempotent(type=PARAM)` → 根据请求参数摘要防重 |

**核心实现**：

- **Token 模式**：接口前置暴露 `/api/token` 获取一次性令牌（Redis 存储，User ID + Path 复合 Key），业务方法执行前通过 AOP 消费令牌。消费成功才放行，重复携带同一令牌则拦截返回"请勿重复提交"。

- **SpEL 模式**（MQ 场景最常用）：`@MQIdempotent(key = "#message.orderSn")`，AOP 将 SpEL 表达式结果作为幂等 Key。RocketMQ 消费时先从 Redis 尝试 SET NX，成功则继续消费，失败则返回 `CONSUME_SUCCESS` 不抛异常（避免 MQ 无限重试）。

- **Param 模式**：对请求体做 JSON 序列化 → MD5，适合无业务标识的场景，但谨慎使用（相同参数的合法重复调用会被拦截）。

**工程考量**：
- 幂等状态存储在 Redis，带 TTL 防止 Key 无限积累
- MQ 幂等失败返回 SUCCESS（不是 LATER），因为消费状态已不可知但消息可丢弃
- 全局异常处理器统一捕获 `RepeatConsumptionException`，转换为友好错误码

---

### 5. Cache Aside + Canal 异步一致性

**余票缓存的特殊挑战**：余票是高频读、高频写的热点数据。无法用简单的 TTL 过期（可能导致缓存与 DB 不一致）。

**方案**：
1. **启动预加载**：`SeatMarginCacheLoader` 在 CommandLineRunner 中批量加载所有列车的余票到 Redis Hash
2. **Canal binlog 监听**：`CanalCommonSyncBinlogConsumer` 消费 RocketMQ 中的 Canal 事件
3. **策略分发**：`TicketAvailabilityCacheUpdateHandler`（实现了 `AbstractExecuteStrategy`）通过 `mark()` 识别 `t_seat` 表变更，提取 `(train_id, start_station, end_station, seat_type)`，对 Redis Hash 做 `increment` 操作，保持缓存与 DB 一致

```
DB 数据变更 → Canal binlog → RocketMQ → TicketAvailabilityCacheUpdateHandler
                                                    ↓
                                         Redis Hash increment/decrement
```

**技术收益**：缓存始终热且准确，避免 TTL 过期雪崩；增量更新而非全量刷新，降低写入放大。

---

## 工程化实践

### 编码规范

- **Checkstyle**：170+ 条规则（禁止 `System.out.println`、禁止魔法数字 > 2、行宽 ≤ 200、方法 ≤ 150 行）
- **Spotless**：编译期自动格式化 + License Header（空内容，不污染源码）
- **Lombok**：全项目统一 `equalsAndHashCode.callSuper=skip`（实体继承 `BaseDO` 时不包含父类字段）

### 异常与响应体规范

```
AbstractException
  ├── ClientException   (A 系列错误码，4xx，客户端参数/权限问题)
  ├── ServiceException  (B 系列错误码，5xx，服务逻辑/数据问题)
  └── RemoteException   (C 系列错误码，5xx，远程调用失败)

GlobalExceptionHandler → 统一捕获 → Result { code: "0"(成功), "A..."(失败), data: T }
```

服务代码中永远不直接调用 `Results.failure()`（protected），而是 throw 相应异常让全局处理器转换。

### 设计模式应用

| 模式 | 使用场景 |
|------|----------|
| **责任链 (Chain of Responsibility)** | 购票流程校验、退款流程校验、车票查询过滤 — `AbstractChainContext` |
| **策略 (Strategy)** | Canal 事件处理分发 (`AbstractExecuteStrategy` + `mark()`)、不同座位等级购票 |
| **建造者 (Builder)** | DTO 构建、LLM 请求体组装 |
| **模板方法** | Snowflake 分布式 ID 生成器工作 ID 选择 (`AbstractWorkIdChooseTemplate`) |

### 自定义 Spring Boot Starter

`frameworks/` 下每个模块都是独立的 Starter，通过 `AutoConfiguration.imports` 自动装配：

- `cache`：封装 `StringRedisTemplate` + `RedissonClient` + 布隆过滤器穿透保护 + 本地缓存多级策略
- `distributedid`：Snowflake (Redis 分配 Worker ID) + 业务 ID 生成器
- `idempotent`：AOP + SpEL + Redis 的幂等框架（支持 REST API 和 MQ 两种消费场景）
- `designpattern`：责任链上下文 + 策略选择器
- `log`：`@ILog` 注解切面自动记录方法入参/出参/耗时

---

## 可观测性

- **Prometheus**：Micrometer 通过 `/actuator/prometheus` 暴露 JVM、线程池、HTTP、DB 指标
- **Hippo4J**：动态线程池监控与告警
- **ShardingSphere SQL 展示**：`props.sql-show: true` 在日志中输出实际路由 SQL
- **Feign 日志**：DEBUG 级别输出全量远程调用请求/响应

---

## 快速开始

### 环境依赖

- Java 17+
- MySQL 8.0+ （端口 13306）
- Redis 6.0+
- RocketMQ 4.9+
- Canal (可选，关闭后缓存使用定时刷新)
- XXL-Job (可选，定时任务可通过 API 手动触发)

### 构建

```bash
mvn clean compile -DskipTests
```

### 运行

```bash
# 逐个启动微服务（无顺序要求）
mvn spring-boot:run -pl services/gateway-service
mvn spring-boot:run -pl services/user-service
mvn spring-boot:run -pl services/ticket-service
mvn spring-boot:run -pl services/order-service
mvn spring-boot:run -pl services/pay-service
mvn spring-boot:run -pl services/ai-service
```

### 前端

```bash
cd console-vue
yarn install
yarn serve    # 开发模式
yarn build    # 生产构建
```

### 数据库初始化

```sql
-- 执行 resources/db/ 下的 SQL 脚本：
-- ticketing_system-springcloud-order.sql
-- ticketing_system-springcloud-pay.sql
-- ticketing_system-springcloud-ticket.sql
-- ticketing_system-springcloud-user.sql
-- ticketing_system-ai.sql
```
