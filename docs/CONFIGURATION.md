# HCR AAD 配置文档

## 配置文件总览

| 文件 | 端 | 来源 | 重载 |
| --- | --- | --- | --- |
| `config/espoints-common.toml` | 服务端/通用 | Forge COMMON | 重启或配置界面同步 |
| `config/espoints-client.toml` | 客户端 | Forge CLIENT | 客户端重启/配置界面 |
| `config/hcr_map_player_display.json` | 服务端 | 自动创建 | `/hcrpi reload` |
| `config/espoints/teamfight.json` | 服务端 | 自动创建 | `/hcrpi teamfight loadconfig` 或 `/hcrpi reload` |
| `config/espoints/presets/preset_<id>.json` | 服务端 | 命令生成 | `/hcrpi teamfight load <id>` |
| `data/espoints/tactical_map/default.json` | 服务端数据包 | 内置，可被数据包覆盖 | `/reload` |

只编辑 `src/main/resources` 或服务器配置/数据包文件，不要编辑 `build/resources` 下的构建副本。

## 构建与模组元数据配置

| `gradle.properties` 字段 | 当前值 | 说明 |
| --- | --- | --- |
| `minecraft_version` | `1.20.1` | 目标游戏版本 |
| `forge_version` | `47.4.0` | Forge 开发版本 |
| `mod_id` | `espoints` | 资源命名空间和模组 ID |
| `mod_version` | `1.0.3-final` | 构建版本 |
| `espetro_version` | `1.0.2-final` | Espetro 最低版本 |
| `mod_group_id` | `com.example.espoints` | Maven group |

`META-INF/mods.toml` 声明 Forge 47、Minecraft 1.20.1、MUtil 6.3.0 和 Espetro 最低版本。变更版本时应同步更新 Gradle 属性、依赖声明和元数据范围。

其他 JSON 资源：

- `assets/espoints/lang/zh_cn.json`、`en_us.json` 是翻译键值表；两种语言应保持相同键集合。
- `pack.mcmeta` 是模组内置资源包元数据，Minecraft 1.20.1 使用 `pack_format: 15`。
- `assets/espoints/md_templates` 是运行时生成教程的 Markdown 模板，不是服务器规则配置。

## `espoints-common.toml`

### HUD Settings

| 字段 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `enableHUD` | Boolean | `true` | 总体 HUD 开关 |
| `enableCarousel` | Boolean | `true` | 据点信息轮播开关 |

### Team Settings

| 字段 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `enableTeams` | Boolean | `true` | 兼容旧配置；当前逻辑固定使用 Espetro 阵营 |
| `enableTeamIndicator` | Boolean | `true` | 玩家头顶友军/敌军标识 |

### Performance Settings

| 字段 | 类型 | 默认 | 范围 | 说明 |
| --- | --- | ---: | ---: | --- |
| `checkInterval` | Integer | 5 | 1..100 tick | 据点检测间隔；越小响应越快、CPU 开销越高 |

### Reward Settings

| 字段 | 默认 | 范围 | 单位/说明 |
| --- | ---: | ---: | --- |
| `pointRewardInterval` | 60 | 1..3600 | 据点内周期奖励间隔，秒 |
| `pointRewardAmount` | 5 | 1..1000 | 每次据点内奖励 |
| `killRewardAmount` | 50 | 1..1000 | 击杀奖励 |
| `captureRewardAmount` | 100 | 1..1000 | 占领奖励 |
| `capturedRewardInterval` | 60 | 1..3600 | 已占据点持续奖励间隔，秒 |
| `capturedRewardAmount` | 10 | 1..1000 | 持续奖励数值 |
| `capturedRewardDelay` | 5 | 1..3600 | 首次持续奖励延迟，秒 |
| `enableFriendlyFirePenalty` | `true` | Boolean | 是否启用友军击杀惩罚 |
| `friendlyFirePenalty` | 200 | 1..10000 | 友军击杀扣分 |

### Operation Settings

| 字段 | 类型 | 默认 | 范围 | 说明 |
| --- | --- | --- | --- | --- |
| `enableOperationMode` | Boolean | `true` | 兼容旧配置；当前行动模式固定启用 |
| `lowReinforcementThreshold` | Double | `10.0` | 0..100 | 低兵力 BGM 阈值百分比；0 为禁用 |

## `espoints-client.toml`

```toml
[tacticalMap]
displayMode = "TOGGLE_KEY"
miniMapScale = 75
attackerProgressBarColor = "#FF5500"
defenderProgressBarColor = "#0055FF"
```

| 字段 | 默认 | 可选值/格式 |
| --- | --- | --- |
| `displayMode` | `TOGGLE_KEY` | `TOGGLE_KEY`, `ALWAYS_VISIBLE_BOTTOM_LEFT`, `ALWAYS_VISIBLE_TOP_LEFT`, `ALWAYS_VISIBLE_BOTTOM_RIGHT`, `ALWAYS_VISIBLE_TOP_RIGHT` |
| `miniMapScale` | `75` | 25..100，百分比 |
| `attackerProgressBarColor` | `#FF5500` | `#RRGGBB` |
| `defenderProgressBarColor` | `#0055FF` | `#RRGGBB` |

## `hcr_map_player_display.json`

```json
{
  "showPlayerLocations": true
}
```

`showPlayerLocations` 控制战术地图是否同步并显示玩家位置。也可用 `/hcrpi mapctrl true|false` 修改；修改会立即保存。

## `teamfight.json`

推荐格式：

```json
{
  "totalBatches": 2,
  "endBehavior": "terminate",
  "teamReinforcements": {
    "ATTACK": 280,
    "DEFEND": 1200
  },
  "plannedPoints": [
    {
      "name": "A",
      "batch": 1,
      "pos1": { "x": 100, "y": 60, "z": 100 },
      "pos2": { "x": 120, "y": 80, "z": 120 }
    },
    {
      "name": "B",
      "batch": 2,
      "pos1": [200, 60, 200],
      "pos2": [220, 80, 220]
    }
  ]
}
```

### 根字段

| 字段 | 必填 | 默认 | 规则 |
| --- | --- | --- | --- |
| `totalBatches` | 否 | 据点最大 `batch`，无据点时为 1 | >= 1，且不能小于最大批次 |
| `endBehavior` | 否 | `terminate` | `terminate` 或 `loop` |
| `teamReinforcements` | 否 | 两队各 50 | 对象；值必须 > 0 |
| `plannedPoints` | 否 | 空数组 | 数组或以据点名为键的对象 |

兼容别名：`plannedPoints` 也可写成 `points` 或 `capturePoints`；兵力也可使用 `attackReinforcements`、`attackerReinforcements`、`defendReinforcements`、`defenderReinforcements`。

### 据点字段

| 字段 | 必填 | 规则 |
| --- | --- | --- |
| `name` | 数组形式必填 | 单个大写字母 `A`..`Z`，不可重复 |
| `batch` | 是 | >= 1；每批最多 7 个据点 |
| `pos1` | 是 | `{x,y,z}` 或 `[x,y,z]` |
| `pos2` | 是 | 与 `pos1` 构成非零长方体 |

对象形式示例：

```json
{
  "plannedPoints": {
    "A": {
      "batch": 1,
      "from": [100, 60, 100],
      "to": [120, 80, 120]
    }
  }
}
```

`from`/`to` 分别是 `pos1`/`pos2` 的兼容别名。

## 战术地图数据包 JSON

内置位置：`src/main/resources/data/espoints/tactical_map/default.json`。

服务器覆盖目录：

```text
world/datapacks/my_hcr_config/
├── pack.mcmeta
└── data/espoints/tactical_map/default.json
```

```json
{
  "topLeftX": -512,
  "topLeftZ": -512,
  "bottomRightX": 512,
  "bottomRightZ": 512,
  "initialRange": 512,
  "minimumRange": 64,
  "backgroundImage": "",
  "backgroundImageWidth": 0,
  "backgroundImageHeight": 0,
  "showGrid": true,
  "showLabels": true,
  "tacticalMarkerDurationSeconds": 120,
  "tacticalMarkerFadeSeconds": 120
}
```

| 字段 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `topLeftX`, `topLeftZ` | Integer | -512 | 地图边界第一角 |
| `bottomRightX`, `bottomRightZ` | Integer | 512 | 地图边界第二角；代码会自动取 min/max |
| `initialRange` | Integer | 512 | 首次打开时可见范围，<=0 时使用完整地图尺寸 |
| `minimumRange` | Integer | 64 | 最小缩放范围，至少 1 |
| `backgroundImage` | String | 空 | 背景图片资源 ID或客户端文件路径；空表示无背景 |
| `backgroundImageWidth`, `backgroundImageHeight` | Integer | 0 | 同步的图片尺寸元数据；当前渲染器以实际解码尺寸为准，负数会归零 |
| `showGrid` | Boolean | `true` | 网格开关 |
| `showLabels` | Boolean | `true` | 标签开关 |
| `tacticalMarkerDurationSeconds` | Integer | 120 | 标点总寿命，至少 1 秒 |
| `tacticalMarkerFadeSeconds` | Integer | 120 | 淡出时间，至少 1 且不超过总寿命 |

执行 `/reload` 后服务端应用配置并同步所有客户端。旧文件 `config/hcr_tactical_map.json` 已不再读取，应迁移到数据包路径。

`backgroundImage` 支持以下写法：

```text
espoints:textures/gui/map.png
assets/espoints/textures/gui/map.png
textures/gui/map.png
config/espoints/map.png
/absolute/client/path/map.png
```

前三种从客户端资源管理器读取；后两种从每个客户端的游戏目录或绝对路径读取。因此服务器配置文件路径时，所有客户端都必须在相同相对位置放置图片。发布整合包时优先使用资源 ID。

## 行动预设 JSON

`/hcrpi teamfight save <id>` 生成 `config/espoints/presets/preset_<id>.json`：

```json
{
  "plannedPoints": {
    "A": {
      "name": "A",
      "pos1": { "x": 0, "y": 60, "z": 0 },
      "pos2": { "x": 10, "y": 70, "z": 10 },
      "batch": 1
    }
  },
  "teamRoles": {
    "ATTACK": "attacker",
    "DEFEND": "defender"
  },
  "teamReinforcements": {
    "ATTACK": 280,
    "DEFEND": 1200
  }
}
```

预设格式是旧式完整状态快照，缺少严格校验。新部署优先使用 `teamfight.json`；预设用于管理员快速保存/恢复运行中的计划。

## 数据包 `pack.mcmeta`

```json
{
  "pack": {
    "pack_format": 15,
    "description": "HCR server configuration"
  }
}
```

## 配置命令

所有管理命令要求权限等级 2：

```text
/hcrpi reload
/hcrpi mapctrl <true|false>
/hcrpi teamfight loadconfig
/hcrpi teamfight saveconfig
/hcrpi teamfight start [totalBatches] [terminate|loop]
/hcrpi teamfight stop
/hcrpi teamfight nextbatch
/hcrpi teamfight save <id>
/hcrpi teamfight load <id>
```

## 校验与排错

- JSON 必须是 UTF-8 且不能包含尾随逗号或注释。
- 修改数据包地图后使用原版 `/reload`；修改 `teamfight.json` 后使用 `/hcrpi teamfight loadconfig`。
- 行动正在运行时，普通 `loadconfig` 会拒绝覆盖状态。
- 玩家没有显示在地图上时检查 `hcr_map_player_display.json` 和 Espetro 队伍状态。
- 背景图不显示时同时检查资源 ID、图片是否随资源包分发，以及宽高字段。
