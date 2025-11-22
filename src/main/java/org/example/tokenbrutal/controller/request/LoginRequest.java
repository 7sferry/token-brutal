package org.example.tokenbrutal.controller.request;

import lombok.Builder;

/************************
 * Made by [MR Ferry™]  *
 * on November 2025     *
 ************************/

@Builder
public record LoginRequest(String username, String password){
}
