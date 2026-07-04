# HCR AAD (`espoints`)

HCR AAD 是 Minecraft Forge 1.20.1 的行动攻防、据点和战术地图模组。项目目录名为 `ds`，实际模组 ID 为 `espoints`，发布名称为 `HCR AAD`。

## 核心功能

- 分批次行动攻防与进攻/防守兵力。
- 据点占领、奖励、死亡扣兵力和胜负结算。
- 战术地图、地图背景、玩家位置与缩放控制。
- 指挥官/小队长战术标点，同阵营同步。
- 据点详情、兵力、区域和消息 HUD。
- JSON 行动配置、预设保存及数据包热重载。
- 与 Espetro 的 `ATTACK`/`DEFEND` 阵营、指挥官、小队和兵站状态集成。

## 环境与依赖

| 项目 | 版本 |
| --- | --- |
| Java | 17 |
| Minecraft | 1.20.1 |
| Forge | 47.4.0 |
| MUtil | 6.3.0 |
| Espetro | 1.0.2-final 或更高 |

客户端和服务器均需安装 HCR AAD、Espetro 和 MUtil。

## 默认按键

| 按键 | 功能 |
| --- | --- |
| `J` | 请求并打开据点总览 |
| `V` | 显示/隐藏战术地图 |
| `X` | 打开战术地图客户端设置 |
| `C` | 地图可见时增大显示范围 |
| `B` | 地图可见时缩小显示范围 |
| 未绑定 | 打开 Markdown 阅读器 |
| `R` | 据点详情页请求刷新 |

## 构建

```bash
cd /home/shushu/IdeaProjects/ds
./gradlew build
```

产物位于 `build/libs/espoints-<version>.jar`。

## 文档

- [完整配置与 JSON 文档](docs/CONFIGURATION.md)
- [开发、API 与类参考](docs/DEVELOPMENT.md)

## 快速部署

1. 将 HCR AAD、Espetro 和 MUtil 放入客户端及服务器 `mods/`。
2. 首次启动生成 `espoints-common.toml`、`espoints-client.toml` 和服务器 JSON。
3. 编辑 `config/espoints/teamfight.json`。
4. 在数据包中覆盖 `data/espoints/tactical_map/default.json`，执行 `/reload`。
5. 执行 `/hcrpi teamfight loadconfig`，然后 `/hcrpi teamfight start`。
