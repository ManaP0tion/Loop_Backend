package com.loop.loop_backend.CompanionPost.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "companion_posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanionPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
}
