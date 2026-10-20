package com.easemob.customer.open.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuestTokenResp {

    private String token;
    private String userId;
    private String easemobToken;
    private Long expiresIn;
}
