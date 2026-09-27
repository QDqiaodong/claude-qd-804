# 变电站 · 设备巡检与两票管理

以「两票（工作票 / 操作票）」为核心的变电站运行管理系统。演示录屏用，一键起停。

## 快速开始

```bash
./start.sh        # 首次构建并启动；之后秒级启动
./stop.sh         # 暂停（不删容器）
./resume.sh       # 恢复
./clean.sh        # 收摊：down -v，彻底清空
```

- 前端：http://127.0.0.1:8284
- 后端：http://127.0.0.1:8384 （健康检查 `/actuator/health`）
- MySQL 3584 / Redis 6584

## 技术栈

后端 Spring Boot 3.3.0 + Java 17 + Spring Data JPA(Hibernate 6.5) + MySQL 8 + Redis；
前端 Vue 3 + Element Plus + Vite。主色 `#e65100`（深橙）。

## 五个业务模块

| 模块 | 内容 |
| --- | --- |
| 变电站与间隔 | 变电站、电压等级、间隔（主变/出线/电容器/母联），间隔状态：运行/热备/冷备/检修 |
| 设备台账 | 主变、断路器、隔离开关、CT/PT、避雷器；编号、型号、投运日期、上次检修日期、状态 |
| 巡检与缺陷 | 巡检计划、巡检记录、缺陷（紧急/重大/一般，发现时间、限期消缺日期、闭环状态） |
| 工作票 | 票号、工作负责人、停电范围、签发/许可/终结三审、开工与终结时间 |
| 操作票 | 操作任务、操作步骤序列（按顺序逐条执行）、每步执行人与执行时间 |

## 数据访问路线（本仓技术标识）

1. **存储过程**：`@NamedStoredProcedureQuery` + `@Procedure`
   - `sp_defect_summary`：统计各变电站当月缺陷数与消缺率
   - `sp_equipment_summary_by_voltage`：按电压等级汇总设备台数与待检数
   - `sp_next_work_ticket_no`：一键生成工作票票号并回写

   定义见 `backend/src/main/resources/procedures.sql`，由 `ProcedureInitializer`
   （ApplicationRunner）在启动时经 DataSource 执行。之所以不放 `mysql/schema.sql`：
   那是构建期在 MySQL 容器里跑的，过程体里的 `;` 会被客户端截断。
2. **手写 JPQL `@Query`**：缺陷超期排行、两票状态统计、时间轴按天票量等。

## 业务规则（抛 `BizException`，前端直接弹中文提示）

- 倒闸操作步骤必须按顺序执行，跳步时提示「应先执行第 N 步」
- 工作票签发人 / 许可人 / 终结人三人不得为同一人
- 缺陷超期未消缺，不允许终结该间隔的工作票
- 设备处于运行状态的间隔，不允许下达检修工作票

## 前端四个页面（顶部时间轴导航 + 四种操作范式）

1. **间隔平面示意图**：SVG 母线 + 各间隔格子，按四种状态着色，点格子弹 popover 直接登记缺陷
2. **两票票面预览卡 + 三审签字**：A4 比例票面卡，底部「签发 → 许可 → 终结」依次点亮
3. **倒闸操作步骤勾选器**：竖向步骤列表按顺序勾选，右侧实时预览执行进度与执行人
4. **缺陷超期排行 + 消缺闭环**：横向排行条，前三名标红，展开后「登记 → 派工 → 处理 → 验收」

## 目录结构

```
backend/src/main/java/com/substation/ops/
  domain/        9 个业务实体 + 2 个存储过程结果映射实体
  dto/           请求体与视图对象
  repository/    @Procedure + @Query
  service/       业务规则（BizException）
  controller/    /api/xxx，统一 ApiResponse 包装
  config/        ProcedureInitializer
backend/src/main/resources/{schema.sql, procedures.sql}
mysql/schema.sql          构建期执行的建表 + 种子数据（普通 DDL/DML）
frontend/src/{App.vue, views/*.vue, api/index.js, router/index.js, styles/main.css}
```
