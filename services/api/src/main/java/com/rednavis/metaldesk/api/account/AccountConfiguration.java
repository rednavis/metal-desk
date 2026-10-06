package com.rednavis.metaldesk.api.account;

import com.rednavis.metaldesk.api.account.verification.VerificationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds the account lifecycle's externally configured settings. */
@Configuration
@EnableConfigurationProperties(VerificationProperties.class)
public class AccountConfiguration {}
