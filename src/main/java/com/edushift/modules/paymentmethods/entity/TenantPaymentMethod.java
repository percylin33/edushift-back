package com.edushift.modules.paymentmethods.entity;

import com.edushift.shared.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;

/**
 * Método de pago configurado por el tenant para que los apoderados
 * realicen pagos (transferencia bancaria, Yape, Plin, etc.).
 *
 * <p>Los datos sensibles (account_number, cci, wallet_phone) se
 * encriptan a nivel de aplicación con AES-256-GCM antes de persistir.
 * El QR de billetera se almacena como URL pública (Firebase Storage).</p>
 */
@Entity
@SQLDelete(sql = "UPDATE edushift.tenant_payment_methods SET deleted = true, deleted_at = NOW(), updated_at = NOW() WHERE id = ?")
@Table(name = "tenant_payment_methods", schema = "edushift")
@Getter
@Setter
@NoArgsConstructor
public class TenantPaymentMethod extends TenantAwareEntity {

	public enum PaymentMethodType {
		BANK_TRANSFER, YAPE, PLIN, CASH
	}

	@Column(name = "public_uuid", nullable = false, updatable = false, unique = true)
	private UUID publicUuid;

	@Enumerated(EnumType.STRING)
	@Column(name = "method_type", nullable = false, length = 20)
	private PaymentMethodType methodType;

	@Column(name = "display_name", length = 100)
	private String displayName;

	// Transferencia bancaria
	@Column(name = "bank_name", length = 100)
	private String bankName;

	@Column(name = "account_holder", length = 200)
	private String accountHolder;

	@Column(name = "account_number")
	private String accountNumber; // ENCRIPTADO

	@Column(name = "cci")
	private String cci; // ENCRIPTADO

	// Billeteras digitales
	@Column(name = "wallet_phone", columnDefinition = "text")
	private String walletPhone; // ENCRIPTADO

	@Column(name = "wallet_qr_url")
	private String walletQrUrl;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	@Column(name = "is_default", nullable = false)
	private boolean defaultMethod = false;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Override
	public void markDeleted() {
		super.markDeleted();
		this.deletedAt = Instant.now();
	}

	@Override
	public void restore() {
		super.restore();
		this.deletedAt = null;
	}

	@PrePersist
	private void onCreate() {
		if (publicUuid == null) publicUuid = UUID.randomUUID();
	}
}
