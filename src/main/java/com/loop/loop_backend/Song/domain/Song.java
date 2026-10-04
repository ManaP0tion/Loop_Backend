package com.loop.loop_backend.Song.domain;

import com.loop.loop_backend.Artist.domain.Artist;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Table(name = "song", uniqueConstraints =
    @UniqueConstraint(columnNames = {"artist_id", "track_id"}))
@SQLDelete(sql = "UPDATE song SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Song {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artist_id")
    private Artist artist;

    // iTunes trackId. 수동 추가 곡은 null
    private Long trackId;

    @Column(nullable = false)
    private String titleOriginal;
    private String titleRomanized;
    private String titleKo;

    @Column(length = 500)
    private String albumArtUrl;

    @Column(nullable = false)
    private Integer sortOrder;

    private LocalDateTime deletedAt;

    @Builder
    private Song(Artist artist, Long trackId, String titleOriginal,
                 String titleRomanized, String titleKo,
                 String albumArtUrl, Integer sortOrder) {
        this.artist = artist;
        this.trackId = trackId;
        this.titleOriginal = titleOriginal;
        this.titleRomanized = titleRomanized;
        this.titleKo = titleKo;
        this.albumArtUrl = albumArtUrl;
        this.sortOrder = sortOrder;
    }

    public void update(String titleOriginal, String titleRomanized,
                       String titleKo, String albumArtUrl) {
        this.titleOriginal = titleOriginal;
        this.titleRomanized = titleRomanized;
        this.titleKo = titleKo;
        this.albumArtUrl = albumArtUrl;
    }

    // iTunes 재불러오기: titleKo는 관리자가 입력한 값이라 덮어쓰지 않는다. 로마자는 비어 있을 때만 채운다 (수동 보정값 보존)
    public void updateFromItunes(String titleOriginal, String titleRomanized,
                                 String albumArtUrl, Integer sortOrder) {
        this.titleOriginal = titleOriginal;
        if (this.titleRomanized == null) this.titleRomanized = titleRomanized;
        this.albumArtUrl = albumArtUrl;
        this.sortOrder = sortOrder;
    }

    // CSV 업로드: 빈 값은 기존 값 유지
    public void updateFromCsv(String titleRomanized, String titleKo) {
        if (titleRomanized != null && !titleRomanized.isBlank()) this.titleRomanized = titleRomanized;
        if (titleKo != null && !titleKo.isBlank()) this.titleKo = titleKo;
    }
}
