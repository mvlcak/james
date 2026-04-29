package dev.mvlcak.james.event;

public sealed interface AppEvent permits
        AppEvent.UserInput, AppEvent.AssistantFail,
        AppEvent.SystemMessage, AppEvent.AssistantComplete {

    record UserInput(String text) implements AppEvent {}
    record AssistantComplete(String fallbackText) implements AppEvent {}
    record AssistantFail(String error) implements AppEvent {}
    record SystemMessage(String text) implements AppEvent {}
}
