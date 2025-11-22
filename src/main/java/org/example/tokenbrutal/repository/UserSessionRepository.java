package org.example.tokenbrutal.repository;

import org.example.tokenbrutal.persistent.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/************************
 * Made by [MR Ferry™]  *
 * on November 2025     *
 ************************/

public interface UserSessionRepository extends JpaRepository<UserSession, String>{
	Optional<UserSession> findByRefreshToken(String refreshToken);
}
