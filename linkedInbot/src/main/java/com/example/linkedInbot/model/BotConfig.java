package com.example.linkedInbot.model;

import lombok.Data;

@Data
public class BotConfig {

    // ── LinkedIn credentials ──────────────────────────────────────────────────
    private String email;
    private String password;

    // ── Job search URL (dynamic, built or pasted by the user) ─────────────────
    private String searchUrl;

    // ── Location / city ───────────────────────────────────────────────────────
    private String targetLocation;   // e.g. "Chennai"
    private String currentCity;      // same or different city text for forms

    // ── CTC (annual in LPA, monthly in ₹) ────────────────────────────────────
    private String currentCtc;          // e.g. "0" or "4"
    private String currentCtcMonthly;   // e.g. "0" or "50000"
    private String expectedCtc;         // annual LPA
    private String expectedCtcMonthly;  // monthly ₹
    private String experienceYears;     // Experience in years
    private String experienceMonths;    // Experience in months

    // ── Notice period (in days / as text) ─────────────────────────────────────
    private String noticePeriod;        // e.g. "0" or "30"

    // ── Professional profile ──────────────────────────────────────────────────
    private String workTitle;           // e.g. "Software Developer"
    private String defaultCompany;      // e.g. "Self-Employed"
    private String portfolioUrl;        // GitHub / portfolio link

    // ── Generic numeric fallback ──────────────────────────────────────────────
    private String defaultNumber;       // e.g. "0" — used for unknown numeric fields

    private String fromMonth;  // e.g. "6" for June
    private String fromYear;   // e.g. "2020"
    private String toMonth;    // e.g. "3" for March
    private String toYear;     // e.g. "2021"

    // ── Personal details (used in dialog-box form filling) ───────────────────
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String gender;

    // ── Additional custom fields the user wants auto-filled ───────────────────
    // key = label keyword (e.g. "github"), value = text to fill
    private java.util.Map<String, String> customFields;
    private String photoPath;
}