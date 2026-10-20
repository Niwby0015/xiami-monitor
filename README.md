# Xiami CMDB 监控运维系统

> 一套面向运维场景的 CMDB 与一体化可观测性平台，覆盖资产台账、基础设施与应用监控、告警、链路追踪、故障演练、CI/CD 与 AI 运维助手。

本项目以 [RuoYi-Vue](https://gitee.com/y_project/RuoYi-Vue)（Spring Boot + Vue3 前后端分离版）为工程底座二次开发。**RuoYi 提供了用户、角色、菜单、权限、日志等通用能力，平台使用其前后端架构实现快速开发**。

---

## 目录

- [功能概览](#功能概览)
- [系统架构](#系统架构)
- [技术栈](#技术栈)
- [目录结构](#目录结构)
- [快速开始](#快速开始)
- [外部系统集成](#外部系统集成)
- [自定义 Exporter](#自定义-exporter)
- [安全与部署注意事项](#安全与部署注意事项)
- [上游与许可](#上游与许可)

---

## 功能概览

### 1. CMDB 配置管理

以设备/实例为中心的资产台账，覆盖机房到应用的全链路资源：

| 分类 | 内容 |
| --- | --- |
| 机房与环境 | 机房、机柜/机房设施、物理机基础信息 |
| 计算资源 | 物理机、虚拟机 |
| 网络设备 | 交换机、路由器、防火墙、负载均衡（LB） |
| 存储资源 | 存储设备与容量信息 |
| 数据服务 | 数据库实例（MySQL 等）、中间件实例（Redis、RabbitMQ 等） |
| 应用资产 | JAR 应用（非 K8s 部署的 Java 应用）、应用分类、启动命令、端口、归属设备 |
| 关系与拓扑 | 设备负责人、资产状态、设备间链路（拓扑关系）、系统流量拓扑 |
| 负载配置 | 负载/部署配置与批量部署（`/loadset`） |

CMDB 数据是监控、告警和拓扑页面的统一数据源：设备树、应用列表、实例信息均从 CMDB 读取。

### 2. 监控中心

| 模块 | 说明 |
| --- | --- |
| 硬件监控 | CPU 型号/核心数/线程数/主频、内存槽位与容量等硬件信息，数据来自自研 Exporter |
| 数据库监控 | 数据库实例总览、运行状态、异常实例；内置故障注入（sysbench / stress） |
| 中间件监控 | Redis、RabbitMQ 等中间件实例信息与 Grafana 面板嵌入 |
| JAR 应用监控 | 按设备/应用/分类查看 JVM 与业务指标；面板支持「Grafana 默认面板」和「自定义 PromQL」两种模式，可配置图表类型、时间范围、单位、刷新间隔与排序 |
| 微服务监控 | 以 Namespace / Service / Pod 维度查看 K8s 微服务指标，支持 Grafana 面板与命名空间、服务配置的持久化 |
| Kubernetes 监控 | 命名空间、Node、Pod、Deployment、Service 的列表与详情（fabric8 Kubernetes Client） |
| 链路追踪 | SkyWalking 界面嵌入与 GraphQL 查询（服务/实例/端点/Trace），调用链树可视化；JAR 应用支持 Jaeger Trace 查询 |
| 系统/流量拓扑 | 基于 DeepFlow 流量数据，使用 AntV G6 渲染机器视角拓扑与流量关系 |
| 主机/进程监控 | process-exporter 采集进程级指标并接入 Prometheus |
| 平台监控 | 服务监控（CPU/内存/磁盘/JVM）、缓存监控、Druid 连接池监控、在线用户、登录/操作日志 |

### 3. 告警中心（Nightingale / N9E）

- 告警规则的新增、编辑、删除、启停
- 活跃告警、历史告警查询
- 告警屏蔽（静默）
- 告警订阅与通知规则配置
- N9E 连接信息支持在页面中维护

告警能力由 N9E 提供，本平台通过后端代理统一鉴权和转发。

### 4. 故障演练（Chaos Mesh）

- Chaos Mesh Dashboard 嵌入
- 实验（Experiments）、演练计划（Plans）、事件（Events）、归档（Archive）页面
- 数据库监控页提供面向 MySQL 的异常注入：QPS 飙升、连接打满、慢查询、全表扫描、死锁、CPU/内存/磁盘 IO 压测等（依赖目标机 `sysbench`、`stress` 与 SSH）

> 故障演练仅应在测试/预发环境使用。

### 5. CI/CD（Jenkins）

- 流水线（Pipeline）管理：增删改查、创建 Jenkins Job
- 构建模板管理
- 触发构建、查看构建状态与日志
- 同步 Jenkins Job 列表与构建历史

### 6. AI 运维助手

- 前端提供对话式运维助手，支持文本、表单交互与 Markdown 渲染
- 基于 LangChain4j + DeepSeek（OpenAI 兼容接口）
- Supervisor 负责意图路由，分派给系统、Kubernetes、告警等子智能体
- 支持对话历史与人工审批（Approve）流程

### 7. 流程审批（Flowable）

- 流程定义、流程实例、待办/已办任务
- 流程表达式、监听器、动态表单
- BPMN 设计器（bpmn-js）与表单设计器（vform3）
- 可用于变更审批、故障处置、资源申请等运维流程

### 8. 平台基础能力（继承自 RuoYi）

用户、部门、岗位、角色、菜单、字典、参数、通知公告、操作日志、登录日志、在线用户、定时任务、代码生成、接口文档等。

---

## 系统架构

```text
┌──────────────────────────────────────────────────────────────────┐
│                      浏览器 / Mobisys-Ruoyi-vue                   │
│   CMDB 台账 · 监控大屏 · 告警中心 · 故障演练 · CI/CD · AI 助手      │
└───────────────────────────────┬──────────────────────────────────┘
                                │ HTTP / JWT
┌───────────────────────────────▼──────────────────────────────────┐
│                  Mobisys-Ruoyi-java（Spring Boot）                │
│  权限菜单 · CMDB 服务 · 监控聚合 API · 告警代理 · 流程引擎 · AI     │
└───┬──────────┬───────────┬───────────┬───────────┬───────────────┘
    │          │           │           │           │
  MySQL      Redis     Kubernetes    N9E API    Jaeger / Jenkins
    │                    API
    │
    └── 外部可观测性栈：Prometheus · Grafana · SkyWalking · DeepFlow · Chaos Mesh
```

后端不直接存储监控时序数据：指标由 Prometheus 采集，Grafana 负责展示，平台负责资产关联、面板编排、权限与聚合查询。

---

## 技术栈

### 后端

| 类别 | 技术 |
| --- | --- |
| 语言/运行时 | Java 8 |
| 框架 | Spring Boot 2.5.15、Spring Framework 5.3.39、Spring Security 5.7.14 |
| 认证 | JWT + Redis |
| 持久层 | MyBatis、PageHelper、Druid、MySQL |
| 流程引擎 | Flowable 6.8.0 |
| Kubernetes | fabric8 Kubernetes Client 6.10.0 |
| AI | LangChain4j 0.35.0（OpenAI 兼容接口，当前接入 DeepSeek） |
| 文档 | Springfox Swagger |
| 其他 | Quartz、POI、FastJSON2、JSch、SkyWalking Agent |

### 前端

| 类别 | 技术 |
| --- | --- |
| 框架 | Vue 3.5、Vite 6、Pinia、Vue Router 4 |
| UI | Element Plus 2.13 |
| 图表 | ECharts 5、AntV G6 4、AntV X6 3 |
| 流程/表单 | bpmn-js、vform3 |
| 其他 | axios、dayjs、vue-quill、wangeditor |

---

## 目录结构

```text
MobisysWS/
├── Mobisys-Ruoyi-java/          # 后端（Spring Boot 多模块）
│   ├── ruoyi-admin/             # 启动模块、Controller（agent/cicd/device/facility/monitor）
│   ├── ruoyi-device/            # CMDB 领域模型、Mapper、Service
│   ├── ruoyi-flowable/          # Flowable 流程定义/实例/任务/表单/表达式/监听器
│   ├── ruoyi-framework/         # 安全、Kubernetes Client 等框架配置
│   ├── ruoyi-system/            # 系统管理与 K8s 查询服务
│   ├── ruoyi-common/            # 通用工具与基础类
│   ├── ruoyi-quartz/            # 定时任务
│   ├── ruoyi-generator/         # 代码生成
│   ├── sql/                     # 初始化 SQL
│   └── skywalking-agent/        # 内置 SkyWalking Java Agent
│
├── Mobisys-Ruoyi-vue/           # 前端（Vue3 + Vite）
│   └── src/
│       ├── views/device/        # CMDB 台账页面
│       ├── views/facility/      # 机房设施
│       ├── views/monitor/       # 监控、告警、K8s、拓扑、链路
│       ├── views/chaosMesh/     # 故障演练
│       ├── views/cicd/          # CI/CD
│       ├── views/agent/         # AI 运维助手
│       ├── views/flowable/      # 流程审批
│       └── views/system/ tool/  # 平台基础功能
│
├── xiami-exporter/              # Go 自研 Prometheus Exporter（硬件/请求指标）
├── OtherExporter/               # process-exporter DaemonSet / ServiceMonitor 配置
├── ruoyi-vue3-flowable/         # Flowable 前端参考工程（非主运行前端）
├── *.json                       # Grafana Dashboard 导出文件
└── README.md
```

---

## 快速开始

### 环境要求

| 组件 | 建议版本 |
| --- | --- |
| JDK | 8 |
| Maven | 3.6+ |
| MySQL | 5.7 / 8.0 |
| Redis | 5.0+ |
| Node.js | 18+（Vite 6） |
| 可选 | Kubernetes、Prometheus、Grafana、N9E、SkyWalking、Jaeger、DeepFlow、Chaos Mesh、Jenkins |

### 1. 初始化数据库

```bash
mysql -uroot -p -e "CREATE DATABASE \`ry-vue\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"

# 若依基础表与菜单
mysql -uroot -p ry-vue < Mobisys-Ruoyi-java/sql/ry_20250522.sql

# 业务扩展表
cd Mobisys-Ruoyi-java/sql
mysql -uroot -p ry-vue < jars_app_manager.sql
mysql -uroot -p ry-vue < jars_monitor_panel_config.sql
mysql -uroot -p ry-vue < jars_panel_sort_span.sql
mysql -uroot -p ry-vue < microservice_monitor_panel_config.sql
mysql -uroot -p ry-vue < monitor_microservice_namespace.sql
mysql -uroot -p ry-vue < "monitor?_microservice_service.sql"
```

> 如数据库不是 `ry-vue` 或账号密码不同，请同步修改 `application-druid.yml`。

### 2. 后端配置与启动

主要配置文件：

- `Mobisys-Ruoyi-java/ruoyi-admin/src/main/resources/application.yml`
- `Mobisys-Ruoyi-java/ruoyi-admin/src/main/resources/application-druid.yml`

需要关注：

| 配置项 | 说明 |
| --- | --- |
| `server.port` | 默认 `30812` |
| `spring.redis.*` | Redis 地址 |
| `spring.datasource.druid.master.*` | MySQL 连接 |
| `n9e.*` | N9E 地址、账号、默认业务组 |
| `jaeger.base-url` | Jaeger Query API |
| `flowable.*` | Flowable 行为配置 |
| `jenkins.*` | Jenkins 地址与凭据（见下方说明） |

编译与启动：

```bash
cd Mobisys-Ruoyi-java
mvn clean package -DskipTests

# 普通启动
java -jar ruoyi-admin/target/ruoyi-admin.jar

# 带 SkyWalking 探针启动
java -javaagent:skywalking-agent/skywalking-agent.jar \
     -jar ruoyi-admin/target/ruoyi-admin.jar
```

SkyWalking 相关环境变量：

```bash
export SW_AGENT_NAME=ruoyi-admin
export SW_AGENT_COLLECTOR_BACKEND_SERVICES=<oap-host>:11800
```

> 注意：`application.yml` 中 Jenkins 配置目前位于 `flowable` 节点下，无法绑定到 `@ConfigurationProperties(prefix = "jenkins")`；如需通过配置文件覆盖，请将 `jenkins:`、`base-url`、`username`、`api-token` 调整为顶层节点，否则实际使用的是 `JenkinsConfig` 中的默认值。

### 3. 前端配置与启动

```bash
cd Mobisys-Ruoyi-vue
npm install

# 开发模式（默认端口 30011）
npm run dev

# 生产构建
npm run build:prod
```

开发代理配置在 `vite.config.js`，默认包含：

- 后端 API：`/dev-api` → `http://localhost:30812`
- Prometheus：`/dev-api/prom-api` → `http://<prometheus>:30092`
- Grafana：`/api` → `http://<grafana>:32556`
- SkyWalking GraphQL：`/graphql` → `http://<skywalking-ui>:12800`

页面标题与接口前缀在 `.env.development` 中配置。

### 4. Docker 构建

仓库根目录的 `Mobisys-Ruoyi-java/Dockerfile` 已包含 SkyWalking Agent，基于预先构建的 `ruoyi-admin/target/ruoyi-admin.jar` 打包：

```bash
cd Mobisys-Ruoyi-java
mvn clean package -DskipTests
docker build -t mobisys-backend .
```

> Dockerfile 基础镜像为 Temurin 24，而 Maven 工程按 Java 8 编译；如遇运行时兼容问题，请统一 JDK 版本。

---

## 外部系统集成

平台通过 HTTP API / 嵌入页面的方式与下列系统集成。默认地址仅供开发环境参考，部署时请按实际环境修改。

| 系统 | 默认地址 | 用途 | 配置位置 |
| --- | --- | --- | --- |
| N9E（夜莺） | `http://192.168.31.34:30005` | 告警规则、活跃/历史告警、静默、订阅 | `application.yml` 或告警页面 |
| Jaeger | `http://192.168.31.34:32686` | JAR 应用 Trace 查询 | `application.yml` / JAR 监控页面 |
| SkyWalking | `http://192.168.31.34:8080` / OAP `11800` | 链路与调用链 | 前端页面、Agent 环境变量 |
| Jenkins | `http://192.168.31.34:8090` | 流水线与构建 | `application.yml` / `JenkinsConfig` |
| Kubernetes | `~/.kube/config` 或 ServiceAccount | Pod/Deployment/Service 查询 | 自动发现 |
| Prometheus | `http://192.168.31.34:30092`（代理默认） | 指标查询 | `vite.config.js`、监控页面 |
| Grafana | `http://192.168.31.34:32556` | 仪表盘嵌入 | `vite.config.js`、页面内地址 |
| DeepFlow | `http://192.168.31.34:30417/v1/query` | 流量拓扑 | `systemtopo` 页面 |
| Chaos Mesh | `http://192.168.31.34:32361` | 故障演练面板 | `chaosMesh` 页面 |
| DeepSeek | `https://api.deepseek.com/v1` | AI 运维助手 | `com.ruoyi.agent.node.*` |

> 部分地址目前硬编码在前端页面（如 Grafana、DeepFlow、Chaos Mesh、SkyWalking）和后端 Controller 中。生产部署建议统一改为后端配置或环境变量下发。

---

## 自定义 Exporter

### xiami-exporter（Go）

采集节点硬件与业务指标，默认监听 `:30012`：

```bash
cd xiami-exporter
go build -o xiami-exporter .
./xiami-exporter
# 指标端点：http://<host>:30012/metrics
# 手动刷新：http://<host>:30012/refresh
```

暴露的主要指标：

| 指标 | 说明 |
| --- | --- |
| `hardware_cpu_model_info` | CPU 型号信息 |
| `hardware_cpu_cores_total` | CPU 核心数 |
| `hardware_cpu_threads_total` | CPU 线程数 |
| `hardware_cpu_frequency_hertz` | CPU 主频 |
| `hardware_memory_slot_info` | 内存槽位信息 |
| `hardware_memory_slots_total` | 内存槽位总数 |
| `myapp_requests_total` | HTTP 请求总数（示例指标） |
| `myapp_request_duration_seconds` | HTTP 请求耗时（示例指标） |

Kubernetes 部署清单：

- `xiami-exporter/packaged-hardwareInfo/`：物理机/已打包环境（DaemonSet + Service + ServiceMonitor）
- `xiami-exporter/unpackaged-hardwareInfo/`：非打包环境（额外包含 Endpoints）

### process-exporter

`OtherExporter/process-exporter/` 提供进程监控的 DaemonSet、Service、ServiceMonitor 与配置文件，接入 Prometheus 后可用于进程级资源监控。

### Grafana

仓库根目录的 JSON 文件为已导出的 Dashboard，可直接导入 Grafana：

- `grafana-custom-metrics-dashboard-fixed.json`
- `jars-custom-metrics-dashboard.json`
- `microservice-pod-1773395643355.json`
- `Petclinic Monitor-1773745072579.json`

---

## 安全与部署注意事项

1. **修改默认凭据**：数据库、Redis、Druid 控制台、JWT `token.secret`、N9E 账号等均需替换为生产环境凭据。
2. **不要在源码中保留密钥**：当前 Jenkins API Token、DeepSeek API Key、SSH 密码等存在硬编码，建议迁移到环境变量或配置中心，并轮换已泄露的密钥。
3. **故障注入接口风险**：`/monitor/inject/run` 会通过 SSH 在目标机执行 `sysbench` / `stress` 命令，且目标地址、账号为硬编码；生产环境应禁用或加白名单与二次确认。
4. **内网地址参数化**：前端与后端存在大量写死的内网 IP/端口，部署到新环境时需逐项替换。
5. **权限控制**：AI 助手的 K8s/告警操作、CI/CD 触发、故障注入等高风险能力应结合 RuoYi 的角色与菜单权限严格收敛。
6. **Flowable 表结构**：`flowable.database-schema-update` 当前为 `none`，首次部署需确认流程相关表已初始化。
7. **备份**：CMDB 数据是平台核心资产，升级或演练前请备份 MySQL。

---

## 上游与许可

- 工程底座：[RuoYi-Vue](https://gitee.com/y_project/RuoYi-Vue)（MIT License），其版权与许可声明继续有效。
- 前端流程设计能力参考 [RuoYi-flowable](https://gitee.com/tony2y/RuoYi-flowable) 与 [ruoyi-vue3-flowable](https://gitee.com/gubeiqing/RuoYi-flowable)。
- 本项目在此基础上进行的 CMDB、监控、告警、演练、CI/CD、AI 助手等改造，遵循仓库原有许可协议。
