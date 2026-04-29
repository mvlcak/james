package dev.mvlcak.james.ai.config;

import dev.mvlcak.james.ai.tool.DiffTool;
import dev.mvlcak.james.ai.tool.SchemaSearchTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.GrepTool;
import org.springaicommunity.agent.tools.ShellTools;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class ToolConfig {


    private static final Logger log = LoggerFactory.getLogger(ToolConfig.class);

    @Bean
    public GrepTool grepTool() {
        return GrepTool.builder().build();
    }

    @Bean
    public FileSystemTools fileSystemTools() {
        return FileSystemTools.builder().build();
    }

    @Bean
    public ShellTools shellTools() {
        return ShellTools.builder().build();
    }

    @Bean
    public DiffTool diffTool() {
        return DiffTool.builder().build();
    }

    @Bean
    public SchemaSearchTools schemaSearchTools(JdbcTemplate jdbcTemplate) {
        try { jdbcTemplate.queryForObject("SELECT 1", Integer.class); }
        catch (Exception e) { log.error(e.getMessage()); }
        return SchemaSearchTools.builder().build(jdbcTemplate);
    }

}
