# Custom Train Door / 自定义列车门

[中文](#中文) · [English](#english) · [自定义门包 / Custom Door Packs](#自定义门包--custom-door-packs)

Custom Train Door 是一个面向 Minecraft 1.21.1 与 Create 的 NeoForge 模组，提供具有独立模型、动画和音效的列车滑动门。

Custom Train Door is a NeoForge mod for Minecraft 1.21.1 and Create. It adds train-style sliding doors with dedicated models, animations, and positional sounds.

## 中文

### 主要功能

- 内置 CR400BF 与 CRH2A 两种列车门。
- 支持右键开关、红石控制和双开门联动。
- 支持 Create 列车及其他动态结构上的开关门交互。
- 组装后仍会播放对应车门的开关门音效。
- 支持通过 ZIP 包添加自定义纹理、动画、名称、音效与合成配方。
- 自定义包会在载入前检查 ID、路径、文件大小和必要资源，错误包会被跳过。

### 内置车门

| 车门 | 现实原型 | 动画 |
|---|---|---|
| CR400BF Train Door | 复兴号 CR400BF | 平滑插值滑动，并带有深度弹出效果 |
| CRH2A Train Door | 和谐号 CRH2A | 分阶段开关动画，包含停顿和速度变化 |

### 环境要求

| 组件 | 版本 |
|---|---|
| Minecraft | 1.21.1 |
| Mod Loader | NeoForge 21.1.x |
| Create | 6.0.x，NeoForge 版 |
| Java | 21 |

### 安装

1. 安装 Minecraft 1.21.1、NeoForge 和 Create。
2. 将本模组 JAR 放入游戏实例的 `mods/` 文件夹。
3. 启动游戏。

> 客户端和服务端都需要安装本模组及其依赖。

## English

### Features

- Includes CR400BF and CRH2A train doors.
- Supports manual interaction, redstone control, and paired double doors.
- Works on assembled Create trains and other moving contraptions.
- Preserves the correct open and close sounds after assembly.
- Loads custom textures, animations, names, sounds, and recipes from ZIP packs.
- Validates custom pack IDs, paths, file sizes, and required resources before registration.

### Built-in Doors

| Door | Real-world inspiration | Animation |
|---|---|---|
| CR400BF Train Door | CR400BF Fuxing EMU | Smooth lerped motion with an optional depth push |
| CRH2A Train Door | CRH2A Hexie EMU | Phased motion with pauses and speed changes |

### Requirements

| Component | Version |
|---|---|
| Minecraft | 1.21.1 |
| Mod Loader | NeoForge 21.1.x |
| Create | 6.0.x for NeoForge |
| Java | 21 |

### Installation

1. Install Minecraft 1.21.1, NeoForge, and Create.
2. Place the mod JAR in the instance's `mods/` directory.
3. Start the game.

> The mod and its dependencies are required on both the client and the server.

## 自定义门包 / Custom Door Packs

首次启动后，模组会在游戏目录创建 `tarindoor/`。将自定义门 ZIP 直接放入该目录并重启游戏。

On first launch, the mod creates a `tarindoor/` directory in the game folder. Put custom door ZIP files directly in that directory and restart the game.

> 自定义门会注册方块、物品和方块实体，因此新增、删除或修改 `door.json` 后必须重启游戏。`/reload` 无法修改注册表。
>
> Custom doors register blocks, items, and block entities. A full game restart is required after adding, removing, or changing `door.json`; `/reload` cannot update registries.

### ZIP 结构 / ZIP Layout

```text
my_train_door.zip
├── door.json          # 必需 / required
├── side.png           # 必需 / required
├── top.png            # 必需 / required
├── bottom.png         # 必需 / required
├── door_open.ogg      # 可选 / optional
└── door_close.ogg     # 可选 / optional
```

所有文件应直接位于 ZIP 根目录。建议使用单声道 OGG，以获得正确的 3D 距离衰减效果。

All files should be placed at the ZIP root. Mono OGG files are recommended for correct positional attenuation.

### `door.json` 示例 / Example

```json
{
  "id": "my_door",
  "name": {
    "zh_cn": "我的列车门",
    "en_us": "My Train Door"
  },
  "animation": {
    "type": "lerped",
    "speed": 0.00833
  },
  "render": {
    "slide_scale": 0.8125,
    "depth_push": {
      "enabled": true,
      "clamp_multiplier": 12.0,
      "scale": 0.1
    }
  },
  "block": {
    "hardness": 5.0,
    "resistance": 6.0,
    "map_color": "metal",
    "sound_type": "netherite_block",
    "open_sound_file": "door_open.ogg",
    "close_sound_file": "door_close.ogg"
  },
  "recipe": {
    "pattern": ["II", "II", "II"],
    "keys": {
      "I": "minecraft:iron_ingot"
    },
    "count": 1
  }
}
```

### 动画类型 / Animation Types

`lerped` 使用平滑追踪速度：

`lerped` uses a smooth chase speed:

```json
{
  "animation": {
    "type": "lerped",
    "speed": 0.00833
  }
}
```

`phased` 使用由暂停和线性过渡组成的阶段序列：

`phased` uses a sequence of pause and linear animation phases:

```json
{
  "animation": {
    "type": "phased",
    "total_ticks": 130,
    "opening": [
      {"type": "pause", "duration": 72},
      {"type": "animate", "duration": 58}
    ],
    "closing": [
      {"type": "animate", "duration": 90},
      {"type": "pause", "duration": 6},
      {"type": "animate", "duration": 34}
    ]
  }
}
```

### 复用已有音效 / Reusing Sound Events

自定义 OGG 也可以替换为已有的 `SoundEvent`。如果事件不存在，模组会回退到原版铁门音效。

Custom OGG files can be replaced with existing `SoundEvent` IDs. Missing events fall back to the vanilla iron door sounds.

```json
{
  "block": {
    "sound_event": {
      "open": "custom_train_door:cr400bf_door_open",
      "close": "custom_train_door:cr400bf_door_close"
    }
  }
}
```

内置事件 / Built-in events:

- `custom_train_door:cr400bf_door_open`
- `custom_train_door:cr400bf_door_close`
- `custom_train_door:crh2a_door_open`
- `custom_train_door:crh2a_door_close`

## 常见问题 / Troubleshooting

### 显示紫黑色错误材质 / Purple-black missing texture

确认 ZIP 根目录包含 `side.png`、`top.png` 和 `bottom.png`，文件名与 `door.json` 引用完全一致，并在修改后完整重启游戏。

Make sure `side.png`, `top.png`, and `bottom.png` exist at the ZIP root, match the names referenced by `door.json`, and restart the game after making changes.

### 声音没有距离衰减 / Sound has no positional attenuation

将音效转换为单声道 OGG。立体声音频通常会被当作非定位音频播放。

Convert the audio to mono OGG. Stereo audio is commonly treated as non-positional audio.

### `/reload` 后没有出现新车门 / New doors do not appear after `/reload`

这是预期行为。自定义门涉及注册表内容，必须重启游戏。

This is expected. Custom doors add registry content and require a full restart.

## License

All Rights Reserved. See the repository metadata for the applicable version.

## Links

- Source / 源码: <https://github.com/yasui-i/custom_train_door>
- Issues / 问题反馈: <https://github.com/yasui-i/custom_train_door/issues>
