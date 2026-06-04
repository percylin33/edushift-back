package com.edushift.modules.tenants.exception;

import com.edushift.shared.exception.ResourceNotFoundException;

/**
 * Thrown when a tenant cannot be resolved (typically by slug at login time).
 * <p>
 * Maps to HTTP 404 via {@link ResourceNotFoundException} with a stable error
 * code so the frontend can show a precise message
 * (e.g. <em>"Colegio no encontrado"</em>) instead of a generic 404.
 */
public class TenantNotFoundException extends ResourceNotFoundException {

	public static TenantNotFoundException forSlug(String slug) {
		return new TenantNotFoundException(
				"Tenant not found for slug: " + slug);
	}

	public static TenantNotFoundException forId(Object id) {
		return new TenantNotFoundException(
				"Tenant not found with id: " + id);
	}

	private TenantNotFoundException(String message) {
		super(message);
	}

}
