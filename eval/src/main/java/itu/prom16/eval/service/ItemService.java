package itu.prom16.eval.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class ItemService {

    private static final Logger logger = LoggerFactory.getLogger(ItemService.class);

    @Value("${erpnext.api.base-url}")
    private String baseUrl;

    /**
     * Update item price by creating or updating a Supplier Quotation
     */
    public void updatePriceListRate(String itemCode, double newPrice, String sid) {
        logger.debug("Updating price for item {} to {}", itemCode, newPrice);
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("Cookie", "sid=" + sid);
            
            // First, get the supplier ID associated with this session
            String supplierId = getSupplierIdFromSession(sid);
            if (supplierId == null || supplierId.isEmpty()) {
                logger.error("No supplier ID found for the current session");
                throw new RuntimeException("No supplier ID found for the current session");
            }
            
            // Create a supplier quotation with the updated price
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("doctype", "Supplier Quotation");
            requestBody.put("supplier", supplierId);
            requestBody.put("items", new Object[]{
                Map.of(
                    "item_code", itemCode,
                    "qty", 1,
                    "rate", newPrice
                )
            });
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            RestTemplate restTemplate = new RestTemplate();
            
            // Create new supplier quotation
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl + "/api/resource/Supplier Quotation",
                HttpMethod.POST,
                entity,
                JsonNode.class
            );
            
            // Submit the quotation if creation was successful
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String quotationName = response.getBody().get("data").get("name").asText();
                submitQuotation(quotationName, sid);
            }
            
            logger.debug("Price updated successfully for {} via supplier quotation", itemCode);
        } catch (Exception e) {
            logger.error("Error updating price for item " + itemCode, e);
            throw new RuntimeException("Error updating price: " + e.getMessage(), e);
        }
    }
    
    /**
     * Update price in an existing Supplier Quotation Item or create a new one if update fails
     */
    public void updateQuotationItemPrice(String quotationName, String itemCode, double newPrice, String sid) {
        logger.debug("Updating price for item {} in quotation {} to {}", itemCode, quotationName, newPrice);
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("Cookie", "sid=" + sid);
            RestTemplate restTemplate = new RestTemplate();
            
            // Get the quotation to check if it's already submitted and find the specific item row
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl + "/api/resource/Supplier Quotation/" + quotationName,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                JsonNode.class);
            
            JsonNode quoteData = response.getBody().get("data");
            int docStatus = quoteData.has("docstatus") ? quoteData.get("docstatus").asInt() : 0;
            String supplier = quoteData.has("supplier") ? quoteData.get("supplier").asText() : "";
            
            // If quotation is already submitted (docstatus = 1), create a new one
            if (docStatus == 1) {
                logger.info("Quotation {} is already submitted. Creating a new one.", quotationName);
                updatePriceListRate(itemCode, newPrice, sid);
                return;
            }
            
            // If not submitted, try to update the existing item
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
                // Update the specific item row in the Supplier Quotation Item table
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("rate", newPrice);
                
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
                
                try {
                    restTemplate.exchange(
                        baseUrl + "/api/resource/Supplier Quotation Item/" + itemRowName,
                        HttpMethod.PUT,
                        entity,
                        Object.class);
                    
                    logger.debug("Price updated successfully for item {} in quotation {}", itemCode, quotationName);
                } catch (HttpClientErrorException ex) {
                    // If update fails, create a new quotation
                    logger.warn("Failed to update existing quotation: {}. Creating a new one.", ex.getMessage());
                    updatePriceListRate(itemCode, newPrice, sid);
                }
            } else {
                // Item not found in quotation, create a new one
                logger.warn("Item {} not found in quotation {}. Creating a new one.", itemCode, quotationName);
                updatePriceListRate(itemCode, newPrice, sid);
            }
        } catch (Exception e) {
            logger.error("Error updating price for item " + itemCode + " in quotation " + quotationName, e);
            throw new RuntimeException("Error updating price: " + e.getMessage(), e);
        }
    }
    
    /**
     * Helper method to submit a quotation
     */
    private void submitQuotation(String quotationName, String sid) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("Cookie", "sid=" + sid);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("doctype", "Supplier Quotation");
            requestBody.put("name", quotationName);
            requestBody.put("docstatus", 1); // 1 means submit
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            RestTemplate restTemplate = new RestTemplate();
            
            // Submit the quotation
            restTemplate.exchange(
                baseUrl + "/api/resource/Supplier Quotation/" + quotationName,
                HttpMethod.PUT,
                entity,
                Object.class
            );
            
            logger.debug("Quotation {} submitted successfully", quotationName);
        } catch (Exception e) {
            logger.error("Error submitting quotation " + quotationName, e);
            // Don't throw exception here, as the price was already updated
        }
    }
    
    /**
     * Get supplier ID from the current session
     */
    private String getSupplierIdFromSession(String sid) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Cookie", "sid=" + sid);
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            RestTemplate restTemplate = new RestTemplate();
            
            // Get user's roles and info
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl + "/api/method/frappe.auth.get_logged_user",
                HttpMethod.GET,
                entity,
                JsonNode.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String username = response.getBody().get("message").asText();
                
                // Get user details to check if they're associated with a supplier
                ResponseEntity<JsonNode> userResponse = restTemplate.exchange(
                    baseUrl + "/api/resource/User/" + username,
                    HttpMethod.GET,
                    entity,
                    JsonNode.class
                );
                
                if (userResponse.getStatusCode().is2xxSuccessful() && userResponse.getBody() != null) {
                    JsonNode userData = userResponse.getBody().get("data");
                    
                    // Check if there's a supplier link
                    if (userData.has("represent_as_supplier") && userData.get("represent_as_supplier").asBoolean()) {
                        if (userData.has("supplier") && !userData.get("supplier").isNull()) {
                            return userData.get("supplier").asText();
                        }
                    }
                }
                
                // If we get here, try fetching the first supplier from the list
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
    
    // Overloaded method if sid is managed elsewhere
    public void updatePriceListRate(String itemCode, double newPrice) {
        // Get the session ID from context or session management service
        String sid = getCurrentSessionId();
        updatePriceListRate(itemCode, newPrice, sid);
    }
    
    // This method should be replaced with your actual session management
    private String getCurrentSessionId() {
        // Implement your actual session ID retrieval logic here
        return "your_session_id_from_context";
    }
}