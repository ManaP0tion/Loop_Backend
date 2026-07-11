package com.loop.loop_backend.Report.domain;

import com.loop.loop_backend.User.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Entity
@Table(name = "report_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Builder
    private ReportImage(Report report, String imageUrl) {
        this.report = report;
        this.imageUrl = imageUrl;
    }
}
