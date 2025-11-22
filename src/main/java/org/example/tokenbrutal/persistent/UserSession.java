package org.example.tokenbrutal.persistent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/************************
 * Made by [MR Ferry™]  *
 * on November 2025     *
 ************************/

@Getter
@Setter
@EqualsAndHashCode(of = "sessionId")
@Entity
public class UserSession{
	@Id
	private String sessionId;
	private String username;
	@Column(updatable = false)
	private Instant creationTime;
	private Instant expirationTime;
}
