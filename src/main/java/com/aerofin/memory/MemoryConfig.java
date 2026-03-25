package com.aerofin.memory;

import com.aerofin.config.AeroFinProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the layered memory system.
 * <p>
 * Short-term memory window size is driven by
 * {@code aero-fin.conversation.context-window-size} so it stays in sync
 * with the existing conversation service configuration.
 */
@Configuration
@RequiredArgsConstructor
public class MemoryConfig {

    private final AeroFinProperties properties;

    @Bean
    public ShortTermMemory shortTermMemory() {
        int windowSize = properties.getConversation().getContextWindowSize();
        return new ShortTermMemory(windowSize);
    }
}
