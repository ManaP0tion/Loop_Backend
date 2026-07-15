package com.loop.loop_backend.Block.service;

import com.loop.loop_backend.Block.domain.Block;
import com.loop.loop_backend.Block.dto.BlockedUserResponseDto;
import com.loop.loop_backend.Block.repository.BlockRepository;
import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * BlockRepository를 Mockito mock 위에 인메모리 리스트를 얹어 실제 저장소처럼 동작하게 만들고,
 * 그 위에서 BlockServiceImpl의 실제 동작(차단 → 조회 → 해제 → 재조회)을 블랙박스로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BlockServiceImplTest {

    @Mock UserRepository userRepository;
    @Mock BlockRepository blockRepository;
    @InjectMocks BlockServiceImpl blockService;

    private final List<Block> store = new ArrayList<>();

    private User userA;
    private User userB;
    private User userC;

    @BeforeEach
    void setUp() {
        store.clear();

        userA = testUser("kakao-1", "userA");
        userB = testUser("kakao-2", "userB");
        userC = testUser("kakao-3", "userC");

        when(userRepository.findById(1L)).thenReturn(Optional.of(userA));
        when(userRepository.findById(2L)).thenReturn(Optional.of(userB));
        when(userRepository.findById(3L)).thenReturn(Optional.of(userC));
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        when(blockRepository.saveAndFlush(any(Block.class))).thenAnswer(invocation -> {
            Block block = invocation.getArgument(0);
            store.add(block);
            return block;
        });
        when(blockRepository.existsByBlockerAndBlocked(any(User.class), any(User.class)))
                .thenAnswer(invocation -> store.stream().anyMatch(b ->
                        b.getBlocker().equals(invocation.getArgument(0))
                                && b.getBlocked().equals(invocation.getArgument(1))));
        when(blockRepository.findByBlockerAndBlocked(any(User.class), any(User.class)))
                .thenAnswer(invocation -> store.stream()
                        .filter(b -> b.getBlocker().equals(invocation.getArgument(0))
                                && b.getBlocked().equals(invocation.getArgument(1)))
                        .findFirst());
        when(blockRepository.findAllByBlocker(any(User.class)))
                .thenAnswer(invocation -> store.stream()
                        .filter(b -> b.getBlocker().equals(invocation.getArgument(0)))
                        .toList());
        doAnswer(invocation -> {
            store.remove((Block) invocation.getArgument(0));
            return null;
        }).when(blockRepository).delete(any(Block.class));
    }

    private User testUser(String providerId, String nickname) {
        User user = User.builder()
                .authProvider(AuthProvider.KAKAO)
                .providerId(providerId)
                .status(Status.ACTIVE)
                .onboardingCompleted(true)
                .build();
        user.updateUserProfile(nickname, null);
        return user;
    }

    // ── 실제 시나리오: 3명 생성 → 2명 차단 → 조회 → 1명 해제 → 재조회 ─────────────

    @Test
    void 두_명을_차단하고_조회한_뒤_한_명을_해제하면_목록에서_그_한_명만_빠진다() {
        blockService.block(1L, 2L);
        blockService.block(1L, 3L);

        List<BlockedUserResponseDto> afterBlock = blockService.getBlockedUsers(1L);
        assertThat(afterBlock).hasSize(2);
        assertThat(afterBlock).extracting(BlockedUserResponseDto::getNickname)
                .containsExactlyInAnyOrder("userB", "userC");

        blockService.unblock(1L, 2L);

        List<BlockedUserResponseDto> afterUnblock = blockService.getBlockedUsers(1L);
        assertThat(afterUnblock).hasSize(1);
        assertThat(afterUnblock.get(0).getNickname()).isEqualTo("userC");
    }

    @Test
    void 차단_안한_사람의_목록은_비어있다() {
        blockService.block(1L, 2L);

        assertThat(blockService.getBlockedUsers(2L)).isEmpty();
        assertThat(blockService.getBlockedUsers(3L)).isEmpty();
    }

    @Test
    void 차단_해제_후에는_다시_같은_사람을_차단할_수_있다() {
        blockService.block(1L, 2L);
        blockService.unblock(1L, 2L);

        blockService.block(1L, 2L);

        assertThat(blockService.getBlockedUsers(1L))
                .extracting(BlockedUserResponseDto::getNickname)
                .containsExactly("userB");
    }

    @Test
    void 생년월일이_없는_사용자는_차단_목록에서_나이가_null이다() {
        blockService.block(1L, 2L);

        BlockedUserResponseDto dto = blockService.getBlockedUsers(1L).get(0);

        assertThat(dto.getAge()).isNull();
    }

    // ── 예외 케이스 ────────────────────────────────────────────────────────────

    @Test
    void 자기_자신을_차단하려_하면_SELF_REPORT_NOT_ALLOWED_예외를_던진다() {
        assertThatThrownBy(() -> blockService.block(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SELF_REPORT_NOT_ALLOWED);
    }

    @Test
    void 존재하지_않는_사용자를_차단하려_하면_USER_NOT_FOUND_예외를_던진다() {
        assertThatThrownBy(() -> blockService.block(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 존재하지_않는_사용자가_차단을_시도하면_USER_NOT_FOUND_예외를_던진다() {
        assertThatThrownBy(() -> blockService.block(999L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 이미_차단한_사용자를_다시_차단하려_하면_ALREADY_BLOCKED_예외를_던진다() {
        blockService.block(1L, 2L);

        assertThatThrownBy(() -> blockService.block(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_BLOCKED);
    }

    @Test
    void 차단하지_않은_사용자를_해제하려_하면_BLOCK_NOT_FOUND_예외를_던진다() {
        assertThatThrownBy(() -> blockService.unblock(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BLOCK_NOT_FOUND);
    }

    @Test
    void 이미_해제한_차단을_다시_해제하려_하면_BLOCK_NOT_FOUND_예외를_던진다() {
        blockService.block(1L, 2L);
        blockService.unblock(1L, 2L);

        assertThatThrownBy(() -> blockService.unblock(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BLOCK_NOT_FOUND);
    }

    @Test
    void 존재하지_않는_사용자의_차단_목록을_조회하면_USER_NOT_FOUND_예외를_던진다() {
        assertThatThrownBy(() -> blockService.getBlockedUsers(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}