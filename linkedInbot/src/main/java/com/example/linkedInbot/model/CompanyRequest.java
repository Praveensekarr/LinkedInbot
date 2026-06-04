package com.example.linkedInbot.model;

import lombok.Data;

import java.util.List;

@Data
public class CompanyRequest {

    private String email;
    private String password;

    private List<String> CompanyLinks;
}
