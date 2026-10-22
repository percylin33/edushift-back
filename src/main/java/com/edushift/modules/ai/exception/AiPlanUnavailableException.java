package com.edushift.modules.ai.exception;

import org.springframework.http.HttpStatus;

public class AiPlanUnavailableException extends AiModuleException {
    public AiPlanUnavailableException() {
        super(HttpStatus.FORBIDDEN, "AI_PLAN_UNAVAILABLE",
                "El plan o la suscripción del colegio no permite usar IA ahora.");
    }
}
