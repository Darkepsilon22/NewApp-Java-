package itu.prom16.eval.service;

import com.fasterxml.jackson.databind.JsonNode;
import itu.prom16.eval.dto.OrderDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
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
            // Étape 1 : récupérer les noms des Purchase Orders
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    baseUrl + "/api/resource/Purchase Order?fields=[\"name\"]",
                    HttpMethod.GET,
                    entity,
                    JsonNode.class);

            JsonNode data = response.getBody().get("data");

            for (JsonNode node : data) {
                String orderName = node.get("name").asText();

                // Étape 2 : détails complets
                ResponseEntity<JsonNode> detailResponse = restTemplate.exchange(
                        baseUrl + "/api/resource/Purchase Order/" + orderName,
                        HttpMethod.GET,
                        entity,
                        JsonNode.class);

                JsonNode detail = detailResponse.getBody().get("data");

                String supplier = getSafeTextValue(detail, "supplier");
                if (supplier.equalsIgnoreCase(supplierId)) {
                    OrderDTO order = new OrderDTO();
                    order.setOrderId(orderName);
                    order.setStatus(getSafeTextValue(detail, "status"));

                    // reçu : si status contient "To Receive" ou "Completed"
                    String status = getSafeTextValue(detail, "status");
                    order.setReceived(status.contains("Receive") || status.contains("Completed"));

                    // payé : si per_billed == 100
                    boolean paid = detail.has("per_billed") && detail.get("per_billed").asDouble() >= 99.9;
                    order.setPaid(paid);

                    orders.add(order);
                }
            }

            return orders;

        } catch (Exception e) {
            logger.error("Erreur lors de la récupération des commandes pour le fournisseur : " + supplierId, e);
            throw new RuntimeException("Erreur API ERPNext pour les commandes", e);
        }
    }

    private List<OrderDTO> parseOrders(JsonNode responseBody) {
        List<OrderDTO> orders = new ArrayList<>();

        if (responseBody == null || !responseBody.has("data")) {
            logger.warn("Réponse ERPNext invalide ou données manquantes pour les commandes");
            return orders;
        }

        JsonNode dataNode = responseBody.get("data");
        if (!dataNode.isArray()) {
            logger.warn("Le noeud 'data' n'est pas un tableau pour les commandes");
            return orders;
        }

        for (JsonNode node : dataNode) {
            OrderDTO order = new OrderDTO();
            order.setOrderId(getSafeTextValue(node, "id"));
            order.setStatus(getSafeTextValue(node, "status"));
            order.setReceived(node.has("received") && node.get("received").asBoolean());
            order.setPaid(node.has("paid") && node.get("paid").asBoolean());

            orders.add(order);
        }

        return orders;
    }

    private String getSafeTextValue(JsonNode node, String fieldName) {
        return node.has(fieldName) ? node.get(fieldName).asText() : "N/A";
    }
}
