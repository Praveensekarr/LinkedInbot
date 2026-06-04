package com.example.linkedInbot.model;

import lombok.Data;

import java.util.List;

@Data
public class JobRequest {

    private String email;
    private String password;

    private List<String> jobUrls;
    private List<String> jobIds;
}
