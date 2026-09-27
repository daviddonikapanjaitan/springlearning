package com.course.springlearning.auth.repository;

import com.course.springlearning.auth.entity.Token;
import com.course.springlearning.auth.security.AuthenticatedUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TokenRepository extends JpaRepository<Token, Long> {

    // A token is active while it is not revoked, not expired, not past expires_at,
    // and its user is enabled and not deleted
    @Query("""
            SELECT new com.course.springlearning.auth.security.AuthenticatedUser(u.id, u.username)
            FROM Token t JOIN t.user u
            WHERE t.token = :token
              AND t.revoked = false AND t.expired = false AND t.expiresAt > :now
              AND u.deleted = false AND u.enabled = true
            """)
    Optional<AuthenticatedUser> findActiveUserByToken(@Param("token") String token, @Param("now") Instant now);

    @Query("""
            SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END
            FROM Token t JOIN t.user u
            WHERE t.token = :token
              AND t.revoked = false AND t.expired = false AND t.expiresAt > :now
              AND u.deleted = false AND u.enabled = true
            """)
    boolean isActive(@Param("token") String token, @Param("now") Instant now);

    // Tokens of the user that are not revoked and expired yet (at most one after a normal login)
    @Query("SELECT t FROM Token t WHERE t.user.id = :userId AND (t.revoked = false OR t.expired = false)")
    List<Token> findAllNotRevokedByUserId(@Param("userId") Long userId);

    /** Revokes and expires one token, e.g. on logout. Returns 0 when it was already revoked or does not exist. */
    @Transactional
    @Modifying
    @Query("""
            UPDATE Token t SET t.revoked = true, t.expired = true, t.updatedAt = :now, t.updatedBy = :actor
            WHERE t.token = :token AND (t.revoked = false OR t.expired = false)
            """)
    int revokeByToken(@Param("token") String token, @Param("actor") String actor, @Param("now") Instant now);

    /** Revokes and expires every token whose expires_at has passed. */
    @Transactional
    @Modifying
    @Query("""
            UPDATE Token t SET t.revoked = true, t.expired = true, t.updatedAt = :now, t.updatedBy = :actor
            WHERE t.expiresAt <= :now AND (t.revoked = false OR t.expired = false)
            """)
    int revokeAllExpired(@Param("actor") String actor, @Param("now") Instant now);
}
