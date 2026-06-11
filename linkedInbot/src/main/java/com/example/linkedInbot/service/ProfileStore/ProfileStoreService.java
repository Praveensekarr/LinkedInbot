package com.example.linkedInbot.service.ProfileStore;

import com.example.linkedInbot.config.AppDataLocation;
import com.example.linkedInbot.model.UserProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Stores user profiles as rows in profiles.xlsx, located in an external
 * data folder (see AppDataLocation) — OUTSIDE the project/codebase.
 *
 * One row per profile, keyed by email (column 0). All UserProfile fields
 * map to columns in a fixed order (see COLUMNS). The customFields map is
 * serialized to a single JSON string in its own column.
 */
@Slf4j
@Service
public class ProfileStoreService {

    private static final String FILE_NAME = "profiles.xlsx";
    private static final String SHEET_NAME = "Profiles";

    /**
     * Fixed column order. Index 0 (email) is the lookup key.
     */
    private static final String[] COLUMNS = {
            "email", "password", "searchUrl",
            "targetLocation", "currentCity",
            "currentCtc", "currentCtcMonthly", "expectedCtc", "expectedCtcMonthly",
            "noticePeriod", "workTitle", "defaultCompany", "portfolioUrl",
            "experienceYears", "experienceMonths", "defaultNumber",
            "firstName", "lastName", "phoneNumber", "photoPath", "gender",
            "customFields" // JSON-encoded map
    };

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ReentrantLock lock = new ReentrantLock();

    private File file() {
        return AppDataLocation.resolve(FILE_NAME);
    }

    // ── Public API ────────────────────────────────────────────────────────

    public Map<String, UserProfile> loadAll() {
        lock.lock();
        try {
            return loadAllUnlocked();
        } finally {
            lock.unlock();
        }
    }

    public UserProfile getByEmail(String email) {
        if (email == null || email.isBlank()) return null;
        return loadAll().get(email.trim().toLowerCase());
    }

    public UserProfile save(UserProfile profile) {
        if (profile.getEmail() == null || profile.getEmail().isBlank()) {
            throw new IllegalArgumentException("Profile email is required");
        }
        String key = profile.getEmail().trim().toLowerCase();

        lock.lock();
        try {
            Map<String, UserProfile> all = loadAllUnlocked();
            all.put(key, profile);
            writeAllUnlocked(all);
            return profile;
        } finally {
            lock.unlock();
        }
    }

    public boolean delete(String email) {
        if (email == null || email.isBlank()) return false;
        String key = email.trim().toLowerCase();

        lock.lock();
        try {
            Map<String, UserProfile> all = loadAllUnlocked();
            UserProfile removed = all.remove(key);
            if (removed != null) {
                writeAllUnlocked(all);
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public Map<String, UserProfile> listAll() {
        return loadAll();
    }

    // ── Excel read/write internals ───────────────────────────────────────

    private Map<String, UserProfile> loadAllUnlocked() {
        Map<String, UserProfile> result = new LinkedHashMap<>();
        File f = file();
        if (!f.exists()) {
            return result;
        }

        try (FileInputStream fis = new FileInputStream(f);
             Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getSheet(SHEET_NAME);
            if (sheet == null) return result;

            int colCount = COLUMNS.length;
            for (int r = 1; r <= sheet.getLastRowNum(); r++) { // row 0 = header
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String email = getCellString(row, 0);
                if (email == null || email.isBlank()) continue;

                UserProfile profile = rowToProfile(row, colCount);
                result.put(email.trim().toLowerCase(), profile);
            }
        } catch (IOException e) {
            log.error("Failed to read {}: {}", FILE_NAME, e.getMessage());
        }
        return result;
    }

    private void writeAllUnlocked(Map<String, UserProfile> all) {
        File f = file();
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(SHEET_NAME);

            // Header row
            Row header = sheet.createRow(0);
            CellStyle headerStyle = headerStyle(wb);
            for (int c = 0; c < COLUMNS.length; c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(COLUMNS[c]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            int r = 1;
            for (UserProfile profile : all.values()) {
                Row row = sheet.createRow(r++);
                profileToRow(profile, row);
            }

            for (int c = 0; c < COLUMNS.length; c++) {
                sheet.autoSizeColumn(c);
            }

            try (FileOutputStream fos = new FileOutputStream(f)) {
                wb.write(fos);
            }
        } catch (IOException e) {
            log.error("Failed to write {}: {}", FILE_NAME, e.getMessage());
            throw new RuntimeException("Could not save profile to " + f.getAbsolutePath(), e);
        }
    }

    private CellStyle headerStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private UserProfile rowToProfile(Row row, int colCount) {
        UserProfile p = new UserProfile();
        p.setEmail(getCellString(row, 0));
        p.setPassword(getCellString(row, 1));
        p.setSearchUrl(getCellString(row, 2));
        p.setTargetLocation(getCellString(row, 3));
        p.setCurrentCity(getCellString(row, 4));
        p.setCurrentCtc(getCellString(row, 5));
        p.setCurrentCtcMonthly(getCellString(row, 6));
        p.setExpectedCtc(getCellString(row, 7));
        p.setExpectedCtcMonthly(getCellString(row, 8));
        p.setNoticePeriod(getCellString(row, 9));
        p.setWorkTitle(getCellString(row, 10));
        p.setDefaultCompany(getCellString(row, 11));
        p.setPortfolioUrl(getCellString(row, 12));
        p.setExperienceYears(getCellString(row, 13));
        p.setExperienceMonths(getCellString(row, 14));
        p.setDefaultNumber(getCellString(row, 15));
        p.setFirstName(getCellString(row, 16));
        p.setLastName(getCellString(row, 17));
        p.setPhoneNumber(getCellString(row, 18));
        p.setPhotoPath(getCellString(row, 19));
        p.setGender(getCellString(row, 20));

        String customFieldsJson = getCellString(row, 21);
        if (customFieldsJson != null && !customFieldsJson.isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, String> map = objectMapper.readValue(customFieldsJson, Map.class);
                p.setCustomFields(map);
            } catch (IOException e) {
                log.warn("Could not parse customFields JSON for {}: {}", p.getEmail(), e.getMessage());
            }
        }
        return p;
    }

    private void profileToRow(UserProfile p, Row row) {
        setCell(row, 0, p.getEmail());
        setCell(row, 1, p.getPassword());
        setCell(row, 2, p.getSearchUrl());
        setCell(row, 3, p.getTargetLocation());
        setCell(row, 4, p.getCurrentCity());
        setCell(row, 5, p.getCurrentCtc());
        setCell(row, 6, p.getCurrentCtcMonthly());
        setCell(row, 7, p.getExpectedCtc());
        setCell(row, 8, p.getExpectedCtcMonthly());
        setCell(row, 9, p.getNoticePeriod());
        setCell(row, 10, p.getWorkTitle());
        setCell(row, 11, p.getDefaultCompany());
        setCell(row, 12, p.getPortfolioUrl());
        setCell(row, 13, p.getExperienceYears());
        setCell(row, 14, p.getExperienceMonths());
        setCell(row, 15, p.getDefaultNumber());
        setCell(row, 16, p.getFirstName());
        setCell(row, 17, p.getLastName());
        setCell(row, 18, p.getPhoneNumber());
        setCell(row, 19, p.getPhotoPath());
        setCell(row, 20, p.getGender());

        String customFieldsJson = "";
        try {
            customFieldsJson = objectMapper.writeValueAsString(
                    p.getCustomFields() == null ? Map.of() : p.getCustomFields());
        } catch (IOException e) {
            log.warn("Could not serialize customFields for {}: {}", p.getEmail(), e.getMessage());
        }
        setCell(row, 21, customFieldsJson);
    }

    private void setCell(Row row, int col, String value) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value == null ? "" : value);
    }

    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                yield (d == Math.floor(d)) ? String.valueOf((long) d) : String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }
}