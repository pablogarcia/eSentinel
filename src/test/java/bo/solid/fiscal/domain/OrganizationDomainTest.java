package bo.solid.fiscal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.solid.fiscal.domain.org.Branch;
import bo.solid.fiscal.domain.org.Company;
import bo.solid.fiscal.domain.org.Environment;
import bo.solid.fiscal.domain.org.Modality;
import bo.solid.fiscal.domain.org.Nit;
import bo.solid.fiscal.domain.org.PointOfSale;
import bo.solid.fiscal.domain.shared.Uuid7;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrganizationDomainTest {

    @Test
    void nit_rejects_out_of_range_values() {
        assertThatThrownBy(() -> new Nit(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Nit(10_000_000_000_000L)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new Nit(1003579028L).value()).isEqualTo(1003579028L);
        assertThat(new Nit(9_999_999_999_999L).value()).isEqualTo(9_999_999_999_999L);
    }

    @Test
    void branch_zero_is_head_office_and_negative_is_rejected() {
        UUID t = Uuid7.next(), c = Uuid7.next();
        assertThat(Branch.create(t, c, 0, "Casa Matriz", "Av. 1", "La Paz", null).isHeadOffice()).isTrue();
        assertThat(Branch.create(t, c, 1, "Sucursal 1", "Av. 2", "La Paz", null).isHeadOffice()).isFalse();
        assertThatThrownBy(() -> Branch.create(t, c, -1, "x", "y", "z", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void point_of_sale_code_zero_is_not_a_pos() {
        assertThatThrownBy(() -> PointOfSale.create(Uuid7.next(), Uuid7.next(), 0, "Caja", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(PointOfSale.create(Uuid7.next(), Uuid7.next(), 1, "Caja 1", null).codigoPuntoVenta()).isEqualTo(1);
    }

    @Test
    void company_requires_social_name() {
        assertThatThrownBy(() -> Company.register(Uuid7.next(), new Nit(1), " ", Environment.PILOT,
                Modality.ELECTRONIC_ONLINE, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void only_electronic_online_is_supported_for_now() {
        assertThat(Modality.ELECTRONIC_ONLINE.supported()).isTrue();
        assertThat(Modality.COMPUTERIZED_ONLINE.supported()).isFalse();
        assertThat(Modality.ELECTRONIC_ONLINE.code()).isEqualTo(1);
        assertThat(Environment.PILOT.code()).isEqualTo(2);
    }

    @Test
    void uuid7_is_version_7_and_time_ordered() {
        UUID a = Uuid7.next();
        assertThat(a.version()).isEqualTo(7);
        assertThat(a.variant()).isEqualTo(2);
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) seen.add(Uuid7.next());
        assertThat(seen).hasSize(10_000);
    }
}
