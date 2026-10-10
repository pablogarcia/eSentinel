package bo.solid.fiscal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import bo.solid.fiscal.domain.audit.AuditDraft;
import bo.solid.fiscal.domain.audit.AuditEvent;
import bo.solid.fiscal.domain.audit.AuditHasher;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditHasherTest {

    private static AuditDraft draft(String actor, String oldState) {
        return new AuditDraft(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                Instant.parse("2026-10-09T12:00:00.123456789Z"), actor, "TENANT_ADMIN", "CompanyRegistered",
                "COMPANY", null, null, oldState, "ACTIVE", null, null, "10.0.0.1", "corr", null);
    }

    @Test
    void hash_is_deterministic_and_64_hex_chars() {
        String h1 = AuditHasher.hash(AuditHasher.GENESIS, 1, draft("alice", null));
        String h2 = AuditHasher.hash(AuditHasher.GENESIS, 1, draft("alice", null));
        assertThat(h1).isEqualTo(h2).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void any_field_seq_or_previous_hash_changes_the_hash() {
        String base = AuditHasher.hash(AuditHasher.GENESIS, 1, draft("alice", null));
        assertThat(AuditHasher.hash(AuditHasher.GENESIS, 1, draft("mallory", null))).isNotEqualTo(base);
        assertThat(AuditHasher.hash(AuditHasher.GENESIS, 2, draft("alice", null))).isNotEqualTo(base);
        assertThat(AuditHasher.hash("1".repeat(64), 1, draft("alice", null))).isNotEqualTo(base);
    }

    @Test
    void null_and_empty_string_are_distinguished() {
        assertThat(AuditHasher.hash(AuditHasher.GENESIS, 1, draft("a", null)))
                .isNotEqualTo(AuditHasher.hash(AuditHasher.GENESIS, 1, draft("a", "")));
    }

    @Test
    void timestamps_are_truncated_to_micros_so_the_hash_survives_a_database_round_trip() {
        assertThat(draft("a", null).occurredAt()).isEqualTo(Instant.parse("2026-10-09T12:00:00.123456Z"));
    }

    @Test
    void sealed_event_verifies_only_against_the_right_previous_hash() {
        AuditEvent e = AuditEvent.seal(UUID.randomUUID(), 1, AuditHasher.GENESIS, draft("alice", null));
        assertThat(e.isConsistentWith(AuditHasher.GENESIS)).isTrue();
        assertThat(e.isConsistentWith("f".repeat(64))).isFalse();
    }
}
