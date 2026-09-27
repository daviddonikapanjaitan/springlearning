package com.course.springlearning.user.repository;

import com.course.springlearning.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByIdAndDeletedFalse(Long id);

    Page<User> findAllByDeletedFalse(Pageable pageable);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    // SELECT ... FOR UPDATE, used by login so two logins of the same user cannot run at once.
    // Email is unique case-insensitively (ux_users_email), so there is at most one match
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findWithLockByEmailIgnoreCaseAndDeletedFalse(String email);
}
