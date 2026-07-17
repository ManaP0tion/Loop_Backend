package com.loop.loop_backend.Concert.domain;

import com.loop.loop_backend.Artist.domain.Artist;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(
    name = "concerts",
    uniqueConstraints = @UniqueConstraint(columnNames = {"kopis_id", "artist_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Concert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_id")
    private Artist artist;

    @Column(name = "kopis_id", length = 20)
    private String kopisId;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "poster_url", length = 500)
    private String posterUrl;

    @Column(name = "venue", length = 255)
    private String venue;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 30, nullable = false)
    private ConcertCategory category;

    public void update(Artist artist, String title, String posterUrl, String venue,
                       LocalDate startDate, LocalDate endDate, ConcertCategory category) {
        this.artist = artist;
        this.title = title;
        this.posterUrl = posterUrl;
        this.venue = venue;
        this.startDate = startDate;
        this.endDate = endDate;
        this.category = category;
    }

    public void updateFromKopis(String title, String posterUrl, String venue,
                                LocalDate startDate, LocalDate endDate, ConcertCategory category) {
        this.title = title;
        this.posterUrl = posterUrl;
        this.venue = venue;
        this.startDate = startDate;
        this.endDate = endDate;
        this.category = category;
    }

    public void updatePosterUrl(String posterUrl) {
        this.posterUrl = posterUrl;
    }
}
