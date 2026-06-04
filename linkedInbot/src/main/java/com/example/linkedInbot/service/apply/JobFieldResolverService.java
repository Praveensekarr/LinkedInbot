package com.example.linkedInbot.service.apply;

import com.example.linkedInbot.model.BotConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Map;

@Slf4j
@Service
public class JobFieldResolverService {

    public String resolveFieldValue(String label, BotConfig c) {
        if (c.getCustomFields() != null) {
            for (Map.Entry<String, String> entry : c.getCustomFields().entrySet()) {
                if (label.contains(entry.getKey().toLowerCase())) {
                    return entry.getValue();
                }
            }
        }

        if (label.contains("reside") || label.contains("city")) {
            return c.getTargetLocation() != null ? c.getTargetLocation() : "";
        }
        if (label.contains("scale") || label.contains("rate") || label.contains("1-10")) {
            return "7";
        }
        if (label.contains("your title") || label.contains("job title") || label.contains("designation")) {
            return c.getWorkTitle() != null ? c.getWorkTitle() : "Developer";
        }
        if (label.contains("notice") || label.contains("notice period") || label.contains("join")
                || label.contains("how soon") || label.contains("can you start") || label.contains("can you join")) {
            return c.getNoticePeriod() != null ? c.getNoticePeriod() : "0";
        }
        if (label.contains("company") || label.contains("employer")) {
            return c.getDefaultCompany() != null ? c.getDefaultCompany() : "Self-Employed";
        }
        if (label.contains("degree") || label.contains("qualification"))
            return "B.E / B.Tech";

        if (label.contains("field of study") || label.contains("major"))
            return "Computer Science Engineering";

        if (label.contains("school") || label.contains("university")
                || label.contains("college") || label.contains("institution"))
            return "Anna University";
        if (label.contains("full name"))
            return c.getFirstName() + " " + c.getLastName();
        if (label.contains("currently based") || label.contains("current location"))
            return c.getTargetLocation();
        if (label.contains("managed teams") || label.contains("team size"))
            return "No";
        if (label.contains("certifications") || label.contains("training programs"))
            return "None";
        if (label.contains("primary reason") || label.contains("seeking a new"))
            return "Career growth and better opportunities";
        if (label.contains("primary technologies") || label.contains("technologies you've worked"))
            return "Java, Spring Boot, MySQL";
        if (label.contains("street"))      return "";
        if (label.contains("province"))    return "Tamil Nadu";
        if (label.contains("postal code")) return "600001";
        if (label.contains("country"))     return "India";
        if (label.contains("facebook"))    return "";
        if (label.contains("twitter"))     return "";
        if (label.contains("portfolio"))   return "";

        // ── MASTER INR NESTED CONDITION LOOP ──
        if (label.contains("inr")) {

            // Nested Check 1: Monthly Scenarios within INR context labels
            if (label.contains("month") || label.contains("monthly")) {
                if (label.contains("expected") || label.contains("excepted") || label.contains("expectations") || label.contains("ectc")) {
                    return c.getExpectedCtcMonthly() != null ? c.getExpectedCtcMonthly() : "0";
                }
                return c.getCurrentCtcMonthly() != null ? c.getCurrentCtcMonthly() : "0";
            }

            // Nested Check 2: Yearly (Annual) LPA Scenario conversion within INR context labels
            String rawLpa = "0";
            if (label.contains("expected") || label.contains("excepted") || label.contains("expectations") || label.contains("ectc")) {
                rawLpa = c.getExpectedCtc() != null ? c.getExpectedCtc().trim() : "0";
            } else {
                rawLpa = c.getCurrentCtc() != null ? c.getCurrentCtc().trim() : "0";
            }

            try {
                double lpaValue = Double.parseDouble(rawLpa);
                if (lpaValue > 0 && lpaValue < 100) {
                    return String.format("%.0f", lpaValue * 100000); // Ex: 2.5 -> 250000
                } else {
                    return String.format("%.0f", lpaValue);
                }
            } catch (Exception ignored) {}
            return rawLpa;
        }

        // ── 1. CURRENT MONTHLY SALARY FALLBACK (No INR present) ──
        if (label.contains("current") && (label.contains("month") || label.contains("monthly"))) {
            if (label.contains("ctc") || label.contains("salary") || label.contains("compensation")) {
                return c.getCurrentCtcMonthly() != null ? c.getCurrentCtcMonthly() : "0";
            }
        }

        // ── 2. EXPECTED MONTHLY SALARY FALLBACK (No INR present) ──
        if ((label.contains("expected") || label.contains("expectations") || label.contains("excepted") || label.contains("ectc")) && (label.contains("month") || label.contains("monthly"))) {
            if (label.contains("ctc") || label.contains("salary") || label.contains("compensation")) {
                return c.getExpectedCtcMonthly() != null ? c.getExpectedCtcMonthly() : "0";
            }
        }

        // ── 3. CURRENT YEARLY CTC / "CTC" BARE FALLBACK ──
        if (label.contains("current") || label.trim().equals("ctc")) {
            if (label.contains("ctc") || label.contains("salary") || label.contains("compensation") || label.contains("fixed")) {
                String val = c.getCurrentCtc() != null ? c.getCurrentCtc().trim() : "0";
                if (label.trim().equals("ctc")) {
                    try {
                        double lpaValue = Double.parseDouble(val);
                        if (lpaValue > 0 && lpaValue < 100) return String.format("%.0f", lpaValue * 100000);
                    } catch (Exception ignored) {}
                }
                return val; // Returns raw "2.5" or "3" directly if context mentions "LPA" or "Lakhs"
            }
        }

        // ── 4. EXPECTED YEARLY CTC / "ECTC" BARE FALLBACK ──
        if (label.contains("expected") || label.contains("excepted") || label.contains("expectations") || label.trim().equals("ectc") || label.contains("ectc")) {
            if (label.contains("ctc") || label.contains("salary") || label.contains("compensation")) {
                String val = c.getExpectedCtc() != null ? c.getExpectedCtc().trim() : "0";
                if (label.trim().equals("ctc") || label.replace(":", "").trim().equals("ectc")) {
                    try {
                        double lpaValue = Double.parseDouble(val);
                        if (lpaValue > 0 && lpaValue < 100) return String.format("%.0f", lpaValue * 100000);
                    } catch (Exception ignored) {}
                }
                return val;
            }
        }

        // ── 5. ABSOLUTE SALARY GENERIC BACKUP ──
        if (label.contains("ctc") || label.contains("salary") || label.contains("compensation")) {
            if (label.contains("month") || label.contains("monthly")) {
                return c.getCurrentCtcMonthly() != null ? c.getCurrentCtcMonthly() : "0";
            }
            return c.getExpectedCtc() != null ? c.getExpectedCtc().trim() : "0";
        }

        // ── 6. GENERAL PROFILE INFO FIELD BINDINGS ──
        if (label.contains("portfolio") || label.contains("url") || label.contains("linkedin") || label.contains("github")) {
            return c.getPortfolioUrl() != null ? c.getPortfolioUrl() : "";
        }
        if (label.contains("first name") || label.contains("given name")) {
            return c.getFirstName() != null ? c.getFirstName() : "";
        }
        if (label.contains("last name") || label.contains("surname")) {
            return c.getLastName() != null ? c.getLastName() : "";
        }
        if (label.contains("phone") || label.contains("mobile")) {
            return c.getPhoneNumber() != null ? c.getPhoneNumber() : "";
        }

        return c.getDefaultNumber() != null ? c.getDefaultNumber() : "0";
    }
}
