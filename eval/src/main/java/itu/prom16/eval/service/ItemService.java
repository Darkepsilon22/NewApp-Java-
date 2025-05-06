package itu.prom16.eval.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ItemService {

    private static final Logger logger = LoggerFactory.getLogger(ItemService.class);

    @Value("${erpnext.api.base-url}")
    private String baseUrl;

   
    public void updatePriceListRate(String itemCode, double newPrice, String sid) {
        updatePriceListRate(itemCode, newPrice, sid, null);
    }
    
    public void updatePriceListRate(String itemCode, double newPrice, String sid, String rfqId) {
        logger.debug("Updating price for item {} to {} for RFQ {}", itemCode, newPrice, rfqId);
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("Cookie", "sid=" + sid);
            
            String supplierId = getSupplierIdFromSession(sid);
            if (supplierId == null || supplierId.isEmpty()) {
                logger.error("No supplier ID found for the current session");
                throw new RuntimeException("No supplier ID found for the current session");
            }
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("doctype", "Supplier Quotation");
            requestBody.put("supplier", supplierId);
            
            if (rfqId != null && !rfqId.trim().isEmpty()) {
                rfqId = rfqId.trim().toUpperCase();
                requestBody.put("request_for_quotation", rfqId);
                logger.info("Liaison au RFQ (document level): {}", rfqId);
            }
            
            List<Map<String, Object>> items = new ArrayList<>();
            Map<String, Object> item = new HashMap<>();
            item.put("item_code", itemCode);
            item.put("qty", 1);
            item.put("rate", newPrice);
            
            if (rfqId != null && !rfqId.trim().isEmpty()) {
                item.put("request_for_quotation", rfqId);
                logger.info("Liaison au RFQ (item level): {}", rfqId);
            }
            
            items.add(item);
            requestBody.put("items", items);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            RestTemplate restTemplate = new RestTemplate();
            logger.debug("Payload envoyé à ERPNext: {}", requestBody);
            
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl + "/api/resource/Supplier Quotation",
                HttpMethod.POST,
                entity,
                JsonNode.class
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                String quotationName = response.getBody().get("data").get("name").asText();
                logger.info("Devis créé avec succès: {}", quotationName);
                
                if (rfqId != null && !rfqId.isEmpty()) {
                    ResponseEntity<JsonNode> verifyResponse = restTemplate.exchange(
                        baseUrl + "/api/resource/Supplier Quotation/" + quotationName,
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        JsonNode.class
                    );
                    
                    JsonNode createdQuote = verifyResponse.getBody().get("data");
                    
                    String linkedRfq = createdQuote.has("request_for_quotation") && 
                                      !createdQuote.get("request_for_quotation").isNull() ?
                                       createdQuote.get("request_for_quotation").asText("") : "";
                    
                    logger.info("Vérification - RFQ niveau document: '{}'", linkedRfq);
                    
                    boolean itemLevelRfqFound = false;
                    if (createdQuote.has("items") && createdQuote.get("items").isArray()) {
                        for (JsonNode itemNode : createdQuote.get("items")) {
                            if (itemNode.has("request_for_quotation") && 
                                !itemNode.get("request_for_quotation").isNull()) {
                                
                                String itemRfq = itemNode.get("request_for_quotation").asText("");
                                if (!itemRfq.isEmpty()) {
                                    logger.info("Vérification - RFQ niveau item: '{}'", itemRfq);
                                    if (itemRfq.equalsIgnoreCase(rfqId)) {
                                        itemLevelRfqFound = true;
                                    }
                                }
                            }
                        }
                    }
                    
                    if ((linkedRfq.isEmpty() || !linkedRfq.equalsIgnoreCase(rfqId)) && !itemLevelRfqFound) {
                        logger.warn("La liaison au RFQ a échoué, tentative de correction...");
                        
                        try {
                            Map<String, Object> updateBody = new HashMap<>();
                            updateBody.put("request_for_quotation", rfqId);
                            
                            HttpEntity<Map<String, Object>> updateEntity = new HttpEntity<>(updateBody, headers);
                            
                            restTemplate.exchange(
                                baseUrl + "/api/resource/Supplier Quotation/" + quotationName,
                                HttpMethod.PUT,
                                updateEntity,
                                JsonNode.class
                            );
                            
                            logger.info("Correction du lien RFQ (niveau document) effectuée pour {}", quotationName);
                            
                            if (createdQuote.has("items") && createdQuote.get("items").isArray()) {
                                for (JsonNode itemNode : createdQuote.get("items")) {
                                    String itemName = itemNode.get("name").asText();
                                    
                                    Map<String, Object> itemUpdateBody = new HashMap<>();
                                    itemUpdateBody.put("request_for_quotation", rfqId);
                                    
                                    HttpEntity<Map<String, Object>> itemUpdateEntity = new HttpEntity<>(itemUpdateBody, headers);
                                    
                                    restTemplate.exchange(
                                        baseUrl + "/api/resource/Supplier Quotation Item/" + itemName,
                                        HttpMethod.PUT,
                                        itemUpdateEntity,
                                        JsonNode.class
                                    );
                                    
                                    logger.info("Correction du lien RFQ (niveau item) effectuée pour {}", itemName);
                                }
                            }
                        } catch (Exception updateEx) {
                            logger.error("Échec de la correction du lien RFQ", updateEx);
                            throw new RuntimeException("Échec de la liaison au RFQ: " + updateEx.getMessage());
                        }
                    } else {
                        logger.info("Vérification OK: Devis {} bien lié au RFQ {}", 
                                   quotationName, linkedRfq.isEmpty() ? "(niveau item)" : linkedRfq);
                    }
                }
                
                submitQuotation(quotationName, headers, new RestTemplate());
                logger.info("Devis {} soumis automatiquement après création", quotationName);
            } else {
                logger.error("Erreur lors de la création du devis: {}", response.getBody());
                throw new RuntimeException("Échec de la création du devis");
            }
        } catch (Exception e) {
            logger.error("Échec critique de la création du devis", e);
            throw new RuntimeException("Erreur technique: " + e.getMessage());
        }
    }
    
  
    public void updateQuotationItemPrice(String quotationName, String itemCode, double newPrice, String sid, String rfqId) {
        logger.debug("Updating price for item {} in quotation {} to {}", itemCode, quotationName, newPrice);
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("Cookie", "sid=" + sid);
            RestTemplate restTemplate = new RestTemplate();
            
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl + "/api/resource/Supplier Quotation/" + quotationName,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                JsonNode.class);
            
            JsonNode quoteData = response.getBody().get("data");
            int docStatus = quoteData.has("docstatus") ? quoteData.get("docstatus").asInt() : 0;
            String supplier = quoteData.has("supplier") ? quoteData.get("supplier").asText() : "";
            
            if (docStatus == 1) {
                logger.info("Quotation {} is already submitted. Creating a new one.", quotationName);
                updatePriceListRate(itemCode, newPrice, sid, rfqId);
                return;
            }
            
            JsonNode items = quoteData.get("items");
            String itemRowName = null;
            
            if (items != null && items.isArray()) {
                for (JsonNode item : items) {
                    if (itemCode.equals(getSafeTextValue(item, "item_code"))) {
                        itemRowName = getSafeTextValue(item, "name");
                        break;
                    }
                }
            }
            
            if (itemRowName != null) {
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("rate", newPrice);
                
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
                
                try {
                    restTemplate.exchange(
                        baseUrl + "/api/resource/Supplier Quotation Item/" + itemRowName,
                        HttpMethod.PUT,
                        entity,
                        Object.class);
                    
                    if (rfqId != null && !rfqId.isEmpty()) {
                        Map<String, Object> quotationUpdateBody = new HashMap<>();
                        quotationUpdateBody.put("request_for_quotation", rfqId);
                        
                        HttpEntity<Map<String, Object>> quotationEntity = new HttpEntity<>(quotationUpdateBody, headers);
                        
                        restTemplate.exchange(
                            baseUrl + "/api/resource/Supplier Quotation/" + quotationName,
                            HttpMethod.PUT,
                            quotationEntity,
                            Object.class);
                        
                        logger.info("Updated RFQ link in quotation {} to {}", quotationName, rfqId);
                    }
                    
                    logger.debug("Price updated successfully for item {} in quotation {}", itemCode, quotationName);
                    
                    submitQuotation(quotationName, headers, restTemplate);
                    logger.info("Devis {} soumis automatiquement après mise à jour du prix", quotationName);
                } catch (HttpClientErrorException ex) {
                    logger.warn("Failed to update existing quotation: {}. Creating a new one.", ex.getMessage());
                    updatePriceListRate(itemCode, newPrice, sid, rfqId);
                }
            } else {
                logger.warn("Item {} not found in quotation {}. Creating a new one.", itemCode, quotationName);
                updatePriceListRate(itemCode, newPrice, sid, rfqId);
            }
        } catch (Exception e) {
            logger.error("Error updating price for item " + itemCode + " in quotation " + quotationName, e);
            throw new RuntimeException("Error updating price: " + e.getMessage(), e);
        }
    }
    
    public void updateQuotationItemPrice(String quotationName, String itemCode, double newPrice, String sid) {
        updateQuotationItemPrice(quotationName, itemCode, newPrice, sid, null);
    }
    
    private void submitQuotation(String quotationName, 
                            HttpHeaders headers, RestTemplate restTemplate) {
    try {
        ResponseEntity<JsonNode> getResponse = restTemplate.exchange(
            baseUrl + "/api/resource/Supplier Quotation/" + quotationName,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            JsonNode.class
        );
        
        if (!getResponse.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Échec de récupération du devis - Statut HTTP " + getResponse.getStatusCode());
        }
        
        JsonNode docData = getResponse.getBody().get("data");
        int docStatus = docData.has("docstatus") ? docData.get("docstatus").asInt() : 0;
        
        if (docStatus == 1) {
            logger.info("Le devis {} est déjà soumis, aucune action nécessaire", quotationName);
            return;
        }
        
        String url = baseUrl + "/api/method/frappe.client.submit";
        
        Map<String, Object> doc = new HashMap<>();
        
        doc.put("doctype", "Supplier Quotation");
        doc.put("name", quotationName);
        
        if (docData.has("naming_series")) doc.put("naming_series", docData.get("naming_series").asText());
        if (docData.has("supplier")) doc.put("supplier", docData.get("supplier").asText());
        if (docData.has("company")) doc.put("company", docData.get("company").asText());
        if (docData.has("transaction_date")) doc.put("transaction_date", docData.get("transaction_date").asText());
        if (docData.has("currency")) doc.put("currency", docData.get("currency").asText());
        if (docData.has("conversion_rate")) doc.put("conversion_rate", docData.get("conversion_rate").asDouble());
        
        if (docData.has("modified")) doc.put("modified", docData.get("modified").asText());
        
        if (docData.has("items") && docData.get("items").isArray()) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (JsonNode itemNode : docData.get("items")) {
                Map<String, Object> item = new HashMap<>();
                
                itemNode.fields().forEachRemaining(entry -> {
                    String key = entry.getKey();
                    JsonNode value = entry.getValue();
                    
                    if (value.isTextual()) item.put(key, value.asText());
                    else if (value.isNumber()) {
                        if (value.isInt()) item.put(key, value.asInt());
                        else if (value.isLong()) item.put(key, value.asLong());
                        else if (value.isDouble()) item.put(key, value.asDouble());
                    }
                    else if (value.isBoolean()) item.put(key, value.asBoolean());
                    else if (value.isNull()) item.put(key, null);
                });
                
                items.add(item);
            }
            doc.put("items", items);
        }
        
        docData.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            
            if (!doc.containsKey(key) && !value.isArray() && !value.isObject()) {
                if (value.isTextual()) doc.put(key, value.asText());
                else if (value.isNumber()) {
                    if (value.isInt()) doc.put(key, value.asInt());
                    else if (value.isLong()) doc.put(key, value.asLong());
                    else if (value.isDouble()) doc.put(key, value.asDouble());
                }
                else if (value.isBoolean()) doc.put(key, value.asBoolean());
                else if (value.isNull()) doc.put(key, null);
            }
        });
        
        Map<String, Object> body = new HashMap<>();
        body.put("doc", doc);
        
        logger.debug("Payload pour soumission du devis {}: {}", quotationName, body);
        
        try {
            Thread.sleep(500);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        
        ResponseEntity<JsonNode> response = restTemplate.exchange(
            url,
            HttpMethod.POST,
            entity,
            JsonNode.class
        );
        
        if (response.getStatusCode().is2xxSuccessful()) {
            JsonNode responseBody = response.getBody();
            if (responseBody != null) {
                logger.info("Soumission réussie du devis {}", quotationName);
            } else {
                logger.warn("Réponse de soumission anormale pour le devis {}", quotationName);
            }
        } else {
            throw new RuntimeException("Échec de soumission - Statut HTTP " + response.getStatusCode());
        }
        
    } catch (Exception e) {
        logger.error("Échec critique de soumission pour {}", quotationName, e);
        throw new RuntimeException("Soumission impossible : " + e.getMessage());
    }
}
    private String getSupplierIdFromSession(String sid) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Cookie", "sid=" + sid);
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            RestTemplate restTemplate = new RestTemplate();
            
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl + "/api/method/frappe.auth.get_logged_user",
                HttpMethod.GET,
                entity,
                JsonNode.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String username = response.getBody().get("message").asText();
                
                ResponseEntity<JsonNode> userResponse = restTemplate.exchange(
                    baseUrl + "/api/resource/User/" + username,
                    HttpMethod.GET,
                    entity,
                    JsonNode.class
                );
                
                if (userResponse.getStatusCode().is2xxSuccessful() && userResponse.getBody() != null) {
                    JsonNode userData = userResponse.getBody().get("data");
                    
                    if (userData.has("represent_as_supplier") && userData.get("represent_as_supplier").asBoolean()) {
                        if (userData.has("supplier") && !userData.get("supplier").isNull()) {
                            return userData.get("supplier").asText();
                        }
                    }
                }
                
                ResponseEntity<JsonNode> suppliersResponse = restTemplate.exchange(
                    baseUrl + "/api/resource/Supplier?limit=1",
                    HttpMethod.GET,
                    entity,
                    JsonNode.class
                );
                
                if (suppliersResponse.getStatusCode().is2xxSuccessful() && 
                    suppliersResponse.getBody() != null &&
                    suppliersResponse.getBody().has("data") &&
                    suppliersResponse.getBody().get("data").isArray() &&
                    suppliersResponse.getBody().get("data").size() > 0) {
                    
                    return suppliersResponse.getBody().get("data").get(0).get("name").asText();
                }
            }
            
            return null;
        } catch (Exception e) {
            logger.error("Error getting supplier ID from session", e);
            return null;
        }
    }
    
    private String getSafeTextValue(JsonNode node, String fieldName) {
        return node.has(fieldName) ? node.get(fieldName).asText() : "";
    }
    
    public void updatePriceListRate(String itemCode, double newPrice) {
        String sid = getCurrentSessionId();
        updatePriceListRate(itemCode, newPrice, sid);
    }
    
    private String getCurrentSessionId() {
        return "your_session_id_from_context";
    }
}