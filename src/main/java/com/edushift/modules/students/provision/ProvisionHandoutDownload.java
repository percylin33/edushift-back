package com.edushift.modules.students.provision;

/**
 * CRED-7 zip download payload (bytes + suggested filename).
 */
public record ProvisionHandoutDownload(byte[] bytes, String fileName) {}
