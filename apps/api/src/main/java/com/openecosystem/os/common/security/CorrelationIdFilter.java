package com.openecosystem.os.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String correlationId = CorrelationIds.normalize(request.getHeader(CorrelationIds.HEADER_NAME));
    CorrelationContext.set(correlationId);
    response.setHeader(CorrelationIds.HEADER_NAME, correlationId);
    MDC.put("correlationId", correlationId);

    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove("correlationId");
      CorrelationContext.clear();
    }
  }
}
