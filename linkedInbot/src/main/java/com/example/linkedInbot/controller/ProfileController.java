package com.example.linkedInbot.controller;

import com.example.linkedInbot.model.UserProfile;
import com.example.linkedInbot.service.ProfileStore.ProfileStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Endpoints for saving / retrieving user profiles, stored in profiles.json
 * on disk. This lets the frontend auto-fill the form for a returning user
 * (by email) regardless of which browser or tab is used on this device.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // adjust if you lock down CORS elsewhere
public class ProfileController {

    private final ProfileStoreService profileStore;

    /**
     * Save or update a profile. Called whenever the user clicks
     * "Save Configuration" on the frontend (in addition to /api/config).
     */
    @PostMapping("/profile")
    public ResponseEntity<?> saveProfile(@RequestBody UserProfile profile) {
        if (profile.getEmail() == null || profile.getEmail().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is required to save a profile"));
        }
        UserProfile saved = profileStore.save(profile);
        return ResponseEntity.ok(Map.of(
                "message", "Profile saved",
                "email", saved.getEmail()
        ));
    }

    /**
     * Fetch a single profile by email — used to auto-fill the form
     * when the user picks their email from the dropdown.
     */
    @GetMapping("/profile/{email}")
    public ResponseEntity<?> getProfile(@PathVariable String email) {
        UserProfile profile = profileStore.getByEmail(email);
        if (profile == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(profile);
    }

    /**
     * List all saved profiles (lightweight — email + workTitle), used to
     * populate the dropdown under the email field on page load.
     */
    @GetMapping("/profiles")
    public ResponseEntity<?> listProfiles() {
        Map<String, Map<String, String>> summary = new LinkedHashMap<>();
        profileStore.listAll().forEach((email, profile) -> {
            Map<String, String> info = new LinkedHashMap<>();
            info.put("email", profile.getEmail());
            info.put("workTitle", profile.getWorkTitle() == null ? "" : profile.getWorkTitle());
            summary.put(email, info);
        });
        return ResponseEntity.ok(summary);
    }

    /**
     * Delete a saved profile (the "✕" button in the dropdown).
     */
    @DeleteMapping("/profile/{email}")
    public ResponseEntity<?> deleteProfile(@PathVariable String email) {
        boolean removed = profileStore.delete(email);
        if (!removed) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("message", "Profile deleted"));
    }
}