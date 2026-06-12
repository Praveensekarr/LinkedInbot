package com.example.linkedInbot.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppliedJob {

    private long id;
    private String jobUrl;
    private String jobname;
    private String companyName;
    private LocalDateTime appliedTime;
    private String status;
}