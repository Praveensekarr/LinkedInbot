package com.example.linkedInbot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AppliedJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_url")
    private String jobUrl;
    private String jobname;
    private String companyName;
    private LocalDateTime appliedTime;

    @Column(name = "status", length = 500)
    private String status;
}
