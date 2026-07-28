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

### door.json Schema Reference

Each custom door ZIP must contain a `door.json` file. Below is the complete schema with a detailed example.

#### Top-level Fields

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | string | **Yes** | Unique door identifier. Must match `[a-z0-9_]{1,48}`. Reserved IDs: `cr400bf`, `crh2a`. |
| `name` | object | No | Localized display names, keyed by locale code (e.g. `en_us`, `zh_cn`). |
| `animation` | object | No | Door open/close animation configuration. Defaults to lerped animation. |
| `render` | object | No | Rendering parameters (slide distance, depth push). Defaults to CR400BF-style. |
| `block` | object | No | Block properties (hardness, sound, map color). Defaults to metal/netherite. |
| `recipe` | object | No | Optional shaped crafting recipe. Omit for no crafting recipe. |

#### `animation` Fields

| Field | Type | Required | Description |
|---|---|---|---|
| `type` | string | No | `"lerped"` (default) or `"phased"`. |
| `speed` | number | No | Lerp speed, range `[0.000001, 1.0]`. Default `1/120 ≈ 0.0083`. Only for `lerped` type. |
| `total_ticks` | integer | No | Total animation ticks, default `130`. Only for `phased` type. |
| `opening` | array | No | List of phase objects for the opening sequence. Only for `phased` type. |
| `closing` | array | No | List of phase objects for the closing sequence. Only for `phased` type. |

Each phase object: `{ "type": "pause" | "animate", "duration": <positive ticks> }`

- `pause` — hold the current progress value for the given duration.
- `animate` — linearly interpolate toward the target (fully open or fully closed).

#### `render` Fields

| Field | Type | Required | Description |
|---|---|---|---|
| `slide_scale` | number | No | Slide distance as a fraction of block width, range `[0.0, 4.0]`. Default `0.8125` (13/16). |
| `depth_push` | object | No | Depth-push effect for thin door panels. |
| `depth_push.enabled` | boolean | No | Enable depth push. Default `false`. |
| `depth_push.clamp_multiplier` | number | No | Clamp multiplier, range `[0.0, 100.0]`. Default `12.0`. |
| `depth_push.scale` | number | No | Depth push scale, range `[-4.0, 4.0]`. Default `0.1`. |

#### `block` Fields

| Field | Type | Required | Description |
|---|---|---|---|
| `hardness` | number | No | Block hardness, range `[0.0, 10000.0]`. Default `5.0`. |
| `resistance` | number | No | Blast resistance, range `[0.0, 10000.0]`. Default `6.0`. |
| `map_color` | string | No | Map color name (one of 50+ valid names, see examples below). Default `"metal"`. |
| `sound_type` | string | No | Block sound type (one of 70+ valid names, see examples below). Default `"netherite_block"`. |
| `open_sound_file` | string | No | Filename of an OGG file inside the ZIP for the open sound. |
| `close_sound_file` | string | No | Filename of an OGG file inside the ZIP for the close sound. |
| `sound_event` | object | No | Reference existing registered SoundEvents instead of custom OGG files. |
| `sound_event.open` | string | No | Resource location of an existing open SoundEvent (e.g. `"minecraft:block.iron_door.open"`). |
| `sound_event.close` | string | No | Resource location of an existing close SoundEvent. |

> **Sound priority**: If `sound_event` is provided, it takes precedence over `open_sound_file` / `close_sound_file`. You can use one or the other, but not both for the same action.

Valid `map_color` values: `none`, `grass`, `sand`, `wool`, `fire`, `ice`, `metal`, `plant`, `snow`, `clay`, `dirt`, `stone`, `water`, `wood`, `quartz`, `color_orange`, `color_magenta`, `color_light_blue`, `color_yellow`, `color_light_green`, `color_pink`, `color_gray`, `color_light_gray`, `color_cyan`, `color_purple`, `color_blue`, `color_brown`, `color_green`, `color_red`, `color_black`, `gold`, `diamond`, `lapis`, `emerald`, `podzol`, `nether`, `terracotta_white`, `terracotta_orange`, `terracotta_magenta`, `terracotta_light_blue`, `terracotta_yellow`, `terracotta_light_green`, `terracotta_pink`, `terracotta_gray`, `terracotta_light_gray`, `terracotta_cyan`, `terracotta_purple`, `terracotta_blue`, `terracotta_brown`, `terracotta_green`, `terracotta_red`, `terracotta_black`, `crimson_nylium`, `crimson_stem`, `crimson_hyphae`, `warped_nylium`, `warped_stem`, `warped_hyphae`.

Valid `sound_type` values: `wood`, `gravel`, `grass`, `lily_pad`, `stone`, `metal`, `glass`, `wool`, `sand`, `snow`, `powder_snow`, `ladder`, `anvil`, `slime_block`, `honey_block`, `wet_grass`, `coral_block`, `bamboo`, `bamboo_sapling`, `scaffolding`, `sweet_berry_bush`, `crop`, `hard_crop`, `vine`, `nether_wood`, `cherry_wood`, `bamboo_wood`, `netherite_block`, `ancient_debris`, `bone_block`, `netherrack`, `nylium`, `basalt`, `soul_soil`, `polished_deepslate`, `deepslate`, `deepslate_bricks`, `dripstone_block`, `moss`, `spore_blossom`, `tuff`, `tuff_bricks`, `calcite`, `amethyst`, `amethyst_cluster`, `large_amethyst_bud`, `medium_amethyst_bud`, `small_amethyst_bud`, `pointed_dripstone`, `copper`, `copper_bulb`, `nether_gold_ore`, `nether_ore`, `froglight`, `frogspawn`, `mud`, `muddy_mangrove_roots`, `mud_bricks`, `packed_mud`, `hanging_roots`, `roots`, `moss_carpet`, `cave_vines`, `nether_sprouts`, `azalea`, `azalea_leaves`, `big_dripleaf`, `decorated_pot`, `decorated_pot_cracked`, `trial_spawner`, `vault`, `heavy_core`, `cobweb`, `wet_sponge`.

#### `recipe` Fields

| Field | Type | Required | Description |
|---|---|---|---|
| `pattern` | array of strings | **Yes** | 1–3 crafting rows, each 1–3 characters wide. All rows must have equal width. Space ` ` = empty slot. |
| `keys` | object | **Yes** | Maps each symbol character to an ingredient (item tag or item ID). e.g. `{"I": "minecraft:iron_ingot"}`. |
| `count` | integer | No | Number of doors crafted. Range `[1, 64]`. Default `1`. |

#### Complete Example: Lerped Animation with Custom Sounds

```json
{
    "id": "my_train_door",
    "name": {
        "en_us": "My Train Door",
        "zh_cn": "我的列车门"
    },
    "animation": {
        "type": "lerped",
        "speed": 0.01
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
        "sound_type": "metal",
        "open_sound_file": "door_open.ogg",
        "close_sound_file": "door_close.ogg"
    },
    "recipe": {
        "pattern": [
            "II",
            "II",
            "II"
        ],
        "keys": {
            "I": "minecraft:iron_ingot"
        },
        "count": 1
    }
}
```

#### Example: Phased Animation (CRH2A-style) with Existing SoundEvents

```json
{
    "id": "phased_door",
    "name": {
        "en_us": "Phased Sliding Door"
    },
    "animation": {
        "type": "phased",
        "total_ticks": 130,
        "opening": [
            { "type": "pause", "duration": 72 },
            { "type": "animate", "duration": 58 }
        ],
        "closing": [
            { "type": "animate", "duration": 90 },
            { "type": "pause", "duration": 6 },
            { "type": "animate", "duration": 34 }
        ]
    },
    "render": {
        "slide_scale": 0.9
    },
    "block": {
        "hardness": 5.0,
        "resistance": 6.0,
        "map_color": "color_light_blue",
        "sound_type": "copper",
        "sound_event": {
            "open": "minecraft:block.iron_door.open",
            "close": "minecraft:block.iron_door.close"
        }
    },
    "recipe": {
        "pattern": [
            " C ",
            "IDI",
            " C "
        ],
        "keys": {
            "C": "minecraft:copper_ingot",
            "I": "minecraft:iron_ingot",
            "D": "minecraft:iron_door"
        },
        "count": 2
    }
}
```

#### Example: Minimal door.json (all defaults)

```json
{
    "id": "simple_door"
}
```

This creates a door with: lerped animation (speed 1/120), CR400BF-style rendering (slide 13/16 + depth push), netherite block sounds, metal map color, and no crafting recipe.

#### ZIP File Layout

```
my_train_door.zip
├── door.json          (required)
├── side.png           (required - 16×N side texture)
├── top.png            (required - 16×16 top face texture)
├── bottom.png         (required - 16×16 bottom face texture)
├── door_open.ogg      (optional - custom open sound)
└── door_close.ogg     (optional - custom close sound)
```

> **Constraints**: `door.json` must be ≤ 256 KiB. Textures must be PNG format. Sound files must be OGG Vorbis format (mono recommended for correct 3D positional attenuation).

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

### door.json 格式参考

每个自定义门 ZIP 必须包含一个 `door.json` 文件。以下是完整格式说明及详细示例。

#### 顶层字段

| 字段 | 类型 | 必需 | 说明 |
|---|---|---|---|
| `id` | string | **是** | 唯一门标识符。必须匹配 `[a-z0-9_]{1,48}`。保留 ID：`cr400bf`、`crh2a`。 |
| `name` | object | 否 | 本地化显示名称，以语言代码为键（如 `en_us`、`zh_cn`）。 |
| `animation` | object | 否 | 开关门动画配置。默认使用线性插值动画。 |
| `render` | object | 否 | 渲染参数（滑动距离、深度推挤效果）。默认采用 CR400BF 风格。 |
| `block` | object | 否 | 方块属性（硬度、音效、地图色）。默认金属/下界合金方块。 |
| `recipe` | object | 否 | 可选的有序合成配方。省略则不添加合成配方。 |

#### `animation` 字段

| 字段 | 类型 | 必需 | 说明 |
|---|---|---|---|
| `type` | string | 否 | `"lerped"`（默认，线性插值）或 `"phased"`（分阶段）。 |
| `speed` | number | 否 | 线性插值速度，范围 `[0.000001, 1.0]`。默认 `1/120 ≈ 0.0083`。仅 `lerped` 类型使用。 |
| `total_ticks` | integer | 否 | 动画总 tick 数，默认 `130`。仅 `phased` 类型使用。 |
| `opening` | array | 否 | 开门阶段列表。仅 `phased` 类型使用。 |
| `closing` | array | 否 | 关门阶段列表。仅 `phased` 类型使用。 |

每个阶段对象格式：`{ "type": "pause" | "animate", "duration": <正整数 tick 数> }`

- `pause` — 在给定时间内保持当前进度不变。
- `animate` — 在给定时间内线性过渡到目标值（完全打开或完全关闭）。

#### `render` 字段

| 字段 | 类型 | 必需 | 说明 |
|---|---|---|---|
| `slide_scale` | number | 否 | 滑动距离占方块宽度的比例，范围 `[0.0, 4.0]`。默认 `0.8125`（13/16）。 |
| `depth_push` | object | 否 | 薄门板的深度推挤效果配置。 |
| `depth_push.enabled` | boolean | 否 | 是否启用深度推挤。默认 `false`。 |
| `depth_push.clamp_multiplier` | number | 否 | 钳制倍率，范围 `[0.0, 100.0]`。默认 `12.0`。 |
| `depth_push.scale` | number | 否 | 深度推挤缩放，范围 `[-4.0, 4.0]`。默认 `0.1`。 |

#### `block` 字段

| 字段 | 类型 | 必需 | 说明 |
|---|---|---|---|
| `hardness` | number | 否 | 方块硬度，范围 `[0.0, 10000.0]`。默认 `5.0`。 |
| `resistance` | number | 否 | 爆炸抗性，范围 `[0.0, 10000.0]`。默认 `6.0`。 |
| `map_color` | string | 否 | 地图颜色名称（50+ 有效值，详见下方示例）。默认 `"metal"`。 |
| `sound_type` | string | 否 | 方块音效类型（70+ 有效值，详见下方示例）。默认 `"netherite_block"`。 |
| `open_sound_file` | string | 否 | ZIP 内自定义开门音效 OGG 文件名。 |
| `close_sound_file` | string | 否 | ZIP 内自定义关门音效 OGG 文件名。 |
| `sound_event` | object | 否 | 引用已注册的 SoundEvent 替代自定义 OGG 文件。 |
| `sound_event.open` | string | 否 | 已有开门 SoundEvent 的资源路径（如 `"minecraft:block.iron_door.open"`）。 |
| `sound_event.close` | string | 否 | 已有关门 SoundEvent 的资源路径。 |

> **音效优先级**：如果提供了 `sound_event`，它将优先于 `open_sound_file` / `close_sound_file`。同一动作只能使用其中一种方式。

有效的 `map_color` 值：`none`、`grass`、`sand`、`wool`、`fire`、`ice`、`metal`、`plant`、`snow`、`clay`、`dirt`、`stone`、`water`、`wood`、`quartz`、`color_orange`、`color_magenta`、`color_light_blue`、`color_yellow`、`color_light_green`、`color_pink`、`color_gray`、`color_light_gray`、`color_cyan`、`color_purple`、`color_blue`、`color_brown`、`color_green`、`color_red`、`color_black`、`gold`、`diamond`、`lapis`、`emerald`、`podzol`、`nether`、`terracotta_white`、`terracotta_orange`、`terracotta_magenta`、`terracotta_light_blue`、`terracotta_yellow`、`terracotta_light_green`、`terracotta_pink`、`terracotta_gray`、`terracotta_light_gray`、`terracotta_cyan`、`terracotta_purple`、`terracotta_blue`、`terracotta_brown`、`terracotta_green`、`terracotta_red`、`terracotta_black`、`crimson_nylium`、`crimson_stem`、`crimson_hyphae`、`warped_nylium`、`warped_stem`、`warped_hyphae`。

有效的 `sound_type` 值：`wood`、`gravel`、`grass`、`lily_pad`、`stone`、`metal`、`glass`、`wool`、`sand`、`snow`、`powder_snow`、`ladder`、`anvil`、`slime_block`、`honey_block`、`wet_grass`、`coral_block`、`bamboo`、`bamboo_sapling`、`scaffolding`、`sweet_berry_bush`、`crop`、`hard_crop`、`vine`、`nether_wood`、`cherry_wood`、`bamboo_wood`、`netherite_block`、`ancient_debris`、`bone_block`、`netherrack`、`nylium`、`basalt`、`soul_soil`、`polished_deepslate`、`deepslate`、`deepslate_bricks`、`dripstone_block`、`moss`、`spore_blossom`、`tuff`、`tuff_bricks`、`calcite`、`amethyst`、`amethyst_cluster`、`large_amethyst_bud`、`medium_amethyst_bud`、`small_amethyst_bud`、`pointed_dripstone`、`copper`、`copper_bulb`、`nether_gold_ore`、`nether_ore`、`froglight`、`frogspawn`、`mud`、`muddy_mangrove_roots`、`mud_bricks`、`packed_mud`、`hanging_roots`、`roots`、`moss_carpet`、`cave_vines`、`nether_sprouts`、`azalea`、`azalea_leaves`、`big_dripleaf`、`decorated_pot`、`decorated_pot_cracked`、`trial_spawner`、`vault`、`heavy_core`、`cobweb`、`wet_sponge`。

#### `recipe` 字段

| 字段 | 类型 | 必需 | 说明 |
|---|---|---|---|
| `pattern` | string 数组 | **是** | 1–3 行合成配方，每行 1–3 个字符宽。所有行宽度必须一致。空格 ` ` = 空槽位。 |
| `keys` | object | **是** | 将每个符号字符映射到合成材料（物品标签或物品 ID）。如 `{"I": "minecraft:iron_ingot"}`。 |
| `count` | integer | 否 | 合成产出数量。范围 `[1, 64]`。默认 `1`。 |

#### 完整示例：线性插值动画 + 自定义音效

```json
{
    "id": "my_train_door",
    "name": {
        "en_us": "My Train Door",
        "zh_cn": "我的列车门"
    },
    "animation": {
        "type": "lerped",
        "speed": 0.01
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
        "sound_type": "metal",
        "open_sound_file": "door_open.ogg",
        "close_sound_file": "door_close.ogg"
    },
    "recipe": {
        "pattern": [
            "II",
            "II",
            "II"
        ],
        "keys": {
            "I": "minecraft:iron_ingot"
        },
        "count": 1
    }
}
```

#### 示例：分阶段动画（CRH2A 风格）+ 已有 SoundEvent

```json
{
    "id": "phased_door",
    "name": {
        "en_us": "Phased Sliding Door"
    },
    "animation": {
        "type": "phased",
        "total_ticks": 130,
        "opening": [
            { "type": "pause", "duration": 72 },
            { "type": "animate", "duration": 58 }
        ],
        "closing": [
            { "type": "animate", "duration": 90 },
            { "type": "pause", "duration": 6 },
            { "type": "animate", "duration": 34 }
        ]
    },
    "render": {
        "slide_scale": 0.9
    },
    "block": {
        "hardness": 5.0,
        "resistance": 6.0,
        "map_color": "color_light_blue",
        "sound_type": "copper",
        "sound_event": {
            "open": "minecraft:block.iron_door.open",
            "close": "minecraft:block.iron_door.close"
        }
    },
    "recipe": {
        "pattern": [
            " C ",
            "IDI",
            " C "
        ],
        "keys": {
            "C": "minecraft:copper_ingot",
            "I": "minecraft:iron_ingot",
            "D": "minecraft:iron_door"
        },
        "count": 2
    }
}
```

#### 示例：最简 door.json（全部使用默认值）

```json
{
    "id": "simple_door"
}
```

这将创建一个使用全部默认值的门：线性插值动画（速度 1/120）、CR400BF 风格渲染（滑动 13/16 + 深度推挤）、下界合金方块音效、金属地图色，无合成配方。

#### ZIP 文件结构

```
my_train_door.zip
├── door.json          (必需)
├── side.png           (必需 - 16×N 侧面纹理)
├── top.png            (必需 - 16×16 顶面纹理)
├── bottom.png         (必需 - 16×16 底面纹理)
├── door_open.ogg      (可选 - 自定义开门音效)
└── door_close.ogg     (可选 - 自定义关门音效)
```

> **约束**：`door.json` 不得超过 256 KiB。纹理必须为 PNG 格式。音效文件必须为 OGG Vorbis 格式（建议使用单声道以获得正确的 3D 距离衰减效果）。

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
