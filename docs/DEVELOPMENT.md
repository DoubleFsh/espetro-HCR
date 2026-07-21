# HCR AAD 开发文档

## 模块边界

HCR AAD 负责据点、行动批次、兵力显示和战术地图。Espetro 是阵营、小队、指挥官、职业和部署状态的权威来源。

```text
Espetro ATTACK/DEFEND
  -> EspetroTeamBridge
  -> CapturePointManager / TacticalMarkerManager
  -> NetworkHandler
  -> 客户端 HUD 与战术地图
```

`EspetroTeamBridge` 通过记分板和反射兼容 Espetro API，避免 HCR AAD 公共类在缺少客户端类时崩溃。

## 生命周期

`HCRPointsMod` 完成以下注册：

1. 注册 COMMON 和 CLIENT Forge 配置。
2. 注册网络包。
3. 注册 HUD overlay。
4. 注册 `/hcrpi` 命令。
5. 服务器启动时加载 `teamfight.json`。
6. 注册战术地图数据包重载监听器。
7. 玩家登录或 `/reload` 后同步地图配置和运行状态。

## 对外 API

### `HCRAPI`

显示玩家消息：

```java
UUID playerId = player.getUUID();
HCRAPI.showMessage(playerId, "目标 A 已占领", 3000L);
HCRAPI.showWinMessage(playerId);
```

自定义样式：

```java
HCRAPI.showMessage(
    playerId,
    "增援抵达",
    4000L,
    180,
    48,
    0xCC000000,
    0xFFFFFFFF,
    0xFFFFAA00,
    2
);
```

战术地图 API 是客户端调用：

```java
HCRAPI.toggleTacticalMap();
boolean visible = HCRAPI.isTacticalMapVisible();
MapDisplayMode mode = HCRAPI.getTacticalMapDisplayMode();
```

获取服务端据点管理器：

```java
CapturePointManager manager = HCRAPI.getCapturePointManager();
CapturePoint point = manager.getCapturePoint("A");
```

不要在网络线程直接修改 `CapturePointManager`；切换到服务器主线程。

### `ESPointsCommanderScriptAPI`

这是给 Espetro KubeJS 指挥官脚本使用的服务端静态桥接 API。Espetro 通过反射访问它，因此 ESPoints 不需要反向编译依赖 Espetro 的脚本实现类。

```java
boolean inside = ESPointsCommanderScriptAPI.isWithinTacticalMap(x, z);
ESPointsCommanderScriptAPI.placeArtilleryTarget(serverPlayer, x, z);
double minX = ESPointsCommanderScriptAPI.getMapMinX();
String team = ESPointsCommanderScriptAPI.getPlayerTeam(serverPlayer);
```

常用方法：

| 方法 | 作用 |
| --- | --- |
| `isWithinTacticalMap(x, z)` | 使用 `TacticalMapJsonConfig.TacticalMapBounds` 校验坐标 |
| `getMapMinX/MinZ/MaxX/MaxZ()` | 返回当前战术地图边界 |
| `getMapWidth()` / `getMapHeight()` | 返回地图宽高 |
| `getPlayerTeam(player)` | 返回 Espetro 规范阵营 |
| `canPlaceTacticalMarker(player)` | 是否为 Espetro 指挥官或小队长 |
| `placeMarker(player, typeId, x, z)` | 通过 `TacticalMarkerManager` 放置标点并同步同阵营 |
| `placeArtilleryTarget(player, x, z)` | 放置 `ARTILLERY_TARGET` 标点 |

## 据点模型

### `CapturePoint`

```java
CapturePoint point = new CapturePoint(
    "A",
    new BlockPos(0, 60, 0),
    new BlockPos(20, 80, 20),
    1
);

if (point.isPositionInside(player.blockPosition())) {
    // 玩家位于据点内。
}
```

`CaptureState` 描述实际占领状态，`DisplayState` 描述 HUD 展示状态。跨网络传输使用 `CapturePoint.SerializableCapturePoint`，不要直接序列化世界对象。

### `CapturePointManager`

```java
CapturePointManager manager = CapturePointManager.getInstance();
manager.addPlannedCapturePoint("A", pos1, pos2, 1);
manager.setTeamRole("ATTACK", "attacker", 280);
manager.setTeamRole("DEFEND", "defender", 1200);
manager.startOperationMode(1, "terminate");
```

常用方法：

| 方法 | 作用 |
| --- | --- |
| `createCapturePoint` / `removeCapturePoint` | 管理当前活动据点 |
| `addPlannedCapturePoint` | 添加批次计划 |
| `startOperationMode` / `stopOperationMode` | 控制行动 |
| `nextBatch` | 手动推进批次 |
| `deductTeamReinforcements` | 扣除兵力 |
| `syncToAllClients` / `syncToClient` | 同步权威状态 |
| `getOverviewSerializablePoints` | 获取总览 DTO |

## 战术地图与标点

安装 Espetro 1.0.6-final 或更高版本时，服务端优先通过反射读取
`EspetroAPI.getFobs()` 和 `EspetroAPI.getRallies()`。客户端据此显示 Radio/HAB/Rally
图标、150 格建设半径、400 格排斥半径、FOB 建材/弹药库存、HAB 可用状态和 Rally
下一波秒数；旧版 Espetro 会回退到 `BastionManager#getAllBastions`。

### `TacticalMapJsonConfig`

```java
TacticalMapJsonConfig config = TacticalMapJsonConfig.getInstance();
TacticalMapJsonConfig.TacticalMapBounds bounds = config.getBounds();
if (bounds.contains(x, z)) {
    double initialRange = config.getInitialRange(bounds);
}
```

### `TacticalMarkerManager`

```java
TacticalMarkerManager.place(
    serverPlayer,
    TacticalMarkerType.ENEMY_TANK,
    serverPlayer.getX(),
    serverPlayer.getZ()
);
```

服务端会校验：调用者必须是 Espetro 指挥官或小队长、坐标必须在地图范围内、每队最多 64 个标点。删除只能由标点创建者执行。

`TacticalMarkerType` 当前包含敌军步兵、坦克、步战车、轻型载具、直升机、进攻/防守指令以及 `ARTILLERY_TARGET`。`ARTILLERY_TARGET` 是 Espetro `155火炮支援` 的结果标点，不会出现在普通右键标点菜单中。

### Espetro 155火炮支援选点

ESPoints 为 Espetro 的 `artillery_155` 指挥官技能提供战术地图选点能力：

1. Espetro 服务端在技能通过权限、阶段和冷却校验后，通过反射调用 `OpenArtillerySupportMapMessage.sendTo(ServerPlayer)`。
2. ESPoints 向该玩家发送 S2C 包，客户端打开 `ArtillerySupportMapScreen`。
3. `ArtillerySupportMapScreen` 使用与 J 键 `CapturePointDetailsScreen` 相同的主面板尺寸规则，并调用 `TacticalMapHUD.renderArtillerySelectionMap` 渲染嵌入式战术地图。
4. 玩家可使用鼠标滚轮缩放、左键拖拽地图；右键不会打开普通标点菜单，而是直接通过 `MapViewport.worldX/worldZ` 计算世界 X/Z。
5. 客户端发送 `SelectArtillerySupportTargetMessage` 到服务端。服务端先校验 `TacticalMapJsonConfig.TacticalMapBounds`，再通过 `EspetroTeamBridge.submitArtillerySupportTarget` 反射调用 `EspetroAPI.submitArtillerySupportTarget(ServerPlayer, double, double)`。
6. Espetro 负责二次权限校验、Y 坐标求值、KubeJS 指挥官技能回调和冷却。ESPoints 在提交成功后放置 `ARTILLERY_TARGET` 战术标点并同步给同阵营玩家。

ESPoints 不生成炮击实体，也不调度炮击波次。默认 `155火炮支援` 的实体 ID、目标高度、覆盖半径、批次数量、间隔和入射角都由 Espetro 的 KubeJS `server_scripts` 决定。

这条链路不让 ESPoints 编译期依赖 Espetro 的实现类，也不让 Espetro 编译期依赖 ESPoints 的客户端类；双方只依赖 Forge `mods.toml` 运行时依赖关系和反射 API。

## 配置类

| 类 | 职责 |
| --- | --- |
| `ModConfig` | COMMON Forge 配置定义 |
| `TacticalMapConfig` | CLIENT Forge 配置定义 |
| `MapPlayerDisplayConfig` | 服务端玩家位置 JSON 持久化 |
| `TeamfightJsonConfig` | 行动 JSON 解析、校验、导入和导出 |
| `TacticalMapJsonConfig` | 数据包地图配置模型与边界计算 |
| `TacticalMapDataReloadListener` | `/reload` 时读取 `tactical_map/default.json` |

## 网络开发

`NetworkHandler` 使用 Forge `SimpleChannel`。所有消息遵循 `encode/decode/handle` 模式。

```java
NetworkHandler.INSTANCE.send(
    PacketDistributor.PLAYER.with(() -> player),
    new SyncTacticalMarkersMessage(markers)
);
```

包分类：

| 类别 | 类 |
| --- | --- |
| 据点状态 | `SyncCapturePointsMessage`, `RequestCapturePointOverviewMessage`, `SyncCapturePointOverviewMessage` |
| 行动状态 | `SyncOperationModeMessage`, `SyncConfigMessage`, `SyncBastionsMessage` |
| 地图 | `SyncPlayerPositionsMessage`, `SyncMapPlayerDisplayMessage`, `SyncTacticalMapConfigMessage` |
| 战术标点 | `PlaceTacticalMarkerMessage`, `RemoveTacticalMarkerMessage`, `RequestTacticalMarkersMessage`, `SyncTacticalMarkersMessage` |
| Espetro 火炮选点 | `OpenArtillerySupportMapMessage`, `SelectArtillerySupportTargetMessage` |
| 客户端效果 | `ShowMessagePopupMessage`, `PlayLowReinforcementAudioMessage` |

新增网络包时必须：

1. 为包分配稳定 ID 并在 `NetworkHandler.registerMessages()` 注册。
2. 对客户端上行数据做权限和范围校验。
3. 世界状态修改使用主线程 consumer。
4. 对集合数量和字符串长度设置上限，避免恶意包导致内存分配。

## 客户端与 HUD

| 类 | 职责 |
| --- | --- |
| `ClientEventHandler` | 按键轮询、GUI 请求和地图缩放 |
| `ClientProxy` | 注册客户端按键 |
| `AudioManager` | `fightBGM` 外部音频文件与低兵力 BGM |
| `PlayerTeamIndicator` | 玩家头顶敌我标记 |
| `TacticalMapHUD` | 地图坐标变换、背景、玩家、据点、普通标点和155火炮选点模式渲染 |
| `CapturePointHUD` | 据点轮播 |
| `CurrentCapturePointHUD` | 当前所在据点 |
| `AreaInfoHUD` | 区域信息 |
| `ReinforcementsHUD` | 双方兵力与进度条 |
| `MessagePopup` | 服务端驱动消息弹窗 |

GUI 类：`CapturePointDetailsScreen`、`ArtillerySupportMapScreen`、`TacticalMapConfigScreen`、`ServerConfigScreen`、`MDRenderScreen`、`MutilScreen`、`HcrMutilWidgets`、`ScrollableList`、`ScreenFadeIn`。

## 类参考

### 核心与 API

| 类 | 说明 |
| --- | --- |
| `HCRPointsMod` | Forge 主入口和生命周期注册 |
| `HCRAPI` | 对外消息、地图和管理器访问 API |
| `ESPointsCommanderScriptAPI` | 给 Espetro KubeJS 指挥官脚本使用的服务端战术地图和标点桥接 API |
| `CapturePoint` | 单个据点状态模型 |
| `CapturePointManager` | 据点、批次、兵力、同步和事件中心 |
| `CaptureState` | 占领状态枚举 |
| `DisplayState` | HUD 状态枚举 |

### 配置与命令

| 类 | 说明 |
| --- | --- |
| `HCRCommand` | `/hcrpi` Brigadier 命令树 |
| `TeamfightJsonConfig` | 主行动 JSON |
| `TeamfightPresetManager` | 旧式预设导入导出 |
| `CapturePointPresetManager` | 已停用的普通据点预设兼容类 |
| `ModConfig` | 通用 TOML |
| `TacticalMapConfig` | 客户端 TOML |
| `MapPlayerDisplayConfig` | 玩家位置 JSON |
| `TacticalMapJsonConfig` | 地图数据包 JSON |
| `TacticalMapDataReloadListener` | 数据包加载器 |

### 战术系统

| 类 | 说明 |
| --- | --- |
| `TacticalMarker` | 不可变标点记录 |
| `TacticalMarkerType` | 标点类型、颜色和普通菜单可选性 |
| `TacticalMarkerManager` | 服务端标点权限、寿命和阵营同步 |
| `EspetroTeamBridge` | Espetro 阵营、权限和155火炮支援坐标提交适配 |
| `MapDisplayMode` | 地图显示模式枚举 |

### 网络消息

`NetworkHandler`、`SyncCapturePointsMessage`、`SyncConfigMessage`、`SyncMapPlayerDisplayMessage`、`SyncOperationModeMessage`、`SyncPlayerPositionsMessage`、`SyncTacticalMapConfigMessage`、`SyncTacticalMarkersMessage`、`SyncCapturePointOverviewMessage`、`SyncBastionsMessage`、`RequestCapturePointOverviewMessage`、`RequestTacticalMarkersMessage`、`PlaceTacticalMarkerMessage`、`RemoveTacticalMarkerMessage`、`OpenArtillerySupportMapMessage`、`SelectArtillerySupportTargetMessage`、`ShowMessagePopupMessage`、`PlayLowReinforcementAudioMessage`。

### 工具

| 类 | 说明 |
| --- | --- |
| `ModLogger` | 统一日志包装 |
| `TutorialManager` | 首次运行生成 Markdown 教程 |

## 命令开发示例

```java
dispatcher.register(
    Commands.literal("my_hcr_command")
        .requires(source -> source.hasPermission(2))
        .executes(context -> {
            CapturePointManager.getInstance().syncToAllClients();
            return 1;
        })
);
```

## 构建与测试

```bash
./gradlew compileJava
./gradlew test
./gradlew build
./gradlew runClient
./gradlew runServer
```

提交前检查：

- 无 Espetro 时依赖提示明确，有 Espetro 时双方阵营正确绑定。
- `teamfight.json` 的成功和失败样例均得到正确日志。
- `/reload` 后地图边界与所有客户端一致。
- 非队长/指挥官无法放置标点。
- 玩家断线、换队和行动重置后缓存被清理。
- 专用服务器不加载 `net.minecraft.client` 类。
