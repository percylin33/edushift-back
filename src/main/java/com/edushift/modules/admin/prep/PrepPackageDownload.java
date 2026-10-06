package com.edushift.modules.admin.prep;

/**
 * Binary ZIP payload plus suggested download file name.
 */
public record PrepPackageDownload(byte[] bytes, String fileName) {
}
