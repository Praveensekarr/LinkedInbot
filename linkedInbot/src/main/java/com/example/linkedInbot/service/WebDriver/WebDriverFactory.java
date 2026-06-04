package com.example.linkedInbot.service.WebDriver;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class WebDriverFactory {

    public WebDriver createDriver(boolean headless) {

        ChromeOptions options = new ChromeOptions();

        options.addArguments("--disable-blink-features=AutomationControlled");

        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);

        options.addArguments("--start-maximized");
        options.addArguments("--disable-notifications");
        options.addArguments("--disable-popup-blocking");
        options.addArguments("--disable-infobars");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--disable-gpu");

        if (headless) {
            options.addArguments("--headless=new");
            options.addArguments("--window-size=1920,1080");
        }

        log.info("Launching Browser {}", headless);

        WebDriver driver = new ChromeDriver(options);

        try {
            String actualUA = (String) ((JavascriptExecutor) driver).executeScript("return navigator.userAgent;");
            String cleanUA = actualUA.replace("HeadlessChrome", "Chrome")
                    .replace("selenium", "");

            Map<String, Object> uaParams = new HashMap<>();
            uaParams.put("userAgent", cleanUA);
            ((ChromeDriver) driver).executeCdpCommand("Network.setUserAgentOverride", uaParams);

            Map<String, Object> stealthParams = new HashMap<>();
            stealthParams.put("source", "Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");
            ((ChromeDriver) driver).executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", stealthParams);

            log.info("Dynamic User-Agent applied: {}", cleanUA);
            log.info("Navigator stealth patch applied via CDP.");
        } catch (Exception e) {
            log.warn("CDP patches failed, attempting JS fallback: {}", e.getMessage());
            try {
                ((JavascriptExecutor) driver).executeScript(
                        "Object.defineProperty(navigator, 'webdriver', {get: () => undefined})"
                );
                log.info("navigator.webdriver patch applied");
            }  catch (Exception ignored) {}
        }

        return driver;
    }

}

