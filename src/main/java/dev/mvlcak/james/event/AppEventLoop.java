package dev.mvlcak.james.event;

import dev.mvlcak.james.chat.StreamingChatService;
import dev.mvlcak.james.tui.JamesAppState;

public class AppEventLoop {

    private final AppEventBus bus;
    private final JamesAppState appState;
    private final StreamingChatService streamingChatService;

    public AppEventLoop(AppEventBus bus, JamesAppState appState, StreamingChatService streamingChatService) {
        this.bus = bus;
        this.appState = appState;
        this.streamingChatService = streamingChatService;
    }

    public void start() {
        Thread.ofVirtual().name("app-event-loop").start(this::run);
    }

    private void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                process(bus.take());
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void process(AppEvent event) {
        switch (event) {

            case AppEvent.UserInput(String text) -> {
                if (appState.isStreaming()) {
                    appState.appendSystemMessage("A response is already streaming.");
                    return;
                }
                appState.appendUserMessage(text);
                appState.startAssistantResponse();
                streamingChatService.startStream(text);
            }
            case AppEvent.AssistantComplete(String fallbackText) -> appState.completeAssistantResponse(fallbackText);
            case AppEvent.AssistantFail(String error) -> appState.abortAssistantResponse(error);
            case AppEvent.SystemMessage(String text) -> appState.appendSystemMessage(text);
        }
    }
}
