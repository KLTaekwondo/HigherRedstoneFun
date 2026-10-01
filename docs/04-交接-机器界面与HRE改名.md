# 04 · 交接文档：机器界面与 HRE 改名

> 写于 2026-10-01。上一个会话因**上下文过长触发 API 400**而中断，
> 本文档用于让下一个会话无缝接手。

---

## 一、当前进度

最后一个提交：`机器界面重构 + 红石燃料 + 增强工作台54格`

### ✅ 已完成并提交

| 项目 | 说明 |
|---|---|
| **燃料改成纯红石** | `FuelValues` 重写，只认 `REDSTONE` / `REDSTONE_BLOCK`，煤/木板/岩浆**一律返回 0**（刻意，非遗漏） |
| **修掉 3×3 只有 2×3 的 bug** | 增强工作台 `inventorySize` 27 → 54；旧输入槽 `28,29,30` 在 27 格里越界被 `MachineInstance.setSlot` 静默丢弃 |
| **增强工作台新布局** | 54 格，左 3×3 输入 / 右中心出产物 / 右上角红石燃料 / 右列竖直燃料条 |
| **燃料槽可配置** | `RecipeMachineLogic` 新增 `fuelSlot` 字段与三参构造；`CraftingTableLogic` 用 slot 8 |
| **手动合成消耗燃料** | 每次合成 100 tick（1 红石 = 16 次，1 红石块 = 144 次） |
| **机器界面渲染** | `MachineMenu` 重写：功能槽以外的格子铺灰玻璃边框、右侧竖直能量条、状态位显示进度/燃料/剩余次数 |

### ❌ 未完成（下一个会话的任务）

1. **HRE 单位改名**：机器说明里的 `J` → `HRE`
2. **系统名改叫「红石流能」**：替换所有作为*系统名*的「电力」
3. **燃煤发电机 → 红石发电机**
4. **「燃料: 可燃物」→「燃料: 红石」**
5. **`SelfTest` 里旧的布局/燃料断言需要同步**（现在很可能跑不过）
6. **27 格机器的界面未实机验证**（`MachineMenu` 是通用实现，但只有 54 格那台被设计过）
7. **`config.yml` 里燃料相关注释**

---

## 二、命名决定（已定，不要再讨论）

```
系统名   红石流能        （取代"电力"）
单位     HRE            （Higher Redstone Energy，取代"J"/"焦耳"）
燃料     红石 / 红石块
```

### 三层结构

```
第一层   手动     增强工作台      消耗红石（每次合成 100 tick）
第二层   红石     基础机器        烧红石
第三层   红石流能  高级机器        能源网络 + HRE
```

### 为什么红石不做成"第三种能源"

「烧红石」和「烧煤」机制完全一样，只是物品不同。做成独立能源只会多一套概念、
玩法上没有任何新东西。所以红石归入**燃料层**，作为其中（也是唯一）的燃料。

---

## 三、关键槽位表（改代码必读）

### 3.1 图鉴 `ui/GuideMenu.java`（45 格 = 5×9）

```
行1 (0-8)    红玻璃 + 标签栏(2,3,4)
行2-4 (9-35) 全部留空，只放当前标签的内容
行5 (36-44)  蓝玻璃 + 按钮
```

| 常量 | 值 | 用途 |
|---|---|---|
| `SLOT_SUBJECT` | 19 | 主题位（合成方式页=机器；结构页=搭建步骤牌） |
| `SLOT_PREVIEW` | 25 | 预览位（产物 / 结构页=机器本身） |
| `SLOT_RECIPE_GRID` | 12,13,14 / 21,22,23 / 30,31,32 | 3×3 九宫格 |
| `SLOT_STRUCTURE_COLUMN` | 31,22,13 | 结构竖列（从下到上） |
| `SLOT_PREV_RECIPE` / `NEXT` | 28 / 34 | 配方翻页 |
| `SLOT_BACK` / `CLOSE` | 36 / 44 | 返回 / 关闭 |
| `SLOT_PREV_PAGE` / `INFO` / `NEXT_PAGE` | 38 / 40 / 42 | 列表页翻页 |
| `SLOT_CURRENT_ITEM` | 40 | 最后一行正中，**显示当前查看的物品** |
| `SLOT_USAGE_PREV` / `NEXT` | 38 / 42 | 用途翻页（与列表页翻页共用槽位，靠标签区分） |
| `LIST_PAGE_SIZE` / `LIST_START` | 27 / 9 | 列表页每页格数与起始槽 |
| `USAGE_SLOTS` | 9..35 | 用途页整块可用 |

**标签顺序**：`RECIPE → STRUCTURE → USAGE`，**按需显示**（没内容就不出现按钮）。

**已知坑（踩过 3 次）**：底部固定槽位容易被标签内容误用导致提示被覆盖。
`SelfTest` 里有「详情页所有槽位唯一」的静态校验守着。

### 3.2 增强工作台 `machines/MachineMenu.java`（54 格 = 6×9）

```
列     1    2    3    4    5    6    7    8    9
行1   [框] [框] [框] [框] [框] [框] [框] [框] [燃]   <- 8  = 红石燃料槽
行2   [入] [入] [入] [框] [框] [出] [出] [出] [能]   <- 17
行3   [入] [入] [入] [框] [框] [出] [出] [出] [能]   <- 26
行4   [入] [入] [入] [框] [框] [出] [出] [出] [能]   <- 35
行5   [框] [框] [框] [状] [框] [框] [框] [框] [能]   <- 39=状态, 44
行6   [框] [框] [框] [框] [框] [框] [框] [框] [能]   <- 53
```

| 常量（在 `recipes/RecipeType.java`） | 值 |
|---|---|
| `CRAFTING_INPUT_AREA` / `ENHANCED_CRAFTING.inputSlots()` | 9,10,11 / 18,19,20 / 27,28,29 |
| `ENHANCED_CRAFTING.outputSlots()` | `{24}`（结果区中心） |
| `CRAFTING_OUTPUT_AREA` | 24,14,15,16,23,25,32,33,34（视觉 3×3，目前只用 24） |
| `CRAFTING_FUEL_SLOT` | 8 |
| `CRAFTING_ENERGY_COLUMN` | 17,26,35,44,53（从上到下） |
| `CRAFTING_STATUS_SLOT` | 39 |

**通用规则**：`MachineMenu.isFunctionalSlot(slot)` = 输入槽 ∪ 输出槽 ∪ 燃料槽。
其余格子由 `renderFrame()` 铺灰玻璃。新增机器只要在 `RecipeType` 里声明槽位就自动生效。

---

## 四、下一个会话要做的事

### 任务 1：HRE 单位改名（`J` → `HRE`）

涉及文件（用 `grep` 找 `" J/t"`、`" J"`、`joule`、`焦耳`）：

- `content/PowerMachines.java` — 机器 lore 里的 `8 J/t`、`(≈160 J/s)`
- `content/GeneticsMachines.java` — `12 J/t` 等
- `ui/GuideMenu.java` — `buildEntry()` 里的 `产能/耗电/储电` 三行
- `recipes/MachineRecipe.java` — `energyCost` 的注释与显示
- `energy/EnergyNetwork.java` — 累计耗电那行
- `energy/EnergyNode.java` — 注释里的「焦耳」
- `machines/MachineLogic.java` — 注释
- `commands/SelfTest.java` — 测试输出里的 `J`

**注意**：`MachineMenu` 里已经用了 `红石流能: xxx HRE`，可以当参考文案。

### 任务 2：系统名「电力」→「红石流能」

**只改作为"系统名"出现的「电力」，不要改"电力机器"这类产品名之外的东西**（产品名本身也要改）：

- `core/ItemGroup.java` — `POWER_MACHINES("高级电力机器", "<red>以焦耳(J)为能量单位…")`
- `ui/GuideSection.java` — `MACHINES("机器", "<gray>基础机器与高级电力机器", …)`
- `ui/GuideMenu.java` — 主页面简介 `基础机器 · 高级电力机器 · 基因工程`
- `commands/HrfCommand.java` — `三个模块: …高级电力机器…`
- `content/PowerMachines.java` — 类注释
- `recipes/RecipeType.java` — `电力熔炼` / `电力研磨` 的 `displayName`

**建议的替换**：`高级电力机器` → `高级红石流能机器`（或简称 `红石流能机器`）。
首次出现的地方要加一句解释，降低玩家的理解成本。

### 任务 3：燃煤发电机 → 红石发电机

`content/PowerMachines.java` 里的 `hrf_coal_generator`：

- `displayName`：`燃煤发电机` → `红石发电机`
- lore：`最基础的发电机，烧燃料产电` → 说明烧红石
- 检查 `GeneratorLogic` 是否用 `FuelValues`（它用 `consumeFuelItem`，应该已经自动变成只吃红石）

### 任务 4：「燃料: 可燃物」→「燃料: 红石」

- `ui/GuideMenu.java` 的 `buildEntry()`：`if (definition.usesFuel()) lore.add("<gray>燃料: <gold>可燃物");`
- `machines/MachineRegistry.java` 已不再自动追加燃料行（早先清理过），确认没有残留
- `machines/MachineDefinition.java` — 若有 `fuelTicksPerUnit()` 相关文案

### 任务 5：`SelfTest` 同步

现在**很可能跑不过**，需要更新：

- 增强工作台相关：槽位数 27 → 54、输出槽 16 → 24
- 燃料相关：把「煤可用」的断言改成「只有红石可用」，并**加一条反向断言「煤不可用」**
- 多方块结构 / 图鉴布局断言（图鉴已改成 45 格）
- 建议加：**「增强工作台每次合成真的扣了 100 tick 燃料」** 的断言
- 建议加：**「输入槽 9 格全部有效」** 的回归断言（防 3×3 变回 2×3）

### 任务 6：其它机器的界面

`MachineMenu` 是通用实现，27 格机器（研磨/压制/熔炼/离心/锯切 + 电力版）应该也能用，
但**没实机验证过**。重点看：

- 状态位回退逻辑（`statusSlot()` 找不到 `39` 就取第一个空闲槽）
- 燃料槽 `0` 在 27 格机器里是左上角，视觉上是否合适
- 组装机 / 分子重组机是 45 格，需要单独看

---

## 五、工作流程（重要，别搞错）

### 用户的规则（必须遵守）

> **默认只编译 + 打包 jar，不要起服务器、不要跑 `/hrf selftest`、不要读日志。**
> 用户自己进游戏测。

只有在以下情况才升级到全量测试：

- 用户**说不清楚问题**
- 同一个 bug **我猜错两次**
- 问题**无法从代码定位**（时序 / 客户端渲染）

### 命令

```powershell
# 编译打包
cd D:\Projects\HigherRedStoneFun
.\gradlew.bat build --no-daemon -q

# 部署到测试服
Copy-Item "build\libs\HigherRedStoneFun-0.1.0.jar" "testserver\plugins\" -Force
```

用户启动服务器：双击 `testserver\start-server.bat`（已配好 Java 25 检测 + 2GB 内存）。

**环境注意**：

- `JAVA_HOME` 指向 **Java 21**（Minecraft 自带），而 Paper 26.3 需要 **Java 25+**。
  构建用 Gradle 的 `org.gradle.java.installations.paths`；启动脚本会自己找 JDK 25。
- PowerShell 里传 JVM 参数必须加引号：`"-Dhrf.selftest=true"`，否则会被拆坏。
- 批处理文件必须 **CRLF** 换行。

---

## 六、写代码时的注意事项

### 物品 tooltip 只放"一句话"

**原则：物品上只说「这是什么」，图鉴里说「怎么用 / 怎么做」。**

- `MachineDefinition.lore()` 的**第一行是物品摘要**，其余进图鉴
- `MachineRegistry.register()` 只取 `lore[0]` + `左键查看详情`
- 不要再往物品上堆产能/燃料/结构/配方类型

### 多方块结构里全是原版方块

显示结构时必须用**原版方块名**（`core/Materials.blockName()`），
不能用机器名——曾经把「工作台」写成「增强工作台」，会误导玩家。

### 底部槽位易被覆盖

`GuideMenu` 的底部固定槽位（36/38/40/42/44）已经被内容区误用坑了 3 次。
改任何渲染方法后，跑一下 `SelfTest` 的「详情页所有槽位唯一」断言。

---

## 七、给下一个会话的 Prompt

```
继续开发 D:\Projects\HigherRedStoneFun（Paper 26.3 插件，Java 25）。

════════════════════════════════════════════════════════
第一步：先读文档，不要跳过
════════════════════════════════════════════════════════

docs/ 下已有 5 份文档，按这个顺序读：

【必读 · 全篇读，共约 400 行】
  1. docs/02b-架构要点速查.md          ← 先读这个，整体架构速查
  2. docs/03-玩法升级与优化设计.md      ← 设计意图与取舍
  3. docs/04-交接-机器界面与HRE改名.md  ← 当前进度、槽位表、本次任务

【按需查 · 很大，不要整篇读，用 grep 定位】
  4. docs/01-粘液科技玩法调研与核心统计.md   (768 行)
  5. docs/02-Slimefun4源码架构分析.md        (1606 行)

  ⚠️ 这两份是前期调研，只在需要参考粘液科技的具体做法时用 grep 查关键词，
     不要整篇读进来——上下文会被撑爆。上一个会话就是因为上下文过长
     触发 API 400 而中断的。

【源码参考（要用时再查，同样不要整篇读）】
  .research/Slimefun4/            粘液科技源码
  .research/paper-api-sources/    Paper API 源码

读完 1~3 之后，先跟我确认你理解的当前状态和待办清单，再动手。

════════════════════════════════════════════════════════
第二步：三件任务，按顺序做
════════════════════════════════════════════════════════

1. HRE 单位改名：机器说明里的 "J" 全部换成 "HRE"（例如 "8 J/t" → "8 HRE/t"）。
   涉及 PowerMachines / GeneticsMachines / GuideMenu / MachineRecipe /
   EnergyNetwork / EnergyNode / SelfTest。

2. 系统名「高级电力机器」改成「红石流能」系：
   ItemGroup.POWER_MACHINES、GuideSection、GuideMenu 主页面简介、
   HrfCommand、PowerMachines 类注释、RecipeType 里的"电力熔炼/电力研磨"。
   同时删掉"焦耳(J)"这类旧单位文案。
   （red：命名决定已在文档第二节定死，不要再重新讨论）

3. 燃煤发电机改成红石发电机（hrf_coal_generator，在 PowerMachines 里）：
   改 displayName 和 lore，燃料说明改成红石。

另外：
- ui/GuideMenu.java 里 buildEntry() 的 "燃料: 可燃物" 要改成 "燃料: 红石"。
- 改完后同步 commands/SelfTest.java 里会失败的断言（增强工作台现在是 54 格、
  输出槽是 24、燃料只有红石），并加两条回归断言：
  (a) 9 个输入槽全部有效（防 3×3 退化成 2×3）
  (b) 煤不能当燃料、红石可以

════════════════════════════════════════════════════════
工作流程（重要）
════════════════════════════════════════════════════════

只编译 + 打包 jar，然后复制到 testserver\plugins\。
不要起服务器、不要跑 /hrf selftest、不要读日志——我自己进游戏测。

  .\gradlew.bat build --no-daemon -q
  Copy-Item "build\libs\HigherRedStoneFun-0.1.0.jar" "testserver\plugins\" -Force

环境坑：JAVA_HOME 指向 Java 21（MC 自带），Paper 26.3 要 Java 25+；
PowerShell 传 -D 参数要加引号；bat 文件必须 CRLF。
```
