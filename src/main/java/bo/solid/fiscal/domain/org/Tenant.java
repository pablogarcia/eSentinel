package bo.solid.fiscal.domain.org;

import bo.solid.fiscal.domain.shared.Uuid7;
import java.time.Instant;
import java.util.UUID;

public record Tenant(UUID id, String name, String status, long version, Instant createdAt) {

    public Tenant {
        if (name == null || name.isBlank() || name.length() > 200) {
            throw new IllegalArgumentException("Nombre de tenant invalido");
        }
    }

    public static Tenant create(String name) {
        return new Tenant(Uuid7.next(), name.strip(), "ACTIVE", 0, Instant.now());
    }
}
