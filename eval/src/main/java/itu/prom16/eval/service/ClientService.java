package itu.prom16.eval.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import itu.prom16.eval.dto.ClientDTO;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ClientService {

    private static final Logger logger = LoggerFactory.getLogger(ClientService.class);

    @Value("${erpnext.api.base-url}")
    private String baseUrl;

    @Value("${erpnext.api.key}")
    private String apiKey;

    @Value("${erpnext.api.secret}")
    private String apiSecret;

    public List<ClientDTO> getClients(String sid) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Cookie", "sid=" + sid); 

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<JsonNode> response = new RestTemplate().exchange(
                baseUrl + "/api/v2/document/Customer",
                HttpMethod.GET,
                entity,
                JsonNode.class
            );

            return parseClientResponse(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Erreur API ERPNext", e);
        }
    }

    private List<ClientDTO> parseClientResponse(JsonNode responseBody) {
        List<ClientDTO> clients = new ArrayList<>();

        if (responseBody == null || !responseBody.has("data")) {
            logger.warn("Réponse ERPNext invalide ou données manquantes");
            return clients;
        }

        JsonNode dataNode = responseBody.get("data");
        if (!dataNode.isArray()) {
            logger.warn("Le noeud 'data' n'est pas un tableau");
            return clients;
        }

        for (JsonNode node : dataNode) {
            ClientDTO client = new ClientDTO();
            
            client.setCustomerName(getSafeTextValue(node, "name")); 
            client.setCustomerType(getSafeTextValue(node, "customer_type")); 
            
            clients.add(client);
        }

        return clients;
    }

    private String getSafeTextValue(JsonNode node, String fieldName) {
        return node.has(fieldName) ? node.get(fieldName).asText() : "N/A";
    }
}