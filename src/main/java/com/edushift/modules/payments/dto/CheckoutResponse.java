package com.edushift.modules.payments.dto;

import java.util.UUID;

public record CheckoutResponse(
        UUID paymentPublicUuid,
        String initPoint,
        String sandboxInitPoint
) {}
