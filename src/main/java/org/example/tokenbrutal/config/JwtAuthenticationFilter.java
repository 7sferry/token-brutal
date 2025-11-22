package org.example.tokenbrutal.config;

/************************
 * Made by [MR Ferry™]  *
 * on November 2025     *
 ************************/

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.example.tokenbrutal.util.TokenUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.ArrayList;

public class JwtAuthenticationFilter implements Filter{

	public static final String BEARER_PREFIX = "Bearer ";

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException{
		HttpServletRequest req = (HttpServletRequest) servletRequest;
		String header = req.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.startsWith(BEARER_PREFIX)) {
			filterChain.doFilter(servletRequest, servletResponse);
			return;
		}
		String accessToken = header.substring(BEARER_PREFIX.length());
		Jws<Claims> claimsJws = TokenUtil.parseJwtToken(accessToken);
		if(claimsJws == null){
			filterChain.doFilter(servletRequest, servletResponse);
			return;
		}
		UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(claimsJws.getPayload(), null, new ArrayList<>());
		SecurityContextHolder.getContext().setAuthentication(auth);
		filterChain.doFilter(servletRequest, servletResponse);
	}
}
