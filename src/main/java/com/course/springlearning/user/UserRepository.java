package com.course.springlearning.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByIdAndDeletedFalse(Long id);

    Page<User> findAllByDeletedFalse(Pageable pageable);

    boolean existsByEmailAndDeletedFalse(String email);

    boolean existsByUsernameAndDeletedFalse(String username);

    boolean existsByEmailAndDeletedFalseAndIdNot(String email, Long id);

    boolean existsByUsernameAndDeletedFalseAndIdNot(String username, Long id);
}
