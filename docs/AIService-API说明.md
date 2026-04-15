# 🧠 AIService 大模型调度接口文档

`AIService` 是系统的“对话引擎层”。它的核心职责是将前端收集到的对话历史进行封装，注入极其严密的“老人陪护系统人设（System Prompt）”，并与外部大语言模型（Moonshot AI）通过 HTTP API 进行通信，最终提取出具有特殊标记的文本返回给控制层。

---

## 1. 核心方法详解

### `getAIResponse(List<Map<String, String>> conversationHistory)`

**功能描述：** 接收一段按时间排序的历史对话记录，注入系统设定后，向 Kimi AI 发起对话请求，并返回 AI 生成的响应内容。

#### 📥 1.1 参数说明
| 参数名 | 类型 | 描述 | 示例 |
| :--- | :--- | :--- | :--- |
| `conversationHistory` | `List<Map<String, String>>` | 按时间正序排列的对话历史记录列表。每个 Map 必须包含 `role` ("user" 或 "assistant") 和 `content` (对话内容)。 | `[{"role":"user","content":"我腿疼"}, {"role":"assistant","content":"怎么了爷爷？"}]` |

#### 📤 1.2 返回结果
| 类型 | 描述 |
| :--- | :--- |
| `String` | AI 返回的原始文本（包含剥离前的 `[EMOTION]` 或 `[REMINDER_TASK]` 等内部通信标签）。如果网络故障，返回容错话术。 |

---

## ⚙️ 2. 执行流程与黑科技解析

整个 `getAIResponse` 方法可以拆分为以下 4 个核心运作步骤：

### 阶段 1：构建带时间戳的超级 Prompt (核心)
这部分是整个类的精髓。我们没有让 AI 自由发挥，而是给它戴上了极其严密的“紧箍咒”：

1. **动态时间戳注入**：
    - 每次调用前，实时获取服务器时间 `currentTime`（如：`2026-04-15 20:50`）。
    - **目的**：大模型自身没有时间概念，将当前时间硬编码塞进 Prompt，AI 才能理解“10分钟后”到底是几点几分，从而生成精确的闹钟提醒。
2. **人设与安全红线 (System Prompt)**：
    - 强制 AI 扮演“豆小包”（贴心孙辈）。
    - **安全熔断机制**：严禁推荐具体药物（这是医疗类 AI 产品的红线），必须回答“不知道”，杜绝 AI 幻觉（编造假药）。
    - 限制字数，防止长篇大论导致老人阅读困难（和长文本转语音带来的接口计费压力）。
3. **“协议级”的标签输出要求**：
    - 通过提供【严格示例学习】（Few-Shot Prompting），强制 AI 扮演一个“标签生成器”。
    - 当老人有情绪波动时，AI 必须在回复末尾附带如 `[EMOTION]身体不适[/EMOTION]`。
    - 当有提醒需求时，必须输出如 `[REMINDER_TASK]吃药|2026-04-15 21:00:00|ONCE[/REMINDER_TASK]` 供 `ChatController` 剥离解析入库。

### 阶段 2：组装对话上下文消息体
将构造好的超级 `SystemPrompt` 作为第一条消息（`role: "system"`）塞入列表头部，随后把 `ChatController` 传过来的 `conversationHistory`（通常是最近10条聊天记录）附加在后面，形成一个完整的记忆链条。

### 阶段 3：构建 HTTP 请求与参数调优
组装发给 Kimi 的 JSON 格式的 Body：
```json
{
  "model": "moonshot-v1-8k",
  "temperature": 0.7,
  "messages": [ { "role": "system", "content": "..." } ]
}
```

### 阶段 4：发起调用与容错解析
利用 OkHttpClient 发起同步网络请求：

容错处理：如果 HTTP 状态码不是 200，或者网络断开，直接返回兜底话术 "哎呀，不好意思，豆包没听清。"，防止前端直接看到冷冰冰的英文字母报错。

深度解析：如果成功，利用 FastJSON 解析出深层嵌套的结果：json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")，将其直接丢给上一层的 ChatController 进行剥离与入库。