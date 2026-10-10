package bo.solid.fiscal.domain.siat;

import java.time.Instant;
import java.util.List;

public record CuisResult(boolean ok, String codigo, Instant validUntil, List<SiatMessage> messages) {
    public CuisResult { messages = List.copyOf(messages); }

    @Override
    public String toString() { return "CuisResult[ok=" + ok + ", validUntil=" + validUntil + ", messages=" + messages + "]"; }
}
