package com.example.linkedInbot.service.BotConfig;

import com.example.linkedInbot.model.BotConfig;
import org.springframework.stereotype.Component;

@Component
public class BotConfigStore {

    private volatile BotConfig current;

    public void save(BotConfig config) {
        this.current = config;
    }

    public BotConfig get() {
        return current;
    }

    public boolean isConfigured() {
        return current != null
                && current.getEmail() != null && !current.getEmail().isBlank()
                && current.getSearchUrl() != null && !current.getSearchUrl().isBlank();
    }
}