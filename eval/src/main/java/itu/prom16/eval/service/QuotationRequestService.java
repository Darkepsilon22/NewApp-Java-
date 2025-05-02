package itu.prom16.eval.service;

import com.fasterxml.jackson.databind.JsonNode;
import itu.prom16.eval.dto.QuotationRequestDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class QuotationRequestService {

    private static final Logger logger = LoggerFactory.getLogger(QuotationRequestService.class);

    @Value("${erpnext.api.base-url}")
    private String baseUrl;

    public List<QuotationRequestDTO> getQuotationRequests(String supplierId, String sid) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Cookie", "sid=" + sid);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        List<QuotationRequestDTO> filteredQuotations = new ArrayList<>();
    
        try {
            // Step 1: Get all RFQs
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    baseUrl + "/api/resource/Request for Quotation?fields=[\"name\"]",
                    HttpMethod.GET,
                    entity,
                    JsonNode.class);
    
            JsonNode data = response.getBody().get("data");
    
            for (JsonNode node : data) {
                String rfqName = node.get("name").asText();
                
                // Step 2: Get detail for each RFQ
                ResponseEntity<JsonNode> detailResponse = restTemplate.exchange(
                        baseUrl + "/api/resource/Request for Quotation/" + rfqName,
                        HttpMethod.GET,
                        entity,
                        JsonNode.class);
    
                JsonNode detail = detailResponse.getBody().get("data");
    
                // Check if this RFQ is for our supplier
                String supplier = "";
                JsonNode suppliersArray = detail.get("suppliers");
                if (suppliersArray != null && suppliersArray.isArray() && suppliersArray.size() > 0) {
                    supplier = getSafeTextValue(suppliersArray.get(0), "supplier");
                }
    
                if (supplier.equalsIgnoreCase(supplierId)) {
                    String itemCode = "";

                    JsonNode items = detail.get("items");
                    if (items != null && items.isArray() && items.size() > 0) {
                        itemCode = getSafeTextValue(items.get(0), "item_code");
                    }

                    QuotationRequestDTO dto = new QuotationRequestDTO();
                    dto.setRequestId(rfqName);
                    dto.setDescription("Demande du " + getSafeTextValue(detail, "transaction_date"));
                    dto.setItemCode(itemCode);
                    dto.setRate(0); // Rate will be fetched in getQuotationDetails
    
                    filteredQuotations.add(dto);
                }
            }
            return filteredQuotations;
    
        } catch (Exception e) {
            logger.error("Erreur API pour les RFQ", e);
            throw new RuntimeException("Erreur lors de la récupération des demandes de devis", e);
        }
    }
    
    public List<QuotationRequestDTO> getQuotationDetails(String rfqId, String supplierId, String sid) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Cookie", "sid=" + sid);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        List<QuotationRequestDTO> quotationDetails = new ArrayList<>();
        
        try {
            // Get Supplier Quotations for this supplier and RFQ
            String filters = "[[\"supplier\",\"=\",\"" + supplierId + "\"]]";
            ResponseEntity<JsonNode> quotesResponse = restTemplate.exchange(
                baseUrl + "/api/resource/Supplier Quotation?filters=" + filters,
                HttpMethod.GET,
                entity,
                JsonNode.class);
            
            JsonNode quotesData = quotesResponse.getBody().get("data");
            
            if (quotesData != null && quotesData.isArray()) {
                for (JsonNode quoteNode : quotesData) {
                    String quoteName = quoteNode.get("name").asText();
                    
                    // Get the details of this quotation
                    ResponseEntity<JsonNode> quoteDetailResponse = restTemplate.exchange(
                        baseUrl + "/api/resource/Supplier Quotation/" + quoteName,
                        HttpMethod.GET,
                        entity,
                        JsonNode.class);
                    
                    JsonNode quoteDetail = quoteDetailResponse.getBody().get("data");
                    JsonNode quoteItems = quoteDetail.get("items");
                    int docStatus = quoteDetail.has("docstatus") ? quoteDetail.get("docstatus").asInt() : 0;
                    
                    // Check if this quotation is associated with our RFQ
                    String quotationRfq = getSafeTextValue(quoteDetail, "request_for_quotation");
                    
                    if (quotationRfq.equals(rfqId) || quotationRfq.isEmpty()) { // Include if matches RFQ or if not linked
                        if (quoteItems != null && quoteItems.isArray()) {
                            for (JsonNode quoteItem : quoteItems) {
                                String quoteItemCode = getSafeTextValue(quoteItem, "item_code");
                                double rate = quoteItem.has("rate") ? quoteItem.get("rate").asDouble() : 0.0;
                                
                                // Get item name
                                String itemName = "N/A";
                                try {
                                    ResponseEntity<JsonNode> itemResponse = restTemplate.exchange(
                                        baseUrl + "/api/resource/Item/" + quoteItemCode,
                                        HttpMethod.GET,
                                        entity,
                                        JsonNode.class);
                                    
                                    JsonNode itemData = itemResponse.getBody().get("data");
                                    itemName = getSafeTextValue(itemData, "item_name");
                                } catch (Exception ex) {
                                    logger.warn("Impossible de récupérer le nom de l'article: " + quoteItemCode, ex);
                                }
                                
                                QuotationRequestDTO dto = new QuotationRequestDTO();
                                dto.setSupplierQuotationName(quoteName);
                                dto.setItemCode(quoteItemCode);
                                dto.setItemName(itemName);
                                dto.setRate(rate);
                                dto.setRequestId(rfqId);
                                // Add document status to indicate if quotation is editable
                                dto.setDocStatus(docStatus);
                                
                                quotationDetails.add(dto);
                                logger.debug("Found item {} in quotation {}: rate={}, docstatus={}", 
                                    quoteItemCode, quoteName, rate, docStatus);
                            }
                        }
                    }
                }
            }
            
            // If no quotations found, create a placeholder for a new one
            if (quotationDetails.isEmpty()) {
                // Get the RFQ details to create a new quotation based on it
                ResponseEntity<JsonNode> rfqResponse = restTemplate.exchange(
                    baseUrl + "/api/resource/Request for Quotation/" + rfqId,
                    HttpMethod.GET,
                    entity,
                    JsonNode.class);
                
                JsonNode rfqData = rfqResponse.getBody().get("data");
                JsonNode rfqItems = rfqData.get("items");
                
                if (rfqItems != null && rfqItems.isArray() && rfqItems.size() > 0) {
                    // Create a placeholder for the first item
                    String itemCode = getSafeTextValue(rfqItems.get(0), "item_code");
                    
                    // Get item name
                    String itemName = "N/A";
                    try {
                        ResponseEntity<JsonNode> itemResponse = restTemplate.exchange(
                            baseUrl + "/api/resource/Item/" + itemCode,
                            HttpMethod.GET,
                            entity,
                            JsonNode.class);
                        
                        JsonNode itemData = itemResponse.getBody().get("data");
                        itemName = getSafeTextValue(itemData, "item_name");
                    } catch (Exception ex) {
                        logger.warn("Impossible de récupérer le nom de l'article: " + itemCode, ex);
                    }
                    
                    // Create a placeholder entry
                    QuotationRequestDTO dto = new QuotationRequestDTO();
                    dto.setSupplierQuotationName("Nouveau devis");
                    dto.setItemCode(itemCode);
                    dto.setItemName(itemName);
                    dto.setRate(0.0);
                    dto.setRequestId(rfqId);
                    dto.setDocStatus(0); // New quotation is editable
                    
                    quotationDetails.add(dto);
                }
            }
            
            return quotationDetails;
            
        } catch (Exception e) {
            logger.error("Erreur API pour les details du supplier quotation", e);
            throw new RuntimeException("Erreur lors de la récupération des détails du devis", e);
        }
    }
    
    public void updateQuotationPrice(String quotationName, String itemCode, double newPrice, String sid) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("Cookie", "sid=" + sid);
            RestTemplate restTemplate = new RestTemplate();
            
            if ("Nouveau devis".equals(quotationName)) {
                // We need to create a new quotation
                // This should be implemented through the ItemService.updatePriceListRate method
                logger.info("Creation of new supplier quotation will be handled by ItemService");
                return;
            }
            
            // First, get the quotation to check if it's already submitted
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl + "/api/resource/Supplier Quotation/" + quotationName,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                JsonNode.class);
            
            JsonNode quoteData = response.getBody().get("data");
            int docStatus = quoteData.has("docstatus") ? quoteData.get("docstatus").asInt() : 0;
            
            // If quotation is already submitted, create a new one instead of updating
            if (docStatus == 1) {
                logger.info("Quotation {} is already submitted. Creating a new one.", quotationName);
                // Will be handled by ItemService.updatePriceListRate
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
                
                restTemplate.exchange(
                    baseUrl + "/api/resource/Supplier Quotation Item/" + itemRowName,
                    HttpMethod.PUT,
                    entity,
                    Object.class);
                
                logger.debug("Price updated successfully for item {} in quotation {} to {}", 
                    itemCode, quotationName, newPrice);
            } else {
                logger.error("Item {} not found in quotation {}", itemCode, quotationName);
                throw new RuntimeException("Item not found in quotation");
            }
        } catch (Exception e) {
            logger.error("Error updating price for item " + itemCode + " in quotation " + quotationName, e);
            throw new RuntimeException("Error updating price: " + e.getMessage(), e);
        }
    }

    private String getSafeTextValue(JsonNode node, String fieldName) {
        return node.has(fieldName) ? node.get(fieldName).asText() : "";
    }
}