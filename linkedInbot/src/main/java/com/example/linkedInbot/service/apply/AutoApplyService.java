package com.example.linkedInbot.service.apply;

import com.example.linkedInbot.model.BotConfig;
import com.example.linkedInbot.model.JobFetchResult;
import com.example.linkedInbot.service.BotConfig.BotConfigStore;
import com.example.linkedInbot.service.DatabaseStore.JobStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.* ;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class AutoApplyService {

    private final JobStoreService storeService;
    private final BotConfigStore configStore;
    private final JobFieldResolverService fieldResolver;

    // ── Convenience getter ────────────────────────────────────────────────────
    private BotConfig cfg() {
        BotConfig c = configStore.get();
        if (c == null) throw new IllegalStateException("BotConfig not initialised – call POST /api/config first");
        return c;
    }

    public boolean applyForJob(WebDriver driver, JobFetchResult job) throws InterruptedException {
        String jobUrl = job.getJobUrl();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(12));

        if (storeService.isAlreadyApplied(jobUrl)) {
            log.info("Already applied to {}, skipping", jobUrl);
            return false;
        }

        try {
            By applyBtnLoc = By.xpath("//button[contains(@class,'jobs-apply-button')]");
            WebElement applyBtn = wait.until(ExpectedConditions.elementToBeClickable(applyBtnLoc));

            String btnText = applyBtn.getText();
            if (!(btnText.contains("Easy Apply") || btnText.contains("Apply"))) return false;

            applyBtn.click();
            Thread.sleep(2000);

            handleSafetyReminderIfPresent(driver);

            if (driver.getWindowHandles().size() > 1) {
                log.info("External application detected for {}", jobUrl);
                return false;
            }

            log.info("Easy Apply modal opened for {}", jobUrl);
            int maxSteps = 10;
            boolean submitted = false;

            for (int i = 0; i < maxSteps; i++) {
                Thread.sleep(1500);

                // Safety net: this dialog has occasionally been seen reappear
                // mid-flow (e.g. after "Next" on certain job postings).
                handleSafetyReminderIfPresent(driver);

                handleAdditionalFields(driver);
                handleDateDropdowns(driver); // fills any Month/Year selects regardless of which dialog they're in

                if (isLocationFieldPresent(driver)) {
                    log.info("Location dropdown detected on step {}. Autofilling...", i + 1);
                    handleLocationField(driver, cfg().getTargetLocation());
                    Thread.sleep(1000);
                }

                List<WebElement> submitBtns = driver.findElements(
                        By.xpath("//button[contains(., 'Submit application')]"));
                if (!submitBtns.isEmpty() && submitBtns.get(0).isDisplayed()) {
                    submitBtns.get(0).click();
                    submitted = true;
                    log.info("Application submitted successfully for {}", jobUrl);
                    handleSuccessModal(driver);
                    break;
                }

                List<WebElement> nextBtns = driver.findElements(
                        By.xpath("//button[contains(., 'Next') or contains(., 'Review')]"));
                if (!nextBtns.isEmpty() && nextBtns.get(0).isDisplayed()) {
                    nextBtns.get(0).click();
                    Thread.sleep(2000);

                    // The safety reminder can also appear right after clicking
                    // "Next"/"Review" on certain postings — clear it before
                    // evaluating whether required fields are unfilled.
                    handleSafetyReminderIfPresent(driver);

                    if (hasUnfilledRequiredFields(driver)) {
                        log.info(">>>> ATTENTION: Manual fields required for {} <<<<", job.getJobTitle());
                        boolean resolved = waitForUserToFix(driver, 60);
                        if (!resolved) {
                            log.warn("Manual intervention timed out. Discarding.");
                            closeModal(driver);
                            break;
                        }
                    }
                    log.info("Moving to next step...");
                } else {
                    log.warn("No navigation button found on step {}. Stopping.", i + 1);
                    closeModal(driver);
                    break;
                }
            }

            String status = submitted ? "Applied Successfully" : "Modal opened but blocked by questions";
            storeService.saveRecord(jobUrl, job.getJobTitle(), job.getCompanyName(), status);
            return submitted;

        } catch (Exception e) {
            log.error("Auto-apply failed for {}: {}", jobUrl, e.getMessage());
            closeModal(driver);
            return false;
        }
    }

    // ── "Job search safety reminder" dialog handler ────────────────────────────
    /**
     * LinkedIn occasionally shows a "Job search safety reminder" dialog
     * (heading text: "Job search safety reminder", with "Research the
     * company" / "Report suspicious jobs" sections) right after clicking
     * Apply, or sometimes mid-flow after Next/Review.
     *
     * If present, click its "Continue applying" button so the normal
     * Easy Apply flow proceeds. If that button can't be found, fall back
     * to dismissing via the dialog's close (X) button. Returns true if
     * the dialog was found and handled, false if it wasn't present.
     */
    private boolean handleSafetyReminderIfPresent(WebDriver driver) {
        try {
            // Look for the dialog by its distinctive heading text first —
            // most robust against LinkedIn's randomized CSS class names.
            List<WebElement> headings = driver.findElements(
                    By.xpath("//h2[contains(normalize-space(.), 'Job search safety reminder')] " +
                            "| //div[contains(normalize-space(.), 'Job search safety reminder')]"));

            boolean dialogPresent = headings.stream().anyMatch(WebElement::isDisplayed);

            // Fallback: even without the heading text, "Continue applying"
            // is a strong, specific signal for this exact dialog.
            List<WebElement> continueBtns = driver.findElements(
                    By.xpath("//button[.//span[contains(normalize-space(.), 'Continue applying')] " +
                            "or contains(normalize-space(.), 'Continue applying')]"));
            continueBtns.removeIf(b -> !b.isDisplayed());

            if (!dialogPresent && continueBtns.isEmpty()) {
                return false; // dialog not present, nothing to do
            }

            log.info("'Job search safety reminder' dialog detected — clicking 'Continue applying'.");

            if (!continueBtns.isEmpty()) {
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", continueBtns.get(0));
                Thread.sleep(1000);
                return true;
            }

            // Last resort: close the dialog via its X button so it doesn't
            // block subsequent element lookups.
            List<WebElement> closeBtns = driver.findElements(
                    By.xpath("//button[contains(@aria-label,'Dismiss') or contains(@aria-label,'Close')]"));
            closeBtns.removeIf(b -> !b.isDisplayed());
            if (!closeBtns.isEmpty()) {
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", closeBtns.get(0));
                log.info("'Job search safety reminder' — 'Continue applying' button not found, closed via X instead.");
                Thread.sleep(1000);
                return true;
            }

            return false;
        } catch (Exception e) {
            log.warn("handleSafetyReminderIfPresent error: {}", e.getMessage());
            return false;
        }
    }

    // ── Date dropdown handler ─────────────────────────────────────────────────
    /**
     * Fills From/To Month+Year dropdowns on LinkedIn's date-range fields.
     *
     * DOM structure (confirmed from inspect):
     *
     *   <fieldset data-test-date-dropdown="start">        ← From
     *     <select data-test-month-select name="month">
     *       <option value="Month">Month</option>
     *       <option value="1">January</option>
     *       ...
     *       <option value="12">December</option>
     *     </select>
     *     <select data-test-year-select name="year">
     *       <option value="Year">Year</option>
     *       <option value="2026">2026</option>
     *       ...
     *       <option value="2020">2020</option>
     *     </select>
     *   </fieldset>
     *
     *   <fieldset data-test-date-dropdown="end">          ← To
     *     (same structure)
     *   </fieldset>
     *
     * Key fix: month options use NUMERIC values ("1"–"12"), NOT text ("January").
     * Placeholder options use value="Month" and value="Year" (not empty string).
     * We scope each fill to its own fieldset so From/To never get mixed up.
     *
     * Values are read from BotConfig so they're user-configurable:
     *   fromMonth / fromYear  →  From date
     *   toMonth   / toYear    →  To date
     *
     * If BotConfig fields are null/blank, sensible hardcoded defaults are used.
     */
    private void handleDateDropdowns(WebDriver driver) {
        try {
            BotConfig c = cfg();

            // Read from BotConfig — fall back to hardcoded defaults if not set
            String fromMonth = (c.getFromMonth() != null && !c.getFromMonth().isBlank())
                    ? c.getFromMonth().trim() : "6";    // June
            String fromYear  = (c.getFromYear()  != null && !c.getFromYear().isBlank())
                    ? c.getFromYear().trim()  : "2020";
            String toMonth   = (c.getToMonth()   != null && !c.getToMonth().isBlank())
                    ? c.getToMonth().trim()   : "3";    // March
            String toYear    = (c.getToYear()    != null && !c.getToYear().isBlank())
                    ? c.getToYear().trim()    : "2021";

            fillDateRange(driver, "start", fromMonth, fromYear);
            fillDateRange(driver, "end",   toMonth,   toYear);

        } catch (Exception e) {
            log.warn("[handleDateDropdowns] Unexpected error: {}", e.getMessage());
        }
    }

    /**
     * Fills the Month and Year selects inside a single
     * fieldset[data-test-date-dropdown="start|end"].
     *
     * @param fieldsetType  "start" (From) or "end" (To)
     * @param monthValue    Numeric string matching the <option value>, e.g. "6" for June
     * @param yearValue     Four-digit year string, e.g. "2020"
     */
    private void fillDateRange(WebDriver driver, String fieldsetType,
                               String monthValue, String yearValue) {
        try {
            // Scope to the correct fieldset so From and To are never mixed up
            WebElement fieldset = driver.findElement(
                    By.cssSelector("fieldset[data-test-date-dropdown='" + fieldsetType + "']"));

            // ── Month ─────────────────────────────────────────────────────────
            try {
                WebElement monthEl = fieldset.findElement(
                        By.cssSelector("select[data-test-month-select]"));
                Select monthSel = new Select(monthEl);

                // Placeholder option has value="Month" (not empty string)
                String currentValue = monthSel.getFirstSelectedOption().getAttribute("value");
                if ("Month".equals(currentValue) || currentValue == null || currentValue.isBlank()) {
                    monthSel.selectByValue(monthValue); // e.g. "6" → June
                    log.info("[handleDateDropdowns] [{}] Month set → value='{}'", fieldsetType, monthValue);
                } else {
                    log.debug("[handleDateDropdowns] [{}] Month already set (value='{}'), skipping.",
                            fieldsetType, currentValue);
                }
            } catch (NoSuchElementException e) {
                log.debug("[handleDateDropdowns] [{}] Month select not found.", fieldsetType);
            } catch (Exception e) {
                log.warn("[handleDateDropdowns] [{}] Month error: {}", fieldsetType, e.getMessage());
            }

            // ── Year ──────────────────────────────────────────────────────────
            try {
                WebElement yearEl = fieldset.findElement(
                        By.cssSelector("select[data-test-year-select]"));
                Select yearSel = new Select(yearEl);

                // Placeholder option has value="Year" (not empty string)
                String currentValue = yearSel.getFirstSelectedOption().getAttribute("value");
                if ("Year".equals(currentValue) || currentValue == null || currentValue.isBlank()) {
                    yearSel.selectByValue(yearValue); // e.g. "2020"
                    log.info("[handleDateDropdowns] [{}] Year set → value='{}'", fieldsetType, yearValue);
                } else {
                    log.debug("[handleDateDropdowns] [{}] Year already set (value='{}'), skipping.",
                            fieldsetType, currentValue);
                }
            } catch (NoSuchElementException e) {
                log.debug("[handleDateDropdowns] [{}] Year select not found.", fieldsetType);
            } catch (Exception e) {
                log.warn("[handleDateDropdowns] [{}] Year error: {}", fieldsetType, e.getMessage());
            }

        } catch (NoSuchElementException e) {
            // This step simply doesn't have a date-range field — perfectly normal
            log.debug("[handleDateDropdowns] Fieldset '{}' not present on this step.", fieldsetType);
        } catch (Exception e) {
            log.warn("[handleDateDropdowns] [{}] Unexpected error: {}", fieldsetType, e.getMessage());
        }
    }

    // ── Dialog-box / form-field handler ──────────────────────────────────────
    private void handleAdditionalFields(WebDriver driver) {
        BotConfig c = cfg();

        handlePhotoUploadIfPresent(driver);

        int groupCount = driver.findElements(
                By.cssSelector(".fb-dash-form-element, .jobs-easy-apply-form-section__grouping")).size();

        for (int idx = 0; idx < groupCount; idx++) {
            try {
                // Re-fetch the whole list fresh on each iteration
                List<WebElement> freshGroups = driver.findElements(
                        By.cssSelector(".fb-dash-form-element, .jobs-easy-apply-form-section__grouping"));
                if (idx >= freshGroups.size()) break;
                WebElement group = freshGroups.get(idx);
                String label = group.getText().toLowerCase();

                // 1. Safety filter – never overwrite identity / contact fields
                //    unless the user explicitly provided them via the config form
                if (label.contains("first name")) {
                    if (c.getFirstName() != null && !c.getFirstName().isBlank()) {
                        fillTextInput(group, c.getFirstName());
                    }
                    continue;
                }
                if (label.contains("last name")) {
                    if (c.getLastName() != null && !c.getLastName().isBlank()) {
                        fillTextInput(group, c.getLastName());
                    }
                    continue;
                }
                if (label.contains("phone") || label.contains("mobile")) {
                    if (c.getPhoneNumber() != null && !c.getPhoneNumber().isBlank()) {
                        fillTextInput(group, c.getPhoneNumber());
                    }
                    continue;
                }
                if (label.contains("email") || label.contains("middle name")) {
                    continue; // never overwrite
                }

                //2. HTML <select> tags
                List<WebElement> selects = group.findElements(By.tagName("select"));
                if (!selects.isEmpty()) {

                    Select sel = new Select(selects.get(0));
                    String firstOptionText = sel.getFirstSelectedOption().getText();

                    // Check if the dropdown is currently unselected
                    if (firstOptionText.contains("Select an option") || firstOptionText.trim().startsWith("0")) {
                        boolean picked = false;

                        // A. Targeted Experience Year/Month Logic
                        if (label.contains("experience") || label.contains("work") || label.contains("year") || label.contains("month")) {
                            String targetValue = "0"; // Universal baseline default

                            if (label.contains("month")) {
                                targetValue = (c.getExperienceMonths() != null && !c.getExperienceMonths().isBlank())
                                        ? c.getExperienceMonths().trim() : "0";
                            } else {
                                targetValue = (c.getExperienceYears() != null && !c.getExperienceYears().isBlank())
                                        ? c.getExperienceYears().trim() : "0";
                            }

                            log.info("Dynamic Experience rule triggered. Scanning dropdown for: '{}'", targetValue);

                            // Loop through the actual options provided by LinkedIn's HTML structure
                            for (WebElement option : sel.getOptions()) {
                                String optionText = option.getText().trim(); // e.g., "2 years"

                                if (optionText.startsWith(targetValue)) {
                                    sel.selectByVisibleText(optionText);
                                    log.info("Successfully matched and selected option: '{}'", optionText);
                                    picked = true;
                                    break;
                                }
                            }
                        }

                        // B. Generic Screening Questionnaire Logic (Fallback matching for Yes/No questions)
                        if (!picked) {
                            for (int i = 0; i < sel.getOptions().size(); i++) {
                                String genericOptionText = sel.getOptions().get(i).getText();
                                if (genericOptionText.equalsIgnoreCase("Yes")) {
                                    sel.selectByIndex(i);
                                    log.info("Generic matching fallback selected: 'Yes'");
                                    picked = true;
                                    break;
                                }
                            }
                        }

                        // C. Hard Fallback: If absolutely nothing matched, select index 1 to avoid leaving it empty
                        if (!picked && sel.getOptions().size() > 1) {
                            sel.selectByIndex(1);
                            log.info("Absolute fallback rule executed. Choice index 1 selected.");
                        }
                    }
                    continue; // Smoothly skip down to the next form group step
                }

                // 3. Radio buttons
                List<WebElement> radios = group.findElements(By.xpath(".//label"));
                if (!radios.isEmpty()) {
                    WebElement optionToClick = null;

                    // ── GENDER: match exactly what the user configured ─────────────
                    if (label.contains("gender") || label.contains("pronouns")
                            || label.contains("sex")) {
                        String configuredGender = c.getGender();
                        if (configuredGender != null && !configuredGender.isBlank()) {
                            for (WebElement radio : radios) {
                                String radioText = "";
                                try { radioText = radio.getText().toLowerCase(); }
                                catch (StaleElementReferenceException e) { continue; }

                                if (radioText.equalsIgnoreCase(configuredGender.trim())
                                        || radioText.contains(configuredGender.toLowerCase().trim())) {
                                    optionToClick = radio;
                                    log.info("Gender matched: '{}'", radio.getText());
                                    break;
                                }
                            }
                        }
                        // If no match found (e.g. "Prefer not to say" not available), skip entirely
                        if (optionToClick == null) {
                            log.info("Gender field — no matching option for '{}', skipping.", c.getGender());
                            continue;
                        }

                        // ── YES/NO and other radio questions (existing logic) ───────────
                    } else {
                        for (WebElement radio : radios) {
                            String radioText = "";
                            try { radioText = radio.getText().toLowerCase(); }
                            catch (StaleElementReferenceException e) { continue; }

                            if (radioText.equals("yes") || radioText.equals("comfortable")) {
                                optionToClick = radio;
                                break;
                            }
                        }
                        if (optionToClick == null && label.contains("english")) {
                            for (WebElement radio : radios) {
                                String rTxt = "";
                                try { rTxt = radio.getText().toLowerCase(); }
                                catch (StaleElementReferenceException e) { continue; }
                                if (rTxt.contains("fluent") || rTxt.contains("advanced")
                                        || rTxt.contains("native")) {
                                    optionToClick = radio;
                                    break;
                                }
                            }
                        }
                        if (optionToClick == null && (label.contains("experience")
                                || label.contains("years") || label.contains("scala"))) {
                            for (WebElement radio : radios) {
                                String rTxt = "";
                                try { rTxt = radio.getText().toLowerCase(); }
                                catch (StaleElementReferenceException e) { continue; }
                                if (rTxt.contains("less than") || rTxt.contains("0-")
                                        || rTxt.contains("limited")) {
                                    optionToClick = radio;
                                    break;
                                }
                            }
                        }
                        if (optionToClick == null && label.contains("start date")) {
                            for (WebElement radio : radios) {
                                String rTxt = "";
                                try { rTxt = radio.getText().toLowerCase(); }
                                catch (StaleElementReferenceException e) { continue; }
                                if (rTxt.contains("immediately")
                                        || rTxt.contains("within 30 days")) {
                                    optionToClick = radio;
                                    break;
                                }
                            }
                        }
                        // Final fallback — first option
                        if (optionToClick == null) optionToClick = radios.get(0);
                    }

                    // ── Click whichever option was selected ─────────────────────────
                    if (optionToClick != null && optionToClick.isDisplayed()) {
                        ((JavascriptExecutor) driver).executeScript(
                                "arguments[0].click();", optionToClick);
                        log.info("Selected radio choice: {}", optionToClick.getText());
                    }
                }

                // 4. Text / number inputs (dialog-box fields handled here)
                List<WebElement> inputs = group.findElements(
                        By.cssSelector("input[type='text'], input[type='number']"));
                if (!inputs.isEmpty()) {

                    WebElement input = inputs.get(0);
                    String currentVal = input.getAttribute("value");

                    if (label.contains("location") || label.contains("current location")) {
                        // First check if it's a plain text input
                        List<WebElement> locationInputs = group.findElements(
                                By.cssSelector("input[type='text'], input:not([type])"));

                        boolean isCombobox = !group.findElements(
                                By.cssSelector("input[role='combobox'], input[aria-autocomplete='list']")).isEmpty();

                        if (isCombobox) {
                            handleLocationField(driver, cfg().getTargetLocation());
                        } else if (!locationInputs.isEmpty()) {
                            WebElement locInput = locationInputs.get(0);
                            locInput.sendKeys(Keys.CONTROL + "a", Keys.BACK_SPACE);
                            locInput.sendKeys(cfg().getTargetLocation());
                            log.info("Filled plain text location field with '{}'", cfg().getTargetLocation());
                        }
                        continue;
                    }

                    if (currentVal == null || currentVal.isEmpty()
                            || label.contains("ctc") || label.contains("salary")
                            || label.contains("notice") || label.contains("experience")
                            || label.contains("location") || label.contains("title")
                            || label.contains("degree") || label.contains("field of study")
                            || label.contains("major") || label.contains("school")
                            || label.contains("street") || label.contains("province")
                            || label.contains("postal") || label.contains("country")
                            || label.contains("facebook") || label.contains("twitter")
                            || label.contains("portfolio") || label.contains("github"))  {

                        input.sendKeys(Keys.CONTROL + "a", Keys.BACK_SPACE);

                        String valueToFill = fieldResolver.resolveFieldValue(label, c);
                        input.sendKeys(valueToFill);
                        log.info("Filled field '{}' with '{}'", label.trim(), valueToFill);
                    }
                }

                // 5. Textarea fields — smart fill required ones, skip cover letters
                List<WebElement> textareas = group.findElements(By.tagName("textarea"));
                if (!textareas.isEmpty()) {
                    WebElement ta = textareas.get(0);
                    String currentVal = ta.getAttribute("value");

                    boolean isCoverLetter = label.contains("cover letter")
                            || label.contains("additional information")
                            || label.contains("tell us about yourself")
                            || label.contains("why do you want")
                            || label.contains("why are you interested");

                    if (isCoverLetter) {
                        log.info("Textarea '{}' — skipping (cover letter type)", label.trim());
                        continue;
                    }

                    // Only fill if empty or has error
                    if (currentVal == null || currentVal.isBlank()) {
                        String answer = resolveTextareaAnswer(label, c);
                        ta.sendKeys(answer);
                        log.info("Textarea filled '{}' with '{}'", label.trim(), answer);
                    }
                }

            } catch (Exception e) {
                log.warn("Field handling error: {}", e.getMessage());
            }
        }
    }

    private void fillTextInput(WebElement group, String value) {
        try {
            List<WebElement> inputs = group.findElements(
                    By.cssSelector("input[type='text'], input[type='number'], input[type='tel']"));
            if (!inputs.isEmpty()) {
                WebElement input = inputs.get(0);
                input.sendKeys(Keys.CONTROL + "a", Keys.BACK_SPACE);
                input.sendKeys(value);
            }
        } catch (Exception e) {
            log.warn("fillTextInput failed: {}", e.getMessage());
        }
    }

    /**
     * Selects the given visible-text option in a <select> only if the
     * dropdown is currently unset (showing a placeholder like "Month"
     * or "Year"). Leaves user-prefilled values untouched.
     */
    private void selectIfUnset(Select select, String visibleText) {
        String current = select.getFirstSelectedOption().getText().trim();
        if (current.isEmpty()
                || current.equalsIgnoreCase("Month")
                || current.equalsIgnoreCase("Year")) {
            try {
                select.selectByVisibleText(visibleText);
            } catch (NoSuchElementException e) {
                log.warn("selectIfUnset: option '{}' not found in dropdown.", visibleText);
            }
        }
    }

    private boolean isLocationFieldPresent(WebDriver driver) {
        List<WebElement> fields = driver.findElements(
                By.cssSelector(".jobs-easy-apply-content input[role='combobox']"));
        return !fields.isEmpty() && fields.get(0).isDisplayed();
    }

    public void handleLocationField(WebDriver driver, String cityName) {
        try {
            WebElement input = driver.findElement(By.cssSelector(
                    ".jobs-easy-apply-modal input[role='combobox'], .jobs-easy-apply-modal input[aria-autocomplete='list']"));
            input.sendKeys(Keys.CONTROL + "a", Keys.BACK_SPACE);
            input.sendKeys(cityName);
            Thread.sleep(2000);
            WebElement firstOption = driver.findElement(By.cssSelector(
                    ".typeahead-suggestion:first-child, .jobs-vicinity-typeahead__list-item:first-child, div[role='option']:first-child"));
            firstOption.click();
        } catch (Exception e) {
            try {
                driver.findElement(By.cssSelector(
                        ".jobs-easy-apply-content input[role='combobox']")).sendKeys(Keys.ENTER);
            } catch (Exception ignored) {}
        }
    }

    private boolean waitForUserToFix(WebDriver driver, int timeoutSeconds) throws InterruptedException {
        int timePassed = 0;
        while (timePassed < timeoutSeconds) {
            Thread.sleep(2000);
            timePassed += 2;
            if (!hasUnfilledRequiredFields(driver)) return true;
            if (driver.findElements(By.cssSelector("div.jobs-easy-apply-modal")).isEmpty()) return true;
        }
        return false;
    }

    private void handleSuccessModal(WebDriver driver) {
        try {
            Thread.sleep(2000);

            // Clear "Save this application?" if it's blocking the Done button
            List<WebElement> saveDialogs = driver.findElements(
                    By.cssSelector("div.artdeco-modal[role='dialog']"));
            for (WebElement dlg : saveDialogs) {
                if (!dlg.isDisplayed()) continue;
                List<WebElement> saveBtns = dlg.findElements(
                        By.xpath(".//button[.//span[normalize-space(text())='Save']]"));
                if (!saveBtns.isEmpty()) {
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", saveBtns.get(0));
                    log.info("handleSuccessModal: cleared Save dialog before clicking Done.");
                    Thread.sleep(1000);
                    break;
                }
            }

            // Now click Done with a proper wait instead of findElement
            new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(ExpectedConditions.elementToBeClickable(
                            By.xpath("//button[normalize-space(.)='Done' or contains(@aria-label,'Dismiss')]")))
                    .click();

        } catch (Exception e) {
            new Actions(driver).sendKeys(Keys.ESCAPE).perform();
        }
    }

    private void closeModal(WebDriver driver) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
        try {
            log.info("Attempting to close the application modal...");

            By closeLoc = By.xpath("//button[contains(@aria-label,'Dismiss') or contains(@aria-label,'Close')]");
            List<WebElement> closeBtns = driver.findElements(closeLoc);

            if (!closeBtns.isEmpty() && closeBtns.get(0).isDisplayed()) {
                closeBtns.get(0).click();
                Thread.sleep(1500);

                // ── Handle "Save this application?" artdeco modal ──────────────
                // FIXED: LinkedIn uses <div class="artdeco-modal">, NOT a <dialog> tag
                List<WebElement> saveDialogs = driver.findElements(
                        By.cssSelector("div.artdeco-modal[role='dialog']"));

                boolean handled = false;
                for (WebElement dlg : saveDialogs) {
                    if (!dlg.isDisplayed()) continue;
                    try {
                        // Stable selector — immune to ember ID regeneration
                        List<WebElement> discardBtns = dlg.findElements(
                                By.cssSelector("button[data-control-name='discard_application_confirm_btn']"));
                        if (!discardBtns.isEmpty()) {
                            ((JavascriptExecutor) driver).executeScript(
                                    "arguments[0].click();", discardBtns.get(0));
                            log.info("'Save this application?' dialog — clicked Discard.");
                            handled = true;
                            Thread.sleep(1000);
                            break;
                        }
                    } catch (Exception ignored) {}
                }

                // ── JS fallback if div scan didn't work ────────────────────────
                if (!handled) {
                    Boolean jsClicked = (Boolean) ((JavascriptExecutor) driver).executeScript(
                            "var btns = document.querySelectorAll('div.artdeco-modal button');" +
                                    "for (var i = 0; i < btns.length; i++) {" +
                                    "  if (btns[i].getAttribute('data-control-name') === 'discard_application_confirm_btn'" +
                                    "      || btns[i].innerText.trim() === 'Discard') {" +
                                    "    btns[i].click(); return true;" +
                                    "  }" +
                                    "} return false;"
                    );
                    if (Boolean.TRUE.equals(jsClicked)) {
                        log.info("'Save this application?' — clicked Discard via JS fallback.");
                        handled = true;
                        Thread.sleep(1000);
                    }
                }

                // ── Legacy XPath fallback (last resort) ───────────────────────
                if (!handled) {
                    List<WebElement> discardBtns = driver.findElements(By.xpath(
                            "//button[contains(.,'Discard')] | //span[contains(text(),'Discard')]/parent::button"));
                    if (!discardBtns.isEmpty()) {
                        log.info("Legacy Discard fallback triggered. Clicking Discard.");
                        ((JavascriptExecutor) driver).executeScript(
                                "arguments[0].click();", discardBtns.get(0));
                        Thread.sleep(1000);
                    }
                }

                wait.until(ExpectedConditions.invisibilityOfElementLocated(
                        By.className("artdeco-modal-overlay")));
                log.info("Modal cleared successfully.");
            }

        } catch (Exception e) {
            log.warn("Modal close failed, using hard ESC escape...");
            new Actions(driver).sendKeys(Keys.ESCAPE).sendKeys(Keys.ESCAPE).perform();
        }
    }

    private boolean handlePhotoUploadIfPresent(WebDriver driver) {
        try {
            String photoPath = cfg().getPhotoPath();
            if (photoPath == null || photoPath.isBlank()) {
                // Safe exit: Quietly skips the entire sequence if user didn't type a path
                return false;
            }

            // ── 1. Check if any photo upload container is present ────────────
            List<WebElement> uploadContainers = driver.findElements(
                    By.cssSelector(".js-jobs-document-upload__container"));

            if (uploadContainers.isEmpty()) {
                return false; // no photo field on this step
            }

            WebElement photoInput = null;
            for (WebElement container : uploadContainers) {
                List<WebElement> fileInputs = container.findElements(
                        By.cssSelector("input[type='file']"));
                for (WebElement input : fileInputs) {
                    String accept = input.getAttribute("accept");
                    if (accept != null && accept.contains("image")) {
                        photoInput = input;
                        break;
                    }
                }
                if (photoInput != null) break;
            }

            if (photoInput == null) {
                log.info("Upload container found but no image-type input detected — skipping.");
                return false;
            }

            // ── 3. Validate the configured photo path ────────────────────────
            if (photoPath == null || photoPath.isBlank()) {
                log.warn("Photo upload field detected but 'photoPath' is not set in BotConfig. Skipping.");
                return false;
            }

            java.io.File photoFile = new java.io.File(photoPath);
            if (!photoFile.exists() || !photoFile.isFile()) {
                log.warn("Photo file not found at path: '{}'. Skipping upload.", photoPath);
                return false;
            }

            // Validate extension matches what LinkedIn accepts
            String fileName = photoFile.getName().toLowerCase();
            if (!fileName.endsWith(".jpg") && !fileName.endsWith(".jpeg")
                    && !fileName.endsWith(".gif") && !fileName.endsWith(".png")) {
                log.warn("Photo file '{}' is not a supported format (JPG/JPEG/GIF/PNG). Skipping.", fileName);
                return false;
            }

            // Validate file size ≤ 2 MB
            long fileSizeBytes = photoFile.length();
            if (fileSizeBytes > 2 * 1024 * 1024) {
                log.warn("Photo file '{}' exceeds 2 MB limit ({} KB). Skipping.",
                        fileName, fileSizeBytes / 1024);
                return false;
            }

            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].style.display = 'block';" +
                            "arguments[0].style.visibility = 'visible';" +
                            "arguments[0].style.opacity = '1';",
                    photoInput);

            String absolutePath = photoFile.getAbsolutePath();
            photoInput.sendKeys(absolutePath);
            log.info("Photo uploaded successfully from path: '{}'", absolutePath);

            Thread.sleep(2000);

            try {
                // LinkedIn typically shows the filename or a preview element after upload
                List<WebElement> confirmations = driver.findElements(By.cssSelector(
                        ".jobs-document-upload__filename, " +
                                ".jobs-document-upload__preview, " +
                                ".js-jobs-document-upload__filename, " +
                                "[data-test-document-upload-status]"));
                if (!confirmations.isEmpty()) {
                    log.info("Photo upload confirmed — preview/filename element visible.");
                } else {
                    log.info("Photo sendKeys() sent — no preview element found, but upload may still have succeeded.");
                }
            } catch (Exception ignored) {}

            return true;

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.warn("handlePhotoUploadIfPresent error: {}", e.getMessage());
            return false;
        }
    }

    private String resolveTextareaAnswer(String label, BotConfig c) {
        // Experience with specific technologies
        if (label.contains("angular"))
            return "I have worked with Angular in professional projects. "
                    + "My experience includes Angular 14+ with component-based architecture, "
                    + "reactive forms, RxJS, and REST API integration.";

        if (label.contains("c++") || label.contains("c plus"))
            return "I have basic knowledge of C++ and have worked on small academic projects. "
                    + "My primary expertise is in Java and Spring Boot for backend development.";

        if (label.contains("python"))
            return "I have working knowledge of Python, primarily for scripting and automation tasks.";

        if (label.contains("react") || label.contains("frontend"))
            return "I have experience with React.js including hooks, state management, "
                    + "and REST API integration in full-stack projects.";

        if (label.contains("sql") || label.contains("database"))
            return "I have hands-on experience with MySQL and basic PostgreSQL, "
                    + "including writing complex queries, joins, and stored procedures.";

        if (label.contains("front-end") || label.contains("frontend")) {
            return "HTML, CSS, JavaScript, React.js - 4/5; Tailwind CSS - 3/5; "
                    + "Bootstrap - 4/5; TypeScript - 3/5.";
        }
        if (label.contains("back-end") || label.contains("backend")) {
            return "Java - 4/5; Spring Boot - 4/5; REST APIs - 4/5; "
                    + "MySQL - 4/5; Node.js - 3/5.";
        }

        // ── Top skills + years of experience in each (free-text version) ──
        if (label.contains("top three skills") || label.contains("top skills")) {
            String years = c.getExperienceYears() != null && !c.getExperienceYears().isBlank()
                    ? c.getExperienceYears() : "2";
            return "Java - " + years + " years; Spring Boot - " + years + " years; "
                    + "MySQL - " + years + " years.";
        }

        // Portfolio / links
        if (label.contains("portfolio") || label.contains("url") || label.contains("linkedin") || label.contains("github"))
            return c.getPortfolioUrl() != null && !c.getPortfolioUrl().isBlank()
                    ? c.getPortfolioUrl() : "";

        // Notice period / availability
        if (label.contains("notice") || label.contains("availability") || label.contains("join"))
            return c.getNoticePeriod() != null ? c.getNoticePeriod() + " days" : "15 days";

        // Salary / CTC
        if (label.contains("ctc") || label.contains("salary") || label.contains("compensation"))
            return "Current: " + (c.getCurrentCtc() != null ? c.getCurrentCtc() : "4 LPA")
                    + ", Expected: " + (c.getExpectedCtc() != null ? c.getExpectedCtc() : "7 LPA");

        // Generic experience question
        if (label.contains("experience") || label.contains("worked with")
                || label.contains("professional") || label.contains("background"))
            return "I have " + (c.getExperienceYears() != null ? c.getExperienceYears() : "2")
                    + " years of professional software development experience, "
                    + "primarily in Java, Spring Boot, REST APIs, and MySQL.";

        // Generic describe / explain questions
        if (label.contains("describe") || label.contains("explain")
                || label.contains("tell") || label.contains("list"))
            return "I have relevant experience in this area through professional projects "
                    + "and continuous learning. I am confident in quickly adapting to new requirements.";

        // Hard fallback for any remaining required textarea
        return "I have relevant experience and am confident in meeting the requirements for this role.";
    }

    private boolean hasUnfilledRequiredFields(WebDriver driver) {
        return !driver.findElements(By.cssSelector(
                ".artdeco-inline-feedback--error, .fb-dash-form-element__error-messaging, [aria-invalid='true']"
        )).isEmpty();
    }
}