package com.loop.loop_backend.Artist.repository;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.domain.Nationality;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface ArtistRepository extends JpaRepository<Artist, Long> {
    Optional<Artist> findByName(String name);
    List<Artist> findByAutoFetchConcertsTrue();
    // nationality <> :excluded 는 null 행을 빼므로 미입력은 따로 붙인다
    List<Artist> findByNationalityNotOrNationalityIsNull(Nationality excluded);

    // 이름(서비스명/기본명칭/한국어/별칭) 부분 일치 + 국적. 각 조건은 null이면 걸지 않는다.
    @Query("SELECT a FROM Artist a WHERE " +
            "(:nationality IS NULL OR a.nationality = :nationality) AND " +
            "(:q IS NULL OR " +
            "LOWER(a.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(a.baseName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(a.nameKo) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(a.nameAlias) LIKE LOWER(CONCAT('%', :q, '%')))")
    List<Artist> search(@Param("q") String q, @Param("nationality") Nationality nationality);

    // 이미지 복사 중에 관리자가 imageUrl을 바꿨으면 덮어쓰지 않도록 원래 값일 때만 교체한다. 반환값 0이면 건너뜀.
    @Modifying
    @Transactional
    @Query("UPDATE Artist a SET a.imageUrl = :newUrl WHERE a.id = :id AND a.imageUrl = :oldUrl")
    int replaceImageUrl(@Param("id") Long id, @Param("oldUrl") String oldUrl, @Param("newUrl") String newUrl);

    List<Artist> findByItunesArtistIdIsNull();

    List<Artist> findByItunesArtistIdIsNotNullOrderByIdAsc();

    // iTunes 일괄 자동 연결. 도는 사이 관리자가 직접 연결했으면 덮어쓰지 않도록 미연결일 때만. 반환값 0이면 건너뜀.
    // autoLinked=false면 이름이 같아 확인 없이 확정으로 저장한다.
    @Modifying
    @Transactional
    @Query("UPDATE Artist a SET a.itunesArtistId = :itunesId, a.artistViewUrl = :viewUrl, a.itunesArtistName = :itunesName, " +
            "a.itunesAutoLinked = :autoLinked WHERE a.id = :id AND a.itunesArtistId IS NULL")
    int autoLinkItunes(@Param("id") Long id, @Param("itunesId") Long itunesId,
                       @Param("viewUrl") String viewUrl, @Param("itunesName") String itunesName,
                       @Param("autoLinked") boolean autoLinked);
}
