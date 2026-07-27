# 自定义列车门

为 Minecraft 添加真实的列车滑动门，支持原版红石控制、手动交互，以及 Create 机械动力模组的列车联动。

---

## 内置车门

| 车门 | 现实原型 | 动画特点 |
|------|---------|---------|
| **CR400BF Train Door** | 复兴号 CR400BF | Lerped 平滑滑动（120 ticks），带深度弹出效果 |
| **CRH2A Train Door** | 和谐号 CRH2A | 分阶段动画（130 ticks）：关门先快后慢+防夹停顿 |

两种门均支持：
- 右键手动开关
- 红石信号控制
- 双开门联动
- Create 列车 / 动态结构上的自动开关门

---

## 依赖

- Minecraft 1.21.1
- NeoForge 21.1+
- [Create 6.0+](https://modrinth.com/mod/create) (NeoForge)

---

## 自定义车门系统

将自定义车门 ZIP 包放入游戏目录下的 `tarindoor/` 文件夹，mod 会在启动时自动读取并注册。


### 快速使用

1. 启动游戏一次，自动创建 `tarindoor/` 文件夹
2. 将 `.zip` 文件放入该文件夹
3. 重启游戏

> 自定义门会注册方块、物品和方块实体，因此新增、删除或修改 `door.json`
> 后必须重启游戏；Minecraft 的 `/reload` 只能重载数据包，不能修改注册表。

---

### ZIP 文件格式

```
my_train_door.zip
├── door.json          # 必需 — 门定义文件 / required
├── side.png           # 必需 — 侧面纹理 / side texture
├── top.png            # 必需 — 顶部纹理 / top texture
├── bottom.png         # 必需 — 底部纹理 / bottom texture
├── door_open.ogg      # 可选 — 开门音效（若未指定 sound_event）
└── door_close.ogg     # 可选 — 关门音效（若未指定 sound_event）
```

---

### door.json 完整 Schema

```jsonc
{
  // --- 必填 / Required ---
  "id": "my_door",                    // 唯一标识符，仅小写字母数字下划线
                                       // Unique ID, lowercase alphanumeric + underscore

  "name": {                           // 本地化名称 / Localized display name
    "en_us": "My Train Door",
    "zh_cn": "我的列车门"
  },

  // --- 动画配置 / Animation ---
  "animation": {
    // "lerped" — 平滑插值动画 / smooth interpolation
    // "phased" — 分阶段关键帧动画 / keyframe-based phased animation
    "type": "lerped",

    // type=lerped 时使用：chase 速度（1/120 ≈ 0.00833）
    "speed": 0.00833,

    // type=phased 时使用：总时长（tick 数，20 tick = 1 秒）
    "total_ticks": 130,

    // type=phased 时使用：阶段序列
    // phase 类型："pause"（保持当前值）或 "animate"（线性过渡）
    "opening": [                      // 开门阶段
      {"type": "pause",   "duration": 72},
      {"type": "animate", "duration": 58}
    ],
    "closing": [                      // 关门阶段
      {"type": "animate", "duration": 90},
      {"type": "pause",   "duration": 6},
      {"type": "animate", "duration": 34}
    ]
  },

  // --- 渲染配置 / Rendering ---
  "render": {
    // 滑动距离倍率 / slide distance multiplier（value² × slide_scale）
    "slide_scale": 0.8125,

    // 深度弹出效果 / optional depth push effect
    "depth_push": {
      "enabled": true,                 // 是否启用
      "clamp_multiplier": 12.0,       // 钳制乘数
      "scale": 0.1                    // 深度位移量（格）
    }
  },

  // --- 方块属性 / Block Properties ---
  "block": {
    "hardness": 5.0,                  // 硬度（可选，默认 5.0）
    "resistance": 6.0,                // 爆炸抗性（可选，默认 6.0）
    "map_color": "metal",             // 地图颜色（可选，默认 metal）
    "sound_type": "netherite_block",  // 方块音效类型（可选，默认 netherite_block）

    // === 音效方式一：ZIP 内置音效文件 / Custom OGG files in ZIP ===
    "open_sound_file": "door_open.ogg",
    "close_sound_file": "door_close.ogg",

    // === 音效方式二：复用已有 SoundEvent / Reuse existing SoundEvent ===
    // 优先级高于方式一 / Takes priority over file-based sounds
    // "sound_event": {
    //   "open": "custom_train_door:cr400bf_door_open",
    //   "close": "custom_train_door:cr400bf_door_close"
    // }
  },

  // --- 合成配方 / Recipe（可选 / optional）---
  "recipe": {
    "pattern": ["II", "II", "II"],
    "keys": {"I": "minecraft:iron_ingot"},
    "count": 1
  }
}
```

---

### 可用内置音效

| SoundEvent | 说明 |
|-----------|------|
| `custom_train_door:cr400bf_door_open` | CR400BF 开门 |
| `custom_train_door:cr400bf_door_close` | CR400BF 关门 |
| `custom_train_door:crh2a_door_open` | CRH2A 开门 |
| `custom_train_door:crh2a_door_close` | CRH2A 关门 |

### 可用 MapColor

`none` `grass` `sand` `wool` `fire` `ice` `metal` `plant` `snow` `clay` `dirt` `stone` `water` `wood` `quartz` `gold` `diamond` `lapis` `emerald` `nether` `color_orange` `color_light_blue` `color_yellow` `color_pink` `color_gray` `color_light_gray` `color_cyan` `color_purple` `color_blue` `color_brown` `color_green` `color_red` `color_black` 等

### 可用 SoundType

`wood` `stone` `metal` `glass` `wool` `sand` `gravel` `netherite_block` `anvil` `slime_block` `copper` `deepslate` `amethyst` `bamboo` 等

---

### 配置示例

**最简 lerped 门（复用 CR400BF 音效）：**

```json
{
  "id": "simple_door",
  "name": {"zh_cn": "简易车门"},
  "animation": {"type": "lerped", "speed": 0.00833},
  "render": {"slide_scale": 0.8125},
  "block": {
    "sound_event": {
      "open": "custom_train_door:cr400bf_door_open",
      "close": "custom_train_door:cr400bf_door_close"
    }
  }
}
```

**带深度弹出 + 分阶段动画 + 自定义音效：**

```json
{
  "id": "fancy_door",
  "name": {"en_us": "Fancy Door", "zh_cn": "精致车门"},
  "animation": {
    "type": "phased",
    "total_ticks": 160,
    "opening": [
      {"type": "pause",   "duration": 60},
      {"type": "animate", "duration": 100}
    ],
    "closing": [
      {"type": "animate", "duration": 100},
      {"type": "pause",   "duration": 10},
      {"type": "animate", "duration": 50}
    ]
  },
  "render": {
    "slide_scale": 0.9,
    "depth_push": {"enabled": true, "clamp_multiplier": 10.0, "scale": 0.15}
  },
  "block": {
    "hardness": 7.0,
    "resistance": 8.0,
    "open_sound_file": "door_open.ogg",
    "close_sound_file": "door_close.ogg"
  },
  "recipe": {
    "pattern": ["GG", "GG", "GG"],
    "keys": {"G": "minecraft:gold_ingot"},
    "count": 1
  }
}
```
