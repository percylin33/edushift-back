package com.edushift.modules.paymentmethods.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Servicio de encriptación AES-256-GCM para datos sensibles de métodos de pago.
 *
 * <p>La key se inyecta desde variable de entorno {@code PAYMENT_ENCRYPTION_KEY}
 * (32 bytes en base64). Cada encriptación usa un IV aleatorio de 12 bytes
 * que se prepone al ciphertext.</p>
 */
@Service
public class PaymentEncryptionService {

	private static final String ALGORITHM = "AES/GCM/NoPadding";
	private static final int GCM_IV_LENGTH = 12;
	private static final int GCM_TAG_LENGTH = 128;

	private final SecretKeySpec secretKey;
	private final SecureRandom secureRandom = new SecureRandom();

	public PaymentEncryptionService(
			@Value("${payment.encryption.key}") String base64Key) {
		byte[] keyBytes = Base64.getDecoder().decode(base64Key);
		if (keyBytes.length != 32) {
			throw new IllegalArgumentException(
				"PAYMENT_ENCRYPTION_KEY debe ser 32 bytes (256 bits) en base64");
		}
		this.secretKey = new SecretKeySpec(keyBytes, "AES");
	}

	/**
	 * Encripta un string y retorna base64(iv + ciphertext).
	 */
	public String encrypt(String plaintext) {
		if (plaintext == null || plaintext.isEmpty()) {
			return plaintext;
		}
		try {
			byte[] iv = new byte[GCM_IV_LENGTH];
			secureRandom.nextBytes(iv);

			Cipher cipher = Cipher.getInstance(ALGORITHM);
			GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
			cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

			byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

			// Prepone IV al ciphertext
			ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
			byteBuffer.put(iv);
			byteBuffer.put(ciphertext);

			return Base64.getEncoder().encodeToString(byteBuffer.array());
		} catch (Exception e) {
			throw new PaymentEncryptionException("Error encriptando dato sensible", e);
		}
	}

	/**
	 * Desencripta un string base64(iv + ciphertext).
	 */
	public String decrypt(String encryptedData) {
		if (encryptedData == null || encryptedData.isEmpty()) {
			return encryptedData;
		}
		try {
			byte[] decoded = Base64.getDecoder().decode(encryptedData);

			ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
			byte[] iv = new byte[GCM_IV_LENGTH];
			byteBuffer.get(iv);
			byte[] ciphertext = new byte[byteBuffer.remaining()];
			byteBuffer.get(ciphertext);

			Cipher cipher = Cipher.getInstance(ALGORITHM);
			GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
			cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

			byte[] plaintext = cipher.doFinal(ciphertext);
			return new String(plaintext, StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new PaymentEncryptionException("Error desencriptando dato sensible", e);
		}
	}

	/**
	 * Retorna solo los últimos 4 dígitos para mostrar en logs o UI.
	 */
	public String mask(String plaintext) {
		if (plaintext == null || plaintext.length() <= 4) {
			return "****";
		}
		return "****" + plaintext.substring(plaintext.length() - 4);
	}

	public static class PaymentEncryptionException extends RuntimeException {
		public PaymentEncryptionException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
