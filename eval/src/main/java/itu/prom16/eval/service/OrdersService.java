package itu.prom16.eval.service;

import com.fasterxml.jackson.databind.JsonNode;
import itu.prom16.eval.dto.OrderDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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

        HttpEntity<String> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();

        List<OrderDTO> orders = new ArrayList<>();

        try {
            // Étape 1 : récupérer la liste des noms de commandes
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    baseUrl + "/api/resource/Purchase Order?fields=[\"name\"]",
                    HttpMethod.GET,
                    entity,
                    JsonNode.class
            );

            JsonNode data = response.getBody().get("data");

            for (JsonNode node : data) {
                String orderName = node.get("name").asText();

                // Étape 2 : détails de chaque commande
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

                    // Champ "paid" basé sur per_billed
                    double perBilled = detail.has("per_billed") ? detail.get("per_billed").asDouble() : 0.0;
                    order.setPaid(perBilled >= 99.9);

                    orders.add(order);
                }
            }

            return orders;

        } catch (Exception e) {
            logger.error("Erreur lors de la récupération des commandes pour le fournisseur : {}", supplierId, e);
            throw new RuntimeException("Erreur API ERPNext pour les commandes", e);
        }
    }

    private String getSafeTextValue(JsonNode node, String fieldName) {
        return node.has(fieldName) && !node.get(fieldName).isNull()
                ? node.get(fieldName).asText()
                : "N/A";
    }
}
