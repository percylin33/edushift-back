package com.edushift.modules.auth.service;

import com.edushift.modules.auth.dto.CompleteOnboardingRequest;
import com.edushift.modules.auth.dto.OnboardingMeResponse;
import com.edushift.modules.auth.dto.UserResponse;

public interface FirstLoginOnboardingService {

	OnboardingMeResponse getOnboarding();

	UserResponse completeOnboarding(CompleteOnboardingRequest request);
}
