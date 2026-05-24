package com.agentx.backend.auth.api;

import com.agentx.backend.auth.application.AuthException;
import com.agentx.backend.auth.application.AuthService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/login")
  public AuthService.LoginResponse login(@RequestBody LoginRequest request) {
    return authService.login(new AuthService.LoginRequest(request.email(), request.password()));
  }

  @ExceptionHandler(AuthException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public Map<String, String> handleAuthException(AuthException exception) {
    return Map.of("code", exception.getMessage());
  }

  public record LoginRequest(@Email String email, @NotBlank String password) {}
}
