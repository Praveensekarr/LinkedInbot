package com.example.linkedInbot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JobFetchResult {

    private String jobTitle;
    private String companyName;
    private String jobUrl;
    private boolean easyApply;
}