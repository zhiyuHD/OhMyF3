# OhMyF3

[English](README.md) | **简体中文**

[![Minecraft](https://img.shields.io/badge/Minecraft-26.3-62B47A?logo=minecraft&logoColor=white)](#)
[![Fabric Loader](https://img.shields.io/badge/Fabric%20Loader-0.20.1%2B-dbb67b)](#)
[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)](#)
[![Release](https://img.shields.io/github/v/release/zhiyuHD/OhMyF3?color=blue)](https://github.com/zhiyuHD/OhMyF3/releases)
[![License](https://img.shields.io/github/license/zhiyuHD/OhMyF3?color=green)](./LICENSE)

**把 F3 界面重做一遍。** OhMyF3 把原版那堵等宽文字墙换成系统字体、关键词图标、圆角卡片和模糊背景，
让你一眼就能读出信息。

面向 Minecraft **26.3**（Fabric）。纯客户端 mod，不改变服务端任何行为。

---

## 它做了什么

| | |
|---|---|
| 🖋 **真字体** | 文字用系统 TrueType 字体渲染（这台机器上是 MiSans，配自写的字形光栅化器），而不是原版 5×7 点阵字。支持亚像素定位，字距不会再挤在一起。 |
| 🧩 **图标优先** | 每个字段旁边都有图标——朝向是罗盘、坐标是定位针、处理器是 CPU，一共四十多个。 |
| 🎴 **卡片分组** | 相关行被归进带细边框的圆角卡片，而不是一条平铺的长列表。 |
| 🫧 **背景模糊** | 卡片背后的世界用游戏自带的模糊链处理，而且是在**画面副本**上做，所以只有卡片区域被模糊，不是整屏。 |
| 🎬 **动画** | 两列会滑入滑出。用的是单个可重定向的数值，动画中途再按 F3 会从当前位置反向，而不是跳回端点。 |
| 🀄 **中文标签** | 字段名可切换为中文（`坐标`、`区块`、`光照`……），在设置界面里开关。 |
| 🔌 **其他 mod 的行** | 别的 mod 加进 F3 的行会用同样样式绘制，并按**所属条目的命名空间**识别，而不是硬编码名单。 |

## 安装

1. 为 Minecraft 26.3 安装 **Fabric Loader 0.20.1+**。
2. 把 [**Fabric API**](https://modrinth.com/mod/fabric-api) 放进 `mods/`。
3. 把 [最新 release](https://github.com/zhiyuHD/OhMyF3/releases) 里的 `OhMyF3-0.1.0.jar` 放进同一个 `mods/`。

装完直接按 **F3** 就行。

## 操作

| 按键 | 作用 |
|---|---|
| **F3** | 开关调试图层 |
| **F9** | 打开 OhMyF3 设置界面 |
| **OhMyF3 按钮** | 标题界面和暂停菜单里也有（有界面打开时按键绑定不会触发，所以这是更可靠的入口） |

设置也可以直接改 `config/ohmyf3.json`：

```json
{
  "chineseLabels": true,
  "icons": true,
  "blur": true,
  "slideAnimation": true
}
```

## 从源码构建

需要 **JDK 25**（Minecraft 26.3 的目标是 Java 25，不是 21）：

```bash
export JAVA_HOME=/path/to/jdk-25
./gradlew build          # 产物在 build/libs/
./gradlew runClient      # 开发客户端
```

## 实现细节

字体光栅化、九宫格卡片、模糊如何限制在卡片内、两列之间如何共享动画状态——
这些都写在 **[docs/internals.md](docs/internals.md)**。

## 许可证

MIT，见 [LICENSE](./LICENSE)。
