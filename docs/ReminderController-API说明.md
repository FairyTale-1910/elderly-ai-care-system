# ⏰ ReminderController 日程提醒接口文档

`ReminderController` 是智慧老人陪护系统中的日程与健康提醒管家。它主要负责查询老人的未完成任务、供前端高频轮询到期提醒，以及处理老人点击确认后的状态更新与周期性任务的时间推移。

---

## 1. 核心业务接口

该控制器包含三个核心接口，分别用于：全量列表查询、到期轮询检测、以及任务状态核销。

### 📥 1.1 获取未完成提醒列表
用于展示老人当前所有的待办任务（按触发时间先后排序）。

- **请求路径**: `/api/reminders/list`
- **请求方式**: `GET`

**请求参数 (URL Param)**
| 参数名 | 类型 | 必填 | 描述 |
| :--- | :--- | :--- | :--- |
| `uid` | Long | **是** | 当前登录老人的唯一用户 ID。 |

**响应参数 (Response)**
返回一个包含任务明细的 JSON 数组：
| 字段名 | 类型 | 描述 |
| :--- | :--- | :--- |
| `reid` | Long | 提醒任务的唯一主键 ID |
| `user_id` | Long | 关联老人的ID |
| `task_name` | String | 任务名称（如：吃降压药） |
| `remind_time` | String | 每日提醒时间（时分秒） |
| `next_remind_time` | String | 下次即将触发的完整时间（年月日时分秒） |
| `repeat_type` | Integer | 重复频率（0: 一次性, 1: 每天, 2: 每周） |
| `is_completed` | Boolean | 是否已完成（当前接口恒为 false） |

---

### 📥 1.2 轮询到期提醒 (高频检查)
供前端设置定时器（如每 10 秒调用一次），当系统时间达到或超过 `next_remind_time` 时，拉取需要立刻弹窗提醒老人的任务。

- **请求路径**: `/api/reminders/check`
- **请求方式**: `GET`

**请求参数 (URL Param)**
| 参数名 | 类型 | 必填 | 描述 |
| :--- | :--- | :--- | :--- |
| `uid` | Long | **是** | 当前登录老人的唯一用户 ID。 |

**响应参数 (Response)**
返回一个精简版的 JSON 数组，仅包含需要立刻执行的任务：
| 字段名 | 类型 | 描述 |
| :--- | :--- | :--- |
| `reid` | Long | 任务 ID（用于传给完成接口） |
| `task_name` | String | 提醒老人的文字内容 |
| `repeat_type` | Integer | 循环类型 |

---

### 📥 1.3 确认完成提醒
当老人在前端弹窗点击“我已完成/已吃药”后触发。根据任务的循环类型，决定是彻底关闭任务，还是将其推迟到下一个周期。

- **请求路径**: `/api/reminders/complete`
- **请求方式**: `POST`

**请求参数 (URL Param)**
| 参数名 | 类型 | 必填 | 描述 |
| :--- | :--- | :--- | :--- |
| `reid` | Long | **是** | 前端通过 check 或 list 接口获取到的任务 ID。 |

**响应参数 (Response)**
返回纯文本字符串：`success`

---

## ⚙️ 2. 执行流程详解 (内部机制)

### 阶段 1：全局任务拉取 (`/list`)
执行 SQL 查询 `reminders` 表，过滤出所有 `is_completed = false`（未完成）的任务，并严格按照 `next_remind_time ASC`（时间正序）排列，保证最紧急的任务排在最前面展示。
```sql
SELECT * FROM reminders WHERE user_id = ? AND is_completed = false ORDER BY next_remind_time ASC
```

### 阶段 2：实时轮询触发机制 (/check)
前端通过 setInterval 每隔数秒发起请求。

触发条件：除了限制 user_id 和未完成状态，最核心的是加入了 next_remind_time <= NOW() 的判定。

性能优化：由于是高频轮询接口，为了节约数据库带宽，SQL 中明确指定只 SELECT reid, task_name, repeat_type，不返回多余字段。

### 阶段 3：任务状态机与周期管理 (/complete)
当调用核销接口时，后端不是简单地将任务置为已完成，而是根据 repeat_type 走入不同的状态分支：

先查明身份：通过传入的 reid 查询该任务的 repeat_type（循环类型）。

分支流转（核心处理）：

一次性任务 (repeat_type == 0 或 null)：直接执行 UPDATE 语句，将 is_completed 标记为 1 (true)。该任务此后将永远从 /list 和 /check 接口中消失。

每日重复 (repeat_type == 1)：不修改完成状态！而是利用 MySQL 的日期函数 DATE_ADD(next_remind_time, INTERVAL 1 DAY)，将下次触发时间精确向后推移 24 小时。

每周重复 (repeat_type == 2)：同样不修改完成状态，利用 DATE_ADD(next_remind_time, INTERVAL 1 WEEK)，将触发时间推延到下周的同一时间。

这种巧妙的循环更新设计，保证了周期性任务可以无限流转，且永远只需在数据库中维护一条核心记录。