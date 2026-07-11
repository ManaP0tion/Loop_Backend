package com.loop.loop_backend.Report.repository;

import com.loop.loop_backend.Report.domain.Report;
import com.loop.loop_backend.Report.domain.ReportImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportImageRepository extends JpaRepository<ReportImage, Long> {

    List<ReportImage> findAllByReport(Report report);
}
