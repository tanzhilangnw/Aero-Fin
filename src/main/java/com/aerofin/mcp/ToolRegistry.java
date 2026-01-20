package com.aerofin.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 工具注册中心
 * <p>
 * 核心功能：
 * 1. 工具注册与发现
 * 2. 工具元数据管理
 * 3. 工具验证与版本控制
 * 4. 支持插件化动态加载
 * <p>
 * 面试亮点：
 * - 插件化架构（类似 Spring Plugin）
 * - 支持工具热插拔
 * - 工具版本管理与兼容性检查
 * - 提供工具检索API（按名称、分类、标签）
 *
 * @author Aero-Fin Team
 */
@Slf4j
@Component
public class ToolRegistry {

    /**
     * 工具存储
     * Key: 工具名称, Value: 工具实例
     */
    private final Map<String, McpTool<?, ?>> tools = new ConcurrentHashMap<>();

    /**
     * 工具元数据缓存
     */
    private final Map<String, McpTool.ToolMetadata> metadataCache = new ConcurrentHashMap<>();

    /**
     * 分类索引
     * Key: 分类名称, Value: 工具名称列表
     */
    private final Map<String, List<String>> categoryIndex = new ConcurrentHashMap<>();

    /**
     * 标签索引
     * Key: 标签, Value: 工具名称列表
     */
    private final Map<String, List<String>> tagIndex = new ConcurrentHashMap<>();

    /**
     * 注册工具
     *
     * @param tool 工具实例
     */
    public void registerTool(McpTool<?, ?> tool) {
        McpTool.ToolMetadata metadata = tool.getMetadata();
        String toolName = metadata.getName();

        if (tools.containsKey(toolName)) {
            log.warn("Tool already registered, overwriting: {}", toolName);
        }

        // 注册工具
        tools.put(toolName, tool);
        metadataCache.put(toolName, metadata);

        // 更新分类索引
        categoryIndex.computeIfAbsent(metadata.getCategory(), k -> new ArrayList<>()).add(toolName);

        // 更新标签索引
        if (metadata.getTags() != null) {
            metadata.getTags().forEach(tag ->
                    tagIndex.computeIfAbsent(tag, k -> new ArrayList<>()).add(toolName)
            );
        }

        log.info("✅ Registered tool: {} (category: {}, version: {})",
                toolName, metadata.getCategory(), metadata.getVersion());
    }

    /**
     * 批量注册工具
     */
    public void registerTools(List<McpTool<?, ?>> toolList) {
        toolList.forEach(this::registerTool);
    }

    /**
     * 注销工具
     */
    public void unregisterTool(String toolName) {
        McpTool<?, ?> removed = tools.remove(toolName);
        if (removed != null) {
            metadataCache.remove(toolName);
            log.info("❌ Unregistered tool: {}", toolName);
        }
    }

    /**
     * 获取工具
     */
    @SuppressWarnings("unchecked")
    public <I, O> McpTool<I, O> getTool(String toolName) {
        return (McpTool<I, O>) tools.get(toolName);
    }

    /**
     * 检查工具是否存在
     */
    public boolean hasTool(String toolName) {
        return tools.containsKey(toolName);
    }

    /**
     * 获取工具元数据
     */
    public McpTool.ToolMetadata getToolMetadata(String toolName) {
        return metadataCache.get(toolName);
    }

    /**
     * 获取所有工具名称
     */
    public Set<String> getAllToolNames() {
        return new HashSet<>(tools.keySet());
    }

    /**
     * 按分类获取工具
     */
    public List<String> getToolsByCategory(String category) {
        return categoryIndex.getOrDefault(category, Collections.emptyList());
    }

    /**
     * 按标签获取工具
     */
    public List<String> getToolsByTag(String tag) {
        return tagIndex.getOrDefault(tag, Collections.emptyList());
    }

    /**
     * 搜索工具
     * <p>
     * 支持按名称、描述、标签搜索
     */
    public List<McpTool.ToolMetadata> searchTools(String keyword) {
        return metadataCache.values().stream()
                .filter(metadata -> matchesKeyword(metadata, keyword))
                .toList();
    }

    /**
     * 获取所有工具元数据（用于 LLM）
     */
    public List<McpTool.ToolMetadata> getAllToolMetadata() {
        return new ArrayList<>(metadataCache.values());
    }

    /**
     * 生成工具目录（Markdown 格式）
     */
    public String generateToolCatalog() {
        StringBuilder catalog = new StringBuilder();
        catalog.append("# 可用工具目录\n\n");

        metadataCache.values().forEach(metadata -> {
            catalog.append(String.format("## %s\n", metadata.getName()));
            catalog.append(String.format("**描述**: %s\n\n", metadata.getDescription()));
            catalog.append(String.format("**分类**: %s\n\n", metadata.getCategory()));

            if (metadata.getParameters() != null && !metadata.getParameters().isEmpty()) {
                catalog.append("**参数**:\n");
                metadata.getParameters().forEach(param -> {
                    catalog.append(String.format("- `%s` (%s)%s: %s\n",
                            param.getName(),
                            param.getType(),
                            param.getRequired() ? " [必需]" : "",
                            param.getDescription()
                    ));
                });
                catalog.append("\n");
            }

            catalog.append("---\n\n");
        });

        return catalog.toString();
    }

    /**
     * 验证工具依赖
     * <p>
     * 检查工具所依赖的其他工具是否已注册
     */
    public boolean validateDependencies(String toolName) {
        McpTool.ToolMetadata metadata = metadataCache.get(toolName);
        if (metadata == null || metadata.getDependencies() == null) {
            return true;
        }

        for (String dependency : metadata.getDependencies()) {
            if (!tools.containsKey(dependency)) {
                log.error("Missing dependency for tool {}: {}", toolName, dependency);
                return false;
            }
        }

        return true;
    }

    /**
     * 获取工具统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalTools", tools.size());
        stats.put("categories", categoryIndex.size());
        stats.put("tags", tagIndex.size());
        stats.put("cacheableTools", metadataCache.values().stream()
                .filter(McpTool.ToolMetadata::getCacheable)
                .count());
        stats.put("asyncTools", metadataCache.values().stream()
                .filter(McpTool.ToolMetadata::getAsync)
                .count());

        return stats;
    }

    // ==================== 辅助方法 ====================

    /**
     * 检查元数据是否匹配关键词
     */
    private boolean matchesKeyword(McpTool.ToolMetadata metadata, String keyword) {
        String lowerKeyword = keyword.toLowerCase();
        return metadata.getName().toLowerCase().contains(lowerKeyword) ||
                metadata.getDescription().toLowerCase().contains(lowerKeyword) ||
                (metadata.getTags() != null && metadata.getTags().stream()
                        .anyMatch(tag -> tag.toLowerCase().contains(lowerKeyword)));
    }
}
