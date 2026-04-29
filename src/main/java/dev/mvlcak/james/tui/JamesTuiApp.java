package dev.mvlcak.james.tui;

import dev.mvlcak.james.tui.config.TuiProperties;
import dev.tamboui.style.Color;
import dev.tamboui.toolkit.app.ToolkitApp;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;

import java.time.Duration;

import static dev.tamboui.toolkit.Toolkit.*;

public class JamesTuiApp extends ToolkitApp {

    private final JamesAppState state;
    private final TuiProperties tuiProperties;
    private final ChatPane chatPane;

    public JamesTuiApp(JamesAppState state, TuiProperties tuiProperties, ChatPane chatPane) {
        this.state = state;
        this.tuiProperties = tuiProperties;
        this.chatPane = chatPane;
    }

    @Override
    protected TuiConfig configure() {
        return TuiConfig.builder()
                .tickRate(Duration.ofMillis(tuiProperties.tickRateMs()))
                .resizeGracePeriod(Duration.ofMillis(tuiProperties.resizeGracePeriodMs()))
                .mouseCapture(true)
                .build();
    }

    @Override
    protected Element render() {
        return switch (state.currentScreen()) {
            case HELP -> renderHelpScreen();
            case CHAT -> renderChatScreen();
        };
    }

    private Element renderHelpScreen() {
        String helpText = """
                /clear Clear the current session transcript and workflow
                /help  Show this help screen

                Esc, q, or Enter returns to chat.
                Ctrl+C quits the application.
                """;
        return panel("Help",
                column(richTextArea(helpText))
        ).rounded().fill().id("root").focusable().onKeyEvent(this::handleRootEvent);
    }

    private EventResult handleRootEvent(KeyEvent event) {
        if (event.isCtrlC()) {
            quit();
            return EventResult.HANDLED;
        }

        if (state.currentScreen() == ScreenMode.HELP) {
            if (event.isCancel() || event.isConfirm() || event.isCharIgnoreCase('q')) {
                state.switchScreen(ScreenMode.CHAT);
                return EventResult.HANDLED;
            }
            return EventResult.UNHANDLED;
        }

        return handleChatScreenEvent(event);
    }

    private EventResult handleChatScreenEvent(KeyEvent event) {
        if (event.isKey(KeyCode.F1)) {
            state.switchScreen(ScreenMode.HELP);
            return EventResult.HANDLED;
        }

        return chatPane.handleKeyEvent(event, true);
    }

    private Element renderDivider() {
        return row(text("─".repeat(500)).fg(Color.BLUE)).length(1);
    }

    private Element renderFooter() {
        return row(
                text(" F1").bold().fg(Color.CYAN),
                text(" Help  ·  "),
                text("PgUp/Dn").bold().fg(Color.CYAN),
                text(" Scroll  ·  "),
                text("Ctrl+C").bold().fg(Color.CYAN),
                text(" Quit")
        ).length(1);
    }

    private Element renderChatScreen() {
        return column(
                chatPane,
                renderFooter()
        ).fill().id("root").focusable().onKeyEvent(this::handleRootEvent);
    }

}
