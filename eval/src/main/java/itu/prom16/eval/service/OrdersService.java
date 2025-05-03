package itu.prom16.eval.service;

import com.fasterxml.jackson.databind.JsonNode;
import itu.prom16.eval.dto.OrderDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrdersService {

    private static final Logger logger = LoggerFactory.getLogger(OrdersService.class);

    @Value("${erpnext.api.base-url}")
    private String baseUrl;

    public List<OrderDTO> getOrders(String supplierId, String sid) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Cookie", "sid=" + sid);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();

        List<OrderDTO> orders = new ArrayList<>();

        try {
            // Récupération des commandes
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    baseUrl + "/api/resource/Purchase Order?fields=[\"name\"]",
                    HttpMethod.GET,
                    entity,
                    JsonNode.class
            );

            JsonNode data = response.getBody().get("data");

            for (JsonNode node : data) {
                String orderName = node.get("name").asText();

                // Détails de la commande
                ResponseEntity<JsonNode> detailResponse = restTemplate.exchange(
                        baseUrl + "/api/resource/Purchase Order/" + orderName,
                        HttpMethod.GET,
                        entity,
                        JsonNode.class
                );

                JsonNode detail = detailResponse.getBody().get("data");
                String supplier = getSafeTextValue(detail, "supplier");

                if (supplier.equalsIgnoreCase(supplierId)) {
                    OrderDTO order = new OrderDTO();
                    order.setOrderId(orderName);
                    order.setStatus(getSafeTextValue(detail, "status"));

                    // Champ "received" basé sur per_received
                    double perReceived = detail.has("per_received") ? detail.get("per_received").asDouble() : 0.0;
                    order.setReceived(perReceived >= 99.9);

                    // Vérification du paiement via les factures liées
                    boolean isPaid = checkOrderPaymentStatus(orderName, entity, restTemplate);
                    order.setPaid(isPaid);

                    orders.add(order);
                }
            }

            return orders;

        } catch (Exception e) {
            logger.error("Erreur lors de la récupération des commandes pour le fournisseur : {}", supplierId, e);
            throw new RuntimeException("Erreur API ERPNext pour les commandes", e);
        }
    }

    private boolean checkOrderPaymentStatus(String orderName, HttpEntity<String> entity, RestTemplate restTemplate) {
        try {
            // Approach 2: Get all related invoices directly without filters
            // We'll use direct path to query for the specific purchase order
            String url = baseUrl + "/api/resource/Purchase Invoice";
    
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    JsonNode.class
            );
    
            JsonNode invoices = response.getBody().get("data");
            boolean hasRelatedInvoice = false;
            
            for (JsonNode invoice : invoices) {
                // Get detailed invoice to check its items
                String invoiceName = invoice.get("name").asText();
                ResponseEntity<JsonNode> detailResponse = restTemplate.exchange(
                        baseUrl + "/api/resource/Purchase Invoice/" + invoiceName,
                        HttpMethod.GET,
                        entity,
                        JsonNode.class
                );
                
                JsonNode detail = detailResponse.getBody().get("data");
                JsonNode items = detail.get("items");
                
                // Check if this invoice is related to our order
                for (JsonNode item : items) {
                    if (item.has("purchase_order") && 
                        orderName.equals(item.get("purchase_order").asText())) {
                        hasRelatedInvoice = true;
                        double outstanding = detail.get("outstanding_amount").asDouble(0.0);
                        if (outstanding > 0) {
                            return false;
                        }
                    }
                }
            }
            
            return hasRelatedInvoice; // Only return true if at least one related invoice exists and is paid
    
        } catch (Exception e) {
            logger.error("Erreur lors de la vérification du paiement pour la commande {}", orderName, e);
            return false;
        }
    }
    private String getSafeTextValue(JsonNode node, String fieldName) {
        return node.has(fieldName) && !node.get(fieldName).isNull()
                ? node.get(fieldName).asText()
                : "N/A";
    }
}