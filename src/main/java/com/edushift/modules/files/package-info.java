/**
 * Files module — binary storage registry + download endpoint
 * (Sprint 7a / BE-7a.0). The bytes live in the active provider
 * (Firebase Storage in prod/staging, local filesystem in dev/test);
 * this module owns the {@code lms_file_objects} table that maps a
 * public UUID to a (provider, remoteKey) pair.
 */
package com.edushift.modules.files;
