package com.urban.settlement.auth;

import com.urban.settlement.model.enums.Role;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthenticationResponse {

  @JsonProperty("access_token")
  private String accessToken;
  @JsonProperty("refresh_token")
  private String refreshToken;
  private Role role;
  private String email;
  private String message;
  private String officerId;
  private Boolean isFirstTime = true;
  private Boolean isVerified = true;
  private Boolean hasTwoFactor = false;
}
