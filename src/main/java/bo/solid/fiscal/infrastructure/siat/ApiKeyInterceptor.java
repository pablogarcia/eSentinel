package bo.solid.fiscal.infrastructure.siat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

/**
 * Agrega el token delegado como header HTTP (no va en el sobre SOAP). El token viaja por un ThreadLocal acotado a la
 * llamada sincronica, de modo que los proxies CXF se comparten entre contribuyentes sin mezclar credenciales.
 */
class ApiKeyInterceptor extends AbstractPhaseInterceptor<Message> {

    private static final ThreadLocal<String> TOKEN = new ThreadLocal<>();

    private final String headerName;
    private final String prefix;

    ApiKeyInterceptor(String headerName, String prefix) {
        super(Phase.PRE_STREAM);
        this.headerName = headerName;
        this.prefix = prefix;
    }

    static void bind(String token) { TOKEN.set(token); }

    static void unbind() { TOKEN.remove(); }

    @Override
    @SuppressWarnings("unchecked")
    public void handleMessage(Message message) {
        String token = TOKEN.get();
        if (token == null) return;
        Map<String, List<String>> headers = (Map<String, List<String>>) message.get(Message.PROTOCOL_HEADERS);
        if (headers == null) {
            headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            message.put(Message.PROTOCOL_HEADERS, headers);
        }
        List<String> values = new ArrayList<>();
        values.add(prefix + token);
        headers.put(headerName, values);
    }
}
