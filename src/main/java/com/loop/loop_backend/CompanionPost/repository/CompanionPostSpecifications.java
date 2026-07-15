package com.loop.loop_backend.CompanionPost.repository;

import com.loop.loop_backend.CompanionPost.domain.CompanionActivity;
import com.loop.loop_backend.CompanionPost.domain.CompanionPost;
import com.loop.loop_backend.CompanionPost.domain.WatchDay;
import com.loop.loop_backend.User.domain.AgeGroup;
import com.loop.loop_backend.User.domain.Gender;
import com.loop.loop_backend.User.domain.User;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;

public final class CompanionPostSpecifications {

    private CompanionPostSpecifications() {
    }

    public static Specification<CompanionPost> concertIdEquals(Long concertId) {
        return (root, query, cb) -> cb.equal(root.get("concertId"), concertId);
    }

    public static Specification<CompanionPost> watchDayEquals(WatchDay watchDay) {
        return (root, query, cb) -> cb.equal(root.get("watchDay"), watchDay);
    }

    public static Specification<CompanionPost> hasActivity(CompanionActivity activity) {
        return (root, query, cb) -> cb.isMember(activity, root.get("activities"));
    }

    public static Specification<CompanionPost> userIdNotEquals(Long userId) {
        return (root, query, cb) -> cb.notEqual(root.get("user").get("id"), userId);
    }

    public static Specification<CompanionPost> genderEquals(Gender gender) {
        if (gender == null) {
            return null;
        }
        return (root, query, cb) -> {
            Join<CompanionPost, User> user = root.join("user");
            return cb.equal(user.get("gender"), gender);
        };
    }

    public static Specification<CompanionPost> ageGroupIn(List<AgeGroup> ageGroups) {
        if (ageGroups == null || ageGroups.isEmpty() || ageGroups.contains(AgeGroup.ANY)) {
            return null;
        }
        return (root, query, cb) -> {
            Join<CompanionPost, User> user = root.join("user");
            LocalDate today = LocalDate.now();

            Predicate[] rangePredicates = ageGroups.stream()
                    .map(group -> {
                        // birthDate <= today.minusYears(minAge) : 최소 minAge살 이상
                        Predicate atLeastMinAge = cb.lessThanOrEqualTo(
                                user.get("birthDate"), today.minusYears(group.getMinAge()));

                        if (group.getMaxAge() == null) {
                            return atLeastMinAge;
                        }
                        // birthDate > today.minusYears(maxAge + 1) : maxAge살 이하
                        Predicate atMostMaxAge = cb.greaterThan(
                                user.get("birthDate"), today.minusYears(group.getMaxAge() + 1));
                        return cb.and(atLeastMinAge, atMostMaxAge);
                    })
                    .toArray(Predicate[]::new);

            return cb.or(rangePredicates);
        };
    }
}