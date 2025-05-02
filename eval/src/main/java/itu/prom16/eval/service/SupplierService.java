package itu.prom16.eval.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;

import itu.prom16.eval.dto.SupplierDTO;

@Service
public class SupplierService {
    private static final Logger logger = LoggerFactory.getLogger(ClientService.class);

    @Value("${erpnext.api.base-url}")
    private String baseUrl;

    @Value("${erpnext.api.key}")
    private String apiKey;

    @Value("${erpnext.api.secret}")
    private String apiSecret;

    public List<SupplierDTO> getSuppliers(String sid) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Cookie", "sid=" + sid);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<JsonNode> response = new RestTemplate().exchange(
                    baseUrl + "/api/v2/document/Supplier",
                    HttpMethod.GET,
                    entity,
                    JsonNode.class);

            return parseSupplierResponse(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Erreur API ERPNext", e);
        }
    }

    private List<SupplierDTO> parseSupplierResponse(JsonNode responseBody) {
        List<SupplierDTO> suppliers = new ArrayList<>();

        if (responseBody == null || !responseBody.has("data")) {
            logger.warn("Réponse ERPNext invalide ou données manquantes");
            return suppliers;
        }

        logger.info("Réponse JSON des fournisseurs : {}", responseBody.toString());

        JsonNode dataNode = responseBody.get("data");
        if (!dataNode.isArray()) {
            logger.warn("Le noeud 'data' n'est pas un tableau");
            return suppliers;
        }

        for (JsonNode node : dataNode) {
            SupplierDTO supplier = new SupplierDTO();

            // Utilisation du champ "name" pour le nom du fournisseur
            supplier.setSupplierName(getSafeTextValue(node, "name"));

            // Vérifiez si un autre champ peut être utilisé pour le type
            supplier.setSupplierType("Non spécifié"); // Valeur par défaut si "type" n'existe pas

            suppliers.add(supplier);
        }

        return suppliers;
    }

    private String getSafeTextValue(JsonNode node, String fieldName) {
        return node.has(fieldName) ? node.get(fieldName).asText() : "N/A";
    }
}
