package bo.solid.fiscal.domain.siat;

import java.time.LocalDateTime;
import java.util.List;

/** Fecha/hora oficial del SIAT (sin zona, hora de Bolivia segun el servicio). */
public record SiatDateTime(boolean ok, LocalDateTime serverTime, List<SiatMessage> messages) {
    public SiatDateTime {
        messages = List.copyOf(messages);
    }
}
