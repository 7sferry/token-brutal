package org.example.tokenbrutal.persistent;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/************************
 * Made by [MR Ferry™]  *
 * on November 2025     *
 ************************/

@Getter
@Setter
@Entity
public class UserSession{
	@Id
	private String sessionId;
	private String username;
	private Instant creationTime;
	private Instant expirationTime;
}
