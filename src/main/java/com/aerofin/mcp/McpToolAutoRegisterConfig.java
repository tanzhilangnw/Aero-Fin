package com.aerofin.mcp;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * MCP 工具自动注册配置
 *
 * 启动时扫描 Spring 容器中的所有 McpTool Bean 并注册到 ToolRegistry。
 * 这样 ToolRegistry 才能真正用于“工具发现/目录/插件化扩展”等能力。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class McpToolAutoRegisterConfig {

    private final ToolRegistry toolRegistry;
    private final List<McpTool<?, ?>> tools;

    @PostConstruct
    public void registerAllTools() {
        toolRegistry.registerTools(tools);
        log.info("✅ MCP tools auto-registered: {}", toolRegistry.getAllToolNames());
    }
}


