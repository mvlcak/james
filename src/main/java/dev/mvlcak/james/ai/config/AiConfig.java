package dev.mvlcak.james.ai.config;

import dev.mvlcak.james.ai.tool.DiffTool;
import dev.mvlcak.james.ai.tool.SchemaSearchTools;
import dev.mvlcak.james.chat.StreamingChatService;
import dev.mvlcak.james.event.AppEventBus;
import dev.mvlcak.james.tui.JamesAppState;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.GrepTool;
import org.springaicommunity.agent.tools.ShellTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Schedulers;

@Configuration
public class AiConfig {

	@Bean
	public MessageChatMemoryAdvisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
		return MessageChatMemoryAdvisor.builder(chatMemory)
				.scheduler(Schedulers.boundedElastic())
				.build();
	}

    @Bean
	public ChatClient chatClient(ChatModel chatModel, GrepTool grepTool, FileSystemTools fileSystemTools, ShellTools shellTools, MessageChatMemoryAdvisor messageChatMemoryAdvisor, DiffTool diffTool, SchemaSearchTools schemaSearchTools) {

		return ChatClient.builder(chatModel)
				.defaultSystem("""
						You are a helpful coding assistant named James. You have access to tools
						for reading files, searching code, running shell commands, searching for db tables,
						getting their schema and editing files. Use them to help the user with their codebase
						and help with creating sql queries.
						
						You are connected to Microsoft SQL Server db
						
						With findTables and getTAbleScript you are connected to database for which
						you should write queries. You search for table name and then get schema of table.
						Based on that create queries for user by writing that sql to file.
						You must always use this tool to get schema of tables you are connected with.
						
						When user asks about some file to edit it or write to it, scan all directories
						in workdir for this class and find it.

						Always give summary what you did after prompt of client.
						    Always after doing work(writing to files or creating files) use diff tool to show client what you have done.

						Current directory: %s
						""".formatted(System.getProperty("user.dir")))
				.defaultAdvisors(messageChatMemoryAdvisor)
				.defaultTools(grepTool, fileSystemTools, shellTools, diffTool, schemaSearchTools)
				.build();
    }


	@Bean
	public StreamingChatService streamingChatService(@Qualifier("chatClient") ChatClient statelessChatClient,
	                                                 JamesAppState jamesAppState, AppEventBus appEventBus) {
		return new StreamingChatService(statelessChatClient, jamesAppState, appEventBus);
	}

}
