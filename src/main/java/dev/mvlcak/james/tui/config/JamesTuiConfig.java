package dev.mvlcak.james.tui.config;

import dev.mvlcak.james.chat.StreamingChatService;
import dev.mvlcak.james.tui.CommandParser;
import dev.mvlcak.james.event.AppEventBus;
import dev.mvlcak.james.event.AppEventLoop;
import dev.mvlcak.james.tui.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JamesTuiConfig {


    @Bean
    public JamesTuiApp jamesTuiApp(JamesAppState jamesAppState, TuiProperties tuiProperties, ChatPane chatPane){
        return new JamesTuiApp(jamesAppState, tuiProperties, chatPane);
    }

    @Bean
    public ChatPane chatPane(JamesAppState appState, CommandParser commandParser, AppEventBus appEventBus) {
        return new ChatPane(appState, appEventBus, commandParser);
    }

    @Bean
    public AppEventBus appEventBus() {
        return new AppEventBus();
    }


    @Bean
    public AppEventLoop appEventLoop(AppEventBus appEventBus, JamesAppState appState,
                                     StreamingChatService streamingChatService) {
        return new AppEventLoop(appEventBus, appState, streamingChatService);
    }

    @Bean
    public CommandParser commandParser(){
        return new CommandParser();
    }

    @Bean
    public ApplicationRunner tuiApplicationRunner(JamesTuiApp jamesTuiApp,
                                                  AppEventBus appEventBus,
                                                  AppEventLoop appEventLoop) {
        return new ApplicationRunner() {
            @Override
            public void run(ApplicationArguments args) throws Exception {
                appEventLoop.start();
                try {
                    jamesTuiApp.run();
                }
                catch (Exception e) {
                    System.out.println("Terminal not supported");
                    System.exit(1);
                }
            }
        };
    }
}
