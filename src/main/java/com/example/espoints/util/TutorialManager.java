package com.example.espoints.util;

import com.example.espoints.ESPointsMod;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Creates the two short, player-facing documents shown by the MUtil reader. */
public final class TutorialManager {
    private static final String MD_FOLDER_NAME = "HCRmdread";

    private TutorialManager() {
    }

    public static void generateTutorialFiles() {
        File mdFolder = new File(Minecraft.getInstance().gameDirectory, MD_FOLDER_NAME);
        if (!mdFolder.exists() && !mdFolder.mkdirs()) {
            ESPointsMod.LOGGER.error("无法创建教程文件夹");
            return;
        }
        writeIfMissing(new File(mdFolder, "MD阅读器使用教程.md"), readerTutorial());
        writeIfMissing(new File(mdFolder, "HCR AAD模组使用教程.md"), gameTutorial());
    }

    private static String readerTutorial() {
        return """
            # 游戏内文档

            ## 打开

            在按键设置的 espetro 分类中，为“打开文档”设置一个顺手的按键。

            ## 阅读

            - 左侧选择文档或章节。
            - 右侧滚动阅读内容。
            - 点击“返回”回到文档列表。

            这里主要放置游戏教程和服务器说明。普通游玩不需要了解文件格式。
            """;
    }

    private static String gameTutorial() {
        return """
            # HCR AAD 新手教程

            ## 这是什么

            HCR AAD 为 Espetro 对局提供战术地图、据点和双方兵力显示。加入游戏后会自动加载，
            普通玩家不需要输入任何命令。

            ## 一局游戏

            先在 Espetro 界面选择阵营、班组和职业，再选择部署点进入战场。进攻方按批次夺取据点，
            防守方阻止占领。留意屏幕上的双方兵力，兵力耗尽的一方失败。

            ## 战术地图

            - V：显示或隐藏地图。
            - 鼠标滚轮：放大或缩小地图。
            - X：调整地图位置、大小和颜色。
            - 按住 Ping Wheel 的标点键：在战场内打开战术标点轮盘。
            - 据点总览默认未绑定，请在按键设置中选择一个不与 Espetro 冲突的按键。

            地图会显示当前据点、队友和本方战术设施。底图与边界由当前战场决定。
            阵营、编制、部署和死亡重部署界面打开时，地图设置与据点界面不会覆盖当前流程。

            ## 战术标点

            指挥官、小队长、火力组组长和合法载具席位可以标记敌情或下达方向。轮盘图标与
            战术地图一致；标点只让本方看到，并会在本局结束后清空。

            ## 指挥官技能

            需要选择目标的技能会打开地图窗口。移动、缩放并选择目标即可；系统会自动检查目标是否有效。

            ## 遇到问题

            地图不可用时先确认已经进入当前战场。仍无法显示，请联系服务器管理员检查地图配置和模组版本。
            """;
    }

    private static void writeIfMissing(File file, String content) {
        if (file.exists()) {
            return;
        }
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            writer.write(content);
            ESPointsMod.LOGGER.info("已生成教程：{}", file.getName());
        } catch (IOException e) {
            ESPointsMod.LOGGER.error("生成教程失败：{}", e.getMessage());
        }
    }
}
