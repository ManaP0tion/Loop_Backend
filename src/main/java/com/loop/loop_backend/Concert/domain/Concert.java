package com.loop.loop_backend.Concert.domain;

import com.loop.loop_backend.Artist.domain.Artist;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "concerts")
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

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "poster_url", length = 500)
    private String posterUrl;

    @Column(name = "venue", length = 255)
    private String venue;

    @Column(name = "performed_at")
    private LocalDateTime performedAt;

    public void update(Artist artist, String title, String posterUrl, String venue, LocalDateTime performedAt) {
        this.artist = artist;
        this.title = title;
        this.posterUrl = posterUrl;
        this.venue = venue;
        this.performedAt = performedAt;
    }
}
