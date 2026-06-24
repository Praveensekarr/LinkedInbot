package com.example.linkedInbot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a saved user profile (mirrors the React "config" object).
 * Persisted as a row in profiles.xlsx (see ProfileStoreService).
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserProfile {

    private String email = "";
    private String password = "";

    private String searchUrl = "";

    private String targetLocation = "";
    private String currentCity = "";

    private String currentCtc = "";
    private String currentCtcMonthly = "";
    private String expectedCtc = "";
    private String expectedCtcMonthly = "";

    private String noticePeriod = "";
    private String workTitle = "";
    private String defaultCompany = "";
    private String portfolioUrl = "";
    private String experienceYears = "";
    private String experienceMonths = "";
    private String defaultNumber = "0";

    private String fromMonth = "";
    private String fromYear  = "";
    private String toMonth   = "";
    private String toYear    = "";

    private String firstName = "";
    private String lastName = "";
    private String phoneNumber = "";
    private String photoPath = "";
    private String gender = "";

    /**
     * Stored as a single JSON-encoded string in one Excel cell
     * (e.g. {"github":"https://..."}), since Excel rows can't hold
     * nested maps directly.
     */
    private Map<String, String> customFields = new HashMap<>();
}