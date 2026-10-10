package bo.solid.fiscal.application.idempotency;

public record StoredResponse(int status, String location, String body) {}
