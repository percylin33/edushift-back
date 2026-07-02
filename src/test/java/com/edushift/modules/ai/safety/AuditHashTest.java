package com.edushift.modules.ai.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Modifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditHashTest {

    @Test
    @DisplayName("sha256Hex returns 64-char lowercase hex for a known input")
    void knownVector() {
        // Known SHA-256 of "abc"
        String hex = AuditHash.sha256Hex("abc");
        assertThat(hex).isEqualTo(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    @DisplayName("sha256Hex is deterministic for the same input")
    void deterministic() {
        String a = AuditHash.sha256Hex("hello world");
        String b = AuditHash.sha256Hex("hello world");
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSize(64);
    }

    @Test
    @DisplayName("sha256Hex returns null for null input")
    void nullInput() {
        assertThat(AuditHash.sha256Hex(null)).isNull();
    }

    @Test
    @DisplayName("sha256Hex produces different hashes for different inputs")
    void differentInputs() {
        assertThat(AuditHash.sha256Hex("a")).isNotEqualTo(AuditHash.sha256Hex("b"));
    }

    @Test
    @DisplayName("class is a utility — only a private no-arg constructor is declared")
    void utilityCtor() {
        var ctors = AuditHash.class.getDeclaredConstructors();
        assertThat(ctors).hasSize(1);
        var ctor = ctors[0];
        assertThat(Modifier.isPrivate(ctor.getModifiers())).isTrue();
        // A private constructor can still be invoked from a unit test via
        // setAccessible — the point is the modifier, not the visibility.
        assertThatCode(() -> {
            ctor.setAccessible(true);
            ctor.newInstance();
        }).doesNotThrowAnyException();
    }
}