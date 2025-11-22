package org.example.tokenbrutal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class TokenBrutalApplication{

	static void main(String[] args){
		SpringApplication.run(TokenBrutalApplication.class, args);
	}

}
