package com.example.linkedInbot.service.login;

import com.example.linkedInbot.service.WebDriver.WebDriverFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class LinkedInLoginService {

    private final WebDriverFactory webDriverFactory;

    public WebDriver login(String email, String password) {

        WebDriver driver = webDriverFactory.createDriver(false);

        try {
            driver.get("https://www.linkedin.com/checkpoint/lg/login");
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));

            Thread.sleep(2000);

            WebElement emailField = wait.until(
                    ExpectedConditions.elementToBeClickable(By.cssSelector("input#username, input#session_key, input[name='session_key']"))
            );
            emailField.clear();
            for (char c : email.toCharArray()) {
                emailField.sendKeys(String.valueOf(c));
                Thread.sleep(50 + (long)(Math.random() * 100));
            }

            Thread.sleep(500);

            //WebElement passwordField = driver.findElement(By.id("password"));
            WebElement passwordField = wait.until(
                    ExpectedConditions.elementToBeClickable(By.cssSelector("input#password, input#session_password, input[name='session_password']"))
            );
            passwordField.clear();
            for (char c : password.toCharArray()) {
                passwordField.sendKeys(String.valueOf(c));
                Thread.sleep(50 + (long)(Math.random() * 100));
            }

            Thread.sleep(800);

           // WebElement loginButton = driver.findElement(By.xpath("//button[@type='submit']"));
            WebElement loginButton = wait.until(
                    ExpectedConditions.elementToBeClickable(By.cssSelector("button[type='submit'], button.btn__primary--large"))
            );
            loginButton.click();

            wait.until(ExpectedConditions.or(
                    ExpectedConditions.urlContains("feed"),
                    ExpectedConditions.urlContains("checkpoint"),
                    ExpectedConditions.urlContains("challenge"),
                    ExpectedConditions.visibilityOfElementLocated(By.id("error-for-username")),
                    ExpectedConditions.visibilityOfElementLocated(By.id("error-for-password"))
            ));

            String pageSource = driver.getPageSource();
            String currentUrl = driver.getCurrentUrl();

            if (pageSource.contains("password was incorrect")) {
                throw new RuntimeException("Invalid LinkedIn - Password");
            }

            if (currentUrl.contains("checkpoint") || currentUrl.contains("challenge")
                    || pageSource.contains("captcha")) {
                log.warn("LinkedIn security check triggered — manual intervention may be needed");

                wait = new WebDriverWait(driver, Duration.ofSeconds(60));

                try {
                    wait.until(ExpectedConditions.urlContains("feed"));
                    log.info("Security check passed manually.");
                } catch (Exception e) {
                    throw new RuntimeException("LinkedIn CAPTCHA/challenge not resolved within 60 seconds");
                }
            }

            log.info("Login successful, current URL: {}", driver.getCurrentUrl());
            return driver;

        } catch (Exception ex) {
            log.error("Login failed: {}", ex.getMessage());
            driver.quit();
            throw new RuntimeException("Login Failed " + ex.getMessage());
        }
    }
}