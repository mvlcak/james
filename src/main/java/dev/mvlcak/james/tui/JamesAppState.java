package dev.mvlcak.james.tui;

import java.util.ArrayList;
import java.util.List;

public class JamesAppState {

    private static final int NO_PENDING_SLOT = -1;

    private ScreenMode screenMode = ScreenMode.CHAT;
    private final String workingDirectory = System.getProperty("user.dir");
    private final List<ChatTranscriptEntry> messages = new ArrayList<>();
    private boolean streaming;
    private int pendingAssistantSlot = NO_PENDING_SLOT;

    public synchronized ScreenMode currentScreen() {
        return screenMode;
    }

    public synchronized void switchScreen(ScreenMode screen) {
        this.screenMode = screen;
    }

    public String workingDirectory() {
        return workingDirectory;
    }

    public synchronized List<ChatTranscriptEntry> messages() {
        return List.copyOf(messages);
    }

    public synchronized boolean isStreaming() {
        return streaming;
    }

    public synchronized void appendUserMessage(String text) {
        messages.add(new ChatTranscriptEntry(ChatRole.USER, text));
    }

    public synchronized void appendSystemMessage(String text) {
        messages.add(new ChatTranscriptEntry(ChatRole.SYSTEM, text));
    }

    public synchronized void startAssistantResponse() {
        pendingAssistantSlot = messages.size();
        messages.add(new ChatTranscriptEntry(ChatRole.ASSISTANT, ""));
        streaming = true;
    }

    public synchronized void completeAssistantResponse(String fallbackIfEmpty) {
        if (pendingAssistantSlot >= 0) {
            ChatTranscriptEntry pending = messages.get(pendingAssistantSlot);
            boolean isEmpty = pending.text() == null || pending.text().isBlank();
            boolean hasFallback = fallbackIfEmpty != null && !fallbackIfEmpty.isBlank();
            if (isEmpty && hasFallback) {
                messages.set(pendingAssistantSlot,
                        new ChatTranscriptEntry(ChatRole.ASSISTANT, fallbackIfEmpty));
            }
        }
        pendingAssistantSlot = NO_PENDING_SLOT;
        streaming = false;
    }

    public synchronized void abortAssistantResponse(String reason) {
        if (pendingAssistantSlot >= 0) {
            messages.remove(pendingAssistantSlot);
            pendingAssistantSlot = NO_PENDING_SLOT;
        }
        streaming = false;
        appendSystemMessage(reason);
    }
}
