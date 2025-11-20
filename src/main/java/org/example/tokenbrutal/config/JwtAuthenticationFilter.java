package org.example.tokenbrutal.config;

/************************
 * Made by [MR Ferry™]  *
 * on November 2025     *
 ************************/

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import jakarta.servlet.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.tokenbrutal.util.JwtUtil;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.ArrayList;

public class JwtAuthenticationFilter implements Filter{

	private String extractTokenFromCookie(HttpServletRequest req, String cookieName){
		if(req.getCookies() == null) return null;
		for(Cookie c : req.getCookies()){
			if(cookieName.equals(c.getName())) return c.getValue();
		}
		return null;
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException{
		HttpServletRequest req = (HttpServletRequest) request;
		HttpServletResponse res = (HttpServletResponse) response;
		String accessToken = extractTokenFromCookie(req, "access_token");
		Jws<Claims> claimsJws = JwtUtil.validateToken(accessToken);
		if(claimsJws == null){
			chain.doFilter(request, response);
			return;
		}
		UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(claimsJws.getPayload(), null, new ArrayList<>());
		SecurityContextHolder.getContext().setAuthentication(auth);
		chain.doFilter(request, response);
	}
}
