package bo.solid.fiscal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FiscalInvoicingApplication {
    public static void main(String[] args) {
        SpringApplication.run(FiscalInvoicingApplication.class, args);
    }
}
