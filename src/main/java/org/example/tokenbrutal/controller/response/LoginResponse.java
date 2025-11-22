package org.example.tokenbrutal.controller.response;

import lombok.Builder;

/************************
 * Made by [MR Ferry™]  *
 * on November 2025     *
 ************************/

@Builder
public record LoginResponse(String accessToken, String message){
}
