package com.example.linkedInbot.repository;

import com.example.linkedInbot.model.AppliedJob;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AppliedJobRepository extends JpaRepository<AppliedJob,Long> {

    boolean existsByJobUrl(String jobUrl);

    @Transactional
    @Modifying
    @Query(value = "TRUNCATE TABLE applied_job", nativeQuery = true)
    void truncateTable();
}
