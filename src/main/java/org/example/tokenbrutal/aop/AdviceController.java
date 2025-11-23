package org.example.tokenbrutal.aop;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/************************
 * Made by [MR Ferry™]  *
 * on November 2025     *
 ************************/

@Slf4j
@RestControllerAdvice
public class AdviceController{

	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	@ExceptionHandler(Exception.class)
	public String handleGeneralError(Exception e) {
		log.error("Error: {}", e.getMessage(), e);
		return "error";
	}

}
