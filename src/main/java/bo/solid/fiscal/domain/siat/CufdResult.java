package bo.solid.fiscal.domain.siat;

import java.time.Instant;
import java.util.List;

public record CufdResult(boolean ok, String codigo, String codigoControl, String direccion, Instant validUntil,
                         List<SiatMessage> messages) {
    public CufdResult { messages = List.copyOf(messages); }

    @Override
    public String toString() { return "CufdResult[ok=" + ok + ", validUntil=" + validUntil + ", messages=" + messages + "]"; }
}
