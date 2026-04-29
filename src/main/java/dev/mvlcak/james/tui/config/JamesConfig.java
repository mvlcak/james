package dev.mvlcak.james.tui.config;

import dev.mvlcak.james.tui.JamesAppState;
import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JamesConfig {
    @Bean
    public JamesAppState appState() {
        return new JamesAppState();
    }

}
