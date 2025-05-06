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
            String filters = "[[\"supplier\",\"=\",\"" + supplierId + "\"]]";
            ResponseEntity<JsonNode> quotesResponse = restTemplate.exchange(
                baseUrl + "/api/resource/Supplier Quotation?filters=" + filters,
                HttpMethod.GET,
                entity,
                JsonNode.class);
            
            JsonNode quotesData = quotesResponse.getBody().get("data");
            
            if (quotesData != null && quotesData.isArray()) {
                logger.info("Found {} supplier quotations for supplier {}", quotesData.size(), supplierId);
                
                for (JsonNode quoteNode : quotesData) {
                    String quoteName = quoteNode.get("name").asText();
                    
                    ResponseEntity<JsonNode> quoteDetailResponse = restTemplate.exchange(
                        baseUrl + "/api/resource/Supplier Quotation/" + quoteName,
                        HttpMethod.GET,
                        entity,
                        JsonNode.class);
                    
                    JsonNode quoteDetail = quoteDetailResponse.getBody().get("data");
                    JsonNode quoteItems = quoteDetail.get("items");
                    int docStatus = quoteDetail.has("docstatus") ? quoteDetail.get("docstatus").asInt() : 0;
                    
                    String quotationRfq = "";
                    if (quoteDetail.has("request_for_quotation") && !quoteDetail.get("request_for_quotation").isNull()) {
                        quotationRfq = quoteDetail.get("request_for_quotation").asText("");
                    }
                    
                    logger.debug("RFQ attendu : {} | RFQ lié : {}", rfqId, quotationRfq);
                    
                    boolean isMatch = false;
                    
                    if (quotationRfq != null && !quotationRfq.isEmpty() && 
                        quotationRfq.equalsIgnoreCase(rfqId.trim())) {
                        isMatch = true;
                        logger.info("Devis {} lié au RFQ {} (niveau document)", quoteName, rfqId);
                    }
                    else if (quoteItems != null && quoteItems.isArray()) {
                        for (JsonNode item : quoteItems) {
                            if (item.has("request_for_quotation") && 
                                !item.get("request_for_quotation").isNull()) {
                                
                                String itemRfq = item.get("request_for_quotation").asText("");
                                if (!itemRfq.isEmpty() && itemRfq.equalsIgnoreCase(rfqId.trim())) {
                                    isMatch = true;
                                    logger.info("Devis {} lié au RFQ {} (niveau item)", quoteName, rfqId);
                                    break;
                                }
                            }
                        }
                    }
                    
                    if (isMatch) {
                        if (quoteItems != null && quoteItems.isArray()) {
                            for (JsonNode quoteItem : quoteItems) {
                                String quoteItemCode = getSafeTextValue(quoteItem, "item_code");
                                double rate = quoteItem.has("rate") ? quoteItem.get("rate").asDouble() : 0.0;
                                
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
                                dto.setDocStatus(docStatus);
                                
                                quotationDetails.add(dto);
                                logger.debug("Found item {} in quotation {}: rate={}, docstatus={}", 
                                    quoteItemCode, quoteName, rate, docStatus);
                            }
                        }
                    } else {
                        logger.warn("Devis {} ignoré - RFQ incorrect: {}", quoteName, quotationRfq);
                    }
                }
            }
    
            
            if (quotationDetails.isEmpty()) {
                ResponseEntity<JsonNode> rfqResponse = restTemplate.exchange(
                    baseUrl + "/api/resource/Request for Quotation/" + rfqId,
                    HttpMethod.GET,
                    entity,
                    JsonNode.class);
                
                JsonNode rfqData = rfqResponse.getBody().get("data");
                JsonNode rfqItems = rfqData.get("items");
                
                if (rfqItems != null && rfqItems.isArray() && rfqItems.size() > 0) {
                    String itemCode = getSafeTextValue(rfqItems.get(0), "item_code");
                    
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
          
                logger.info("Creation of new supplier quotation will be handled by ItemService");
                return;
            }
            
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl + "/api/resource/Supplier Quotation/" + quotationName,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                JsonNode.class);
            
            JsonNode quoteData = response.getBody().get("data");
            int docStatus = quoteData.has("docstatus") ? quoteData.get("docstatus").asInt() : 0;
            
            if (docStatus == 1) {
                logger.info("Quotation {} is already submitted. Creating a new one.", quotationName);
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

    public List<QuotationRequestDTO> getFilteredQuotationDetails(String rfqId, String supplierId, String sid,
                                                             String itemCode, String itemName, String editable) {
    List<QuotationRequestDTO> allQuotations = getQuotationDetails(rfqId, supplierId, sid);

    return allQuotations.stream()
            .filter(q -> itemCode == null || q.getItemCode().toLowerCase().contains(itemCode.toLowerCase()))
            .filter(q -> itemName == null || q.getItemName().toLowerCase().contains(itemName.toLowerCase()))
            .filter(q -> {
                if ("true".equals(editable)) return q.isEditable();
                if ("false".equals(editable)) return !q.isEditable();
                return true;
            })
            .toList();
}


    
}