# RAAS / AAS — ESPoints 权威约定

ESPoints 拥有占点与战术地图权威，模式仅 **AAS** / **RAAS**。

## 目录

```text
EsWorld/<map>/
  EsConfig/game.json          # game.objectiveMode = AAS|RAAS
  EsConfig/TacticalMap.json
  EsConfig/spawn_points.json  # 主基地出生（不在争夺点集合内）
  Points/*.json               # 据点预设池（权威）
```

每个 Points 文件含 `"modes": ["AAS","RAAS"]`。装载时按 `game.objectiveMode` 过滤，再用本局种子随机抽一份。无匹配时回退 `EsConfig/CapturePoints.json`。

## RAAS 规则（对向推线）

1. 本局全部争夺点 **中立开局**，按 lane/stages 生成；`batch` = 阶段号；缩减结果带 `raasFrontline: true`（不再使用旧的「对称全 batch1 / 全占即胜」）。
2. 阵营A（ATTACK）前线从阶段 1 向最大阶段推进；阵营B（DEFEND）从最大阶段向 1 推进。
3. 每方可见 / 可交互 = **已占领点 ∪ 己方前线阶段点**；其它点 UI 隐藏且区域内无交互。
4. 双方前线阶段重合 → **雾散**：全图据点对双方可见（本局保持）。
5. 占领成功 → 占领方 `+captureReinforcement`。
6. 一方占齐 **全部争夺点** → 对方每秒 `-ticketBleedPerSecond`（默认 1）；兵力归零走既有胜负。
7. 失守可导致前线回退到最早未占齐阶段。

阶段内多点（如 `["b","c"]`）全部进入该阶段前线，须 **全部占齐** 才能推进。

## AAS

仍为批次推进 + 守方初始占点；从 Points 池抽 `modes` 含 AAS 的预设。无前线雾。

## 耦合

Espetro 交 EsConfig 路径 + seed；ESPoints 自读 Points / TacticalMap，并回写 `setResolvedObjectiveMode` / 兵力 API。
