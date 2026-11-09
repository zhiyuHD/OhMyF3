package com.ohmyf3.font;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Chinese labels for the debug screen.
 *
 * <p>Only the text this mod owns is translated: the field names recovered by
 * {@link EntryClassifier} (which come from vanilla's English debug output) and a handful of
 * fixed phrases in the hint rows. Values are data and stay as they are, apart from a few
 * phrases that only make sense in English.
 */
public final class Labels {
	private static final Map<String, String> KEYS = new LinkedHashMap<>();
	private static final Map<String, String> PHRASES = new LinkedHashMap<>();

	static {
		KEYS.put("xyz", "坐标");
		KEYS.put("block", "方块");
		KEYS.put("chunk", "区块");
		KEYS.put("section-relative", "区块内偏移");
		KEYS.put("client chunk", "客户端区块");
		KEYS.put("server chunk", "服务端区块");
		KEYS.put("facing", "朝向");
		KEYS.put("biome", "生物群系");
		KEYS.put("light", "光照");
		KEYS.put("day", "天数");
		KEYS.put("time", "时间");
		KEYS.put("seed", "种子");
		KEYS.put("difficulty", "难度");
		KEYS.put("local difficulty", "当地难度");
		KEYS.put("dimension", "维度");
		KEYS.put("dim", "维度");
		KEYS.put("entities", "实体");
		KEYS.put("entity", "实体");
		KEYS.put("size", "尺寸");
		KEYS.put("e", "实体");
		KEYS.put("fps", "帧率");
		KEYS.put("t", "线程");
		KEYS.put("mem", "内存");
		KEYS.put("pools", "内存池");
		KEYS.put("allocation rate", "分配速率");
		KEYS.put("allocated", "已分配");
		KEYS.put("off-heap", "堆外");
		KEYS.put("chunk builder", "区块构建");
		KEYS.put("tasks", "任务");
		KEYS.put("duration", "耗时");
		KEYS.put("ts", "线程状态");
		KEYS.put("n", "线程数");
		KEYS.put("qd", "队列");
		KEYS.put("java", "Java");
		KEYS.put("cpu", "处理器");
		KEYS.put("gpu", "显卡");
		KEYS.put("opengl", "图形接口");
		KEYS.put("display", "显示器");
		KEYS.put("window", "窗口");
		KEYS.put("filtering", "过滤");
		KEYS.put("fancy-clouds b", "云层");
		KEYS.put("debug charts", "调试图表");
		KEYS.put("sodium renderer", "Sodium 渲染器");
		KEYS.put("terrain rendering", "地形渲染");
		KEYS.put("integrated server", "集成服务器");
		KEYS.put("minecraft", "Minecraft");

		PHRASES.put("Profiler hidden", "性能分析已隐藏");
		PHRASES.put("fps + tps hidden", "帧率 + TPS 已隐藏");
		PHRASES.put("Bandwidth + Ping hidden", "带宽 + 延迟已隐藏");
		PHRASES.put("Lightmap hidden", "光照贴图已隐藏");
		PHRASES.put("To edit: press", "编辑请按");
		PHRASES.put("Debug charts:", "调试图表：");
		PHRASES.put("hidden", "已隐藏");
	}

	private Labels() {
	}

	/** Translates a field name, or returns it unchanged when nothing is known about it. */
	public static String key(String english) {
		if (english == null || english.isEmpty()) {
			return english;
		}

		String translated = KEYS.get(english.toLowerCase(Locale.ROOT));
		return translated != null ? translated : english;
	}

	/** Rewrites the few English phrases that would otherwise leak into the Chinese UI. */
	public static String value(String english) {
		if (english == null || english.isEmpty()) {
			return english;
		}

		String result = english;

		for (Map.Entry<String, String> phrase : PHRASES.entrySet()) {
			if (result.contains(phrase.getKey())) {
				result = result.replace(phrase.getKey(), phrase.getValue());
			}
		}

		return result;
	}

	/** Keys that have a translation, for tests and diagnostics. */
	public static int knownKeyCount() {
		return KEYS.size();
	}
}
