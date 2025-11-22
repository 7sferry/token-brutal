package org.example.tokenbrutal.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.tokenbrutal.persistent.UserSession;
import org.example.tokenbrutal.repository.UserSessionRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/************************
 * Made by [MR Ferry™]  *
 * on November 2025     *
 ************************/

@Service
@Slf4j
@RequiredArgsConstructor
public class TokenExtenderService{
	private final UserSessionRepository userSessionRepository;

	@Async
	@Transactional
	public void extendRefreshToken(Instant instant, UserSession userSession){
		log.info("Refreshing token for user {}", userSession.getUsername());
		userSession.setExpirationTime(instant.plusSeconds(TokenUtil.REFRESH_TOKEN_MAX_AGE_SECONDS));
		userSessionRepository.save(userSession);
	}

}
