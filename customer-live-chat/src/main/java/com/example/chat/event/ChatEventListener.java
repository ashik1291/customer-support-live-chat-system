package com.example.chat.event;

public interface ChatEventListener {

    void onLifecycleEvent(ChatEvent event);

    void onMessageEvent(ChatMessageEvent event);
}

