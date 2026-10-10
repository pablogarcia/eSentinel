package bo.solid.fiscal.integration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** SIAT simulado (SOAP 1.1 sobre HTTP). Los tests programan la respuesta y leen headers/cuerpo recibidos. */
final class FakeSiatServer {

    record Request(String path, Map<String, List<String>> headers, String body) {
        String header(String name) {
            return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name))
                    .map(e -> e.getValue().get(0)).findFirst().orElse(null);
        }
    }

    record Response(int status, String body, long delayMs) {
        static Response soap(String innerBody) {
            return new Response(200, envelope(innerBody), 0);
        }
        static Response fault(String faultString) {
            return new Response(500, envelope("<soap:Fault><faultcode>soap:Server</faultcode><faultstring>"
                    + faultString + "</faultstring></soap:Fault>"), 0);
        }
        static Response http(int status) { return new Response(status, "<html>gateway</html>", 0); }
        Response delayed(long ms) { return new Response(status, body, ms); }
    }

    static String envelope(String inner) {
        return "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>" + inner
                + "</soap:Body></soap:Envelope>";
    }

    static final String COMM_OK = "<ns2:verificarComunicacionResponse xmlns:ns2=\"https://siat.impuestos.gob.bo/\">"
            + "<RespuestaComunicacion><mensajesList><codigo>1</codigo><descripcion>OK simulado</descripcion></mensajesList>"
            + "<transaccion>true</transaccion></RespuestaComunicacion></ns2:verificarComunicacionResponse>";

    static String fechaHora(String value) {
        return "<ns2:sincronizarFechaHoraResponse xmlns:ns2=\"https://siat.impuestos.gob.bo/\"><RespuestaFechaHora>"
                + "<transaccion>true</transaccion><fechaHora>" + value + "</fechaHora></RespuestaFechaHora>"
                + "</ns2:sincronizarFechaHoraResponse>";
    }

    private final HttpServer server;
    final List<Request> requests = new CopyOnWriteArrayList<>();
    private volatile Function<Request, Response> behavior = r -> Response.soap(COMM_OK);

    FakeSiatServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.createContext("/", this::handle);
        server.start();
    }

    int port() { return server.getAddress().getPort(); }

    String baseUrl() { return "http://127.0.0.1:" + port() + "/v2"; }

    void respondWith(Function<Request, Response> f) { this.behavior = f; }

    void reset() {
        requests.clear();
        behavior = r -> Response.soap(COMM_OK);
    }

    Request last() { return requests.get(requests.size() - 1); }

    private void handle(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Request req = new Request(ex.getRequestURI().getPath(), Map.copyOf(ex.getRequestHeaders()), body);
        requests.add(req);
        Response res = behavior.apply(req);
        if (res.delayMs() > 0) {
            try { Thread.sleep(res.delayMs()); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
        byte[] out = res.body().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "text/xml;charset=UTF-8");
        try {
            ex.sendResponseHeaders(res.status(), out.length);
            ex.getResponseBody().write(out);
        } catch (IOException ignored) {
            // el cliente ya cerro por timeout
        } finally {
            ex.close();
        }
    }
}
