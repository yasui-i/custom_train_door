## English Short Summary

Interactive CR400BF and CRH2A sliding train doors for Create on Minecraft 1.21.1, with ZIP-based custom textures, animations, sounds, and recipes.

## 中文简短介绍

为 Minecraft 1.21.1 的 Create 列车添加可交互的 CR400BF、CRH2A 滑动门，并支持通过 ZIP 扩展自定义门材质、动画、音效和配方。

---

## English Full Description

# Custom Train Door

Custom Train Door adds train-style sliding doors to Create on Minecraft 1.21.1.

The mod includes CR400BF and CRH2A doors with distinct models, textures, animation timing, and open/close sounds. The doors work both as regular blocks and as part of assembled Create contraptions.

## Features

- CR400BF and CRH2A sliding train doors
- Right-click interaction
- Redstone control
- Paired double-door behavior
- Create train and moving contraption support
- Correct per-door sounds after assembly
- Positional mono train-door sounds
- ZIP-based custom door pack system
- Custom localized names, textures, animations, sounds, and recipes
- Validation for custom pack IDs, paths, sizes, and required resources

## Custom Door Packs

The mod creates a `tarindoor/` directory in the game folder after the first launch. Place custom door ZIP files there and restart the game to load them.

A door pack can provide:

- A `door.json` definition
- Side, top, and bottom textures
- Smooth or phased animations
- Custom OGG open and close sounds
- Existing Minecraft or modded SoundEvents
- Localized display names
- A custom crafting recipe

> Custom doors register new blocks, items, and block entities. Restart the game after changing door packs; `/reload` cannot update registries.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.x
- Create 6.0.x for NeoForge
- Java 21

The mod and its dependencies are required on both the client and the server.

## Installation

1. Install NeoForge and Create.
2. Place the Custom Train Door JAR in `mods/`.
3. Start the game.

## Compatibility

- Currently built for Minecraft 1.21.1 NeoForge only.
- Fabric and Forge builds of Create are not supported.
- Mono OGG files are recommended for correct 3D positional attenuation.

## Links

- Source: <https://github.com/yasui-i/custom_train_door>
- Issue tracker: <https://github.com/yasui-i/custom_train_door/issues>

---

## Suggested Platform Metadata

| Field | Value |
|---|---|
| Project name | Custom Train Door / 自定义列车门 |
| Version | 1.0.0 |
| Game version | Minecraft 1.21.1 |
| Mod loader | NeoForge |
| Required dependency | Create |
| Environment | Client and server |
| License | All Rights Reserved |
| Categories | Technology, Transportation, Decoration, Addons |

## Suggested Search Terms

`Create`, `train`, `train door`, `sliding door`, `CR400BF`, `CRH2A`, `railway`, `contraption`, `列车`, `车门`, `滑动门`, `机械动力`
## 中文完整介绍

# 自定义列车门

Custom Train Door 为 Minecraft 1.21.1 的 Create 模组添加更接近真实列车的滑动门。

模组内置 CR400BF 和 CRH2A 两种车门。它们拥有不同的模型、材质、动画节奏和开关门音效，并支持普通放置和 Create 动态结构。

## 功能

- CR400BF 与 CRH2A 列车滑动门
- 右键手动开关
- 红石信号控制
- 双开门联动
- Create 列车和动态结构支持
- 组装后正常播放对应开关门音效
- 带距离衰减的单声道列车门音效
- ZIP 自定义门包系统
- 自定义本地化名称、纹理、动画、音效和合成配方
- 自定义包路径、ID、文件大小和必要资源检查

## 自定义门包

首次启动后，游戏目录会生成 `tarindoor/` 文件夹。将自定义门 ZIP 放入该文件夹，然后重启游戏即可载入。

一个门包可以包含：

- `door.json` 门定义
- 侧面、顶部和底部纹理
- 平滑或分阶段动画
- 自定义 OGG 开关门音效
- 已注册的 Minecraft/模组 SoundEvent
- 中英文等本地化名称
- 自定义合成配方

> 自定义门会注册新的方块、物品和方块实体，因此修改门包后必须重启游戏，不能使用 `/reload` 热重载。

## 环境要求

- Minecraft 1.21.1
- NeoForge 21.1.x
- Create 6.0.x（NeoForge）
- Java 21

客户端与服务端均需安装本模组及依赖。

## 安装

1. 安装 NeoForge 和 Create。
2. 将 Custom Train Door JAR 放入 `mods/`。
3. 启动游戏。

## 兼容性说明

- 当前仅面向 Minecraft 1.21.1 NeoForge。
- 不支持 Fabric 或 Forge 版本的 Create。
- 自定义音效建议使用单声道 OGG，以保证正确的 3D 距离衰减。

## 链接

- 源码：<https://github.com/yasui-i/custom_train_door>
- 问题反馈：<https://github.com/yasui-i/custom_train_door/issues>

---
