package itu.prom16.eval.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import itu.prom16.eval.dto.SupplierInvoiceDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

@Service
public class AccountingService {

    private static final Logger logger = LoggerFactory.getLogger(AccountingService.class);
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${erpnext.api.base-url}")
    private String baseUrl;

    public AccountingService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    private HttpHeaders createHeaders(String sid) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Cookie", "sid=" + sid);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private SupplierInvoiceDTO mapToInvoiceDTO(JsonNode node) {
        SupplierInvoiceDTO invoice = new SupplierInvoiceDTO();
        invoice.setName(node.get("name").asText());
        invoice.setPostingDate(node.get("posting_date").asText());
        invoice.setGrandTotal(node.get("grand_total").asDouble());
        invoice.setStatus(node.get("status").asText());
        invoice.setPaid("Paid".equalsIgnoreCase(invoice.getStatus()));
        
        if (node.has("supplier")) {
            invoice.setSupplier(node.get("supplier").asText());
        }
        if (node.has("due_date")) {
            invoice.setDueDate(node.get("due_date").asText());
        }
        if (node.has("outstanding_amount")) {
            invoice.setOutstandingAmount(node.get("outstanding_amount").asDouble());
        }
        return invoice;
    }

    public List<SupplierInvoiceDTO> getAllInvoices(String sid) {
        HttpHeaders headers = createHeaders(sid);
        String url = baseUrl + "/api/resource/Purchase Invoice?fields=[\"name\",\"posting_date\",\"grand_total\",\"status\",\"supplier\",\"due_date\",\"outstanding_amount\"]";

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, 
                HttpMethod.GET, 
                new HttpEntity<>(headers), 
                JsonNode.class
            );

            List<SupplierInvoiceDTO> invoices = new ArrayList<>();
            JsonNode data = response.getBody().get("data");
            if (data != null) {
                for (JsonNode node : data) {
                    invoices.add(mapToInvoiceDTO(node));
                }
            }
            return invoices;
        } catch (Exception e) {
            logger.error("Error fetching invoices", e);
            throw new RuntimeException("Failed to fetch invoices from ERPNext", e);
        }
    }

    public void payInvoice(String sid, String invoiceId, String supplier, double amount) {
        HttpHeaders headers = createHeaders(sid);
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            logger.info("Getting purchase invoice details for: {}", invoiceId);
            String invoiceUrl = baseUrl + "/api/resource/Purchase Invoice/" + invoiceId;
            
            ResponseEntity<JsonNode> invoiceResponse = restTemplate.exchange(
                invoiceUrl,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                JsonNode.class
            );
            
            JsonNode invoiceData = invoiceResponse.getBody().get("data");
            if (invoiceData == null) {
                throw new RuntimeException("Could not retrieve invoice data");
            }
            
            String creditTo = invoiceData.path("credit_to").asText();
            logger.info("Credit To account from invoice: {}", creditTo);
            
    
            String companyName = invoiceData.path("company").asText();
            String cashAccountUrl = baseUrl + "/api/resource/Company/" + companyName;
            
            ResponseEntity<JsonNode> companyResponse = restTemplate.exchange(
                cashAccountUrl,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                JsonNode.class
            );
            
            JsonNode companyData = companyResponse.getBody().get("data");
            String defaultCashAccount = companyData.path("default_cash_account").asText();
            
            if (defaultCashAccount == null || defaultCashAccount.isEmpty()) {
                String bankAccountsUrl = baseUrl + "/api/resource/Account?filters=[[\"Account\",\"account_type\",\"=\",\"Bank\"]]&fields=[\"name\"]";
                
                ResponseEntity<JsonNode> bankResponse = restTemplate.exchange(
                    bankAccountsUrl,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    JsonNode.class
                );
                
                JsonNode bankAccounts = bankResponse.getBody().path("data");
                if (bankAccounts.size() > 0) {
                    defaultCashAccount = bankAccounts.get(0).path("name").asText();
                } else {
                    throw new RuntimeException("No bank account found in the system");
                }
            }
            
            logger.info("Using cash/bank account: {}", defaultCashAccount);
            
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("doctype", "Payment Entry");
            payload.put("payment_type", "Pay");
            payload.put("party_type", "Supplier");
            payload.put("party", supplier);
            
            payload.put("paid_from", defaultCashAccount); 
            payload.put("paid_to", creditTo);
            payload.put("paid_amount", amount);
            payload.put("received_amount", amount);
            payload.put("source_exchange_rate", 1);
            payload.put("target_exchange_rate", 1);
            
            payload.put("company", companyName);
            
            ObjectNode reference = objectMapper.createObjectNode();
            reference.put("reference_doctype", "Purchase Invoice");
            reference.put("reference_name", invoiceId);
            reference.put("total_amount", amount);
            reference.put("outstanding_amount", amount);
            reference.put("allocated_amount", amount);

            payload.set("references", objectMapper.createArrayNode().add(reference));
            
            logger.info("Creating payment entry with payload: {}", payload);

            HttpEntity<String> entity = new HttpEntity<>(payload.toString(), headers);
            ResponseEntity<String> createResponse = restTemplate.exchange(
                baseUrl + "/api/resource/Payment Entry",
                HttpMethod.POST,
                entity,
                String.class
            );

            JsonNode result = objectMapper.readTree(createResponse.getBody());
            String paymentName = result.path("data").path("name").asText();
            logger.info("Payment Entry created: {}", paymentName);

            ObjectNode submitPayload = objectMapper.createObjectNode();
            submitPayload.put("docstatus", 1);

            HttpEntity<String> submitEntity = new HttpEntity<>(submitPayload.toString(), headers);
            restTemplate.exchange(
                baseUrl + "/api/resource/Payment Entry/" + paymentName,
                HttpMethod.PUT,
                submitEntity,
                String.class
            );

            logger.info("Payment Entry submitted: {}", paymentName);

        } catch (Exception e) {
            logger.error("Error processing payment for invoice: {}", invoiceId, e);
            throw new RuntimeException("Payment processing failed: " + e.getMessage(), e);
        }
    }
}