package com.edushift.modules.files.service;

import com.edushift.modules.files.entity.FileObject;
import org.springframework.security.core.Authentication;

/** Modules can narrow access to their files before metadata or bytes are returned. */
public interface FileReadAccessGuard {
    void requireReadable(FileObject file, Authentication authentication);

    /** Modules may prevent deletion while a file remains referenced by protected content. */
    default void requireDeletable(FileObject file, Authentication authentication) {
        // Read permissions and deletion rules are separate; modules opt in when needed.
    }
}
