package itu.prom16.eval.controller;

import itu.prom16.eval.service.ItemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;

@Controller
public class ItemController {
    private static final Logger logger = LoggerFactory.getLogger(ItemController.class);
    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @PostMapping("/item/update-price")
    public String updateItemPrice(
            @RequestParam String itemCode,
            @RequestParam double newPrice,
            @RequestParam(required = false) String supplierId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        
        String sid = (String) session.getAttribute("sid");
        if (sid == null) {
            return "redirect:/";
        }
        
        try {
            itemService.updatePriceListRate(itemCode, newPrice, sid);
            redirectAttributes.addFlashAttribute("successMessage", 
                "Prix mis à jour avec succès pour l'article " + itemCode);
        } catch (Exception e) {
            logger.error("Error updating price", e);
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Erreur lors de la mise à jour du prix: " + e.getMessage());
        }
        
        // Redirect back to the quotations page if supplierId is provided
        if (supplierId != null && !supplierId.isEmpty()) {
            return "redirect:/supplier/quotations?supplierId=" + supplierId;
        }
        
        return "redirect:/suppliers";
    }
    
    @PostMapping("/item/update-quotation-price")
    public String updateQuotationItemPrice(
            @RequestParam String itemCode,
            @RequestParam double newPrice,
            @RequestParam String supplierId,
            @RequestParam String rfqId,
            @RequestParam String quotationName,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        
        String sid = (String) session.getAttribute("sid");
        if (sid == null) {
            return "redirect:/";
        }
        
        try {
            if ("Nouveau devis".equals(quotationName)) {
                // Create a new quotation
                logger.info("Creating new supplier quotation for item {} with price {}", itemCode, newPrice);
                itemService.updatePriceListRate(itemCode, newPrice, sid);
            } else {
                // Try to update existing quotation, or create new one if it fails
                logger.info("Updating price for item {} in quotation {} to {}", itemCode, quotationName, newPrice);
                itemService.updateQuotationItemPrice(quotationName, itemCode, newPrice, sid);
            }
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "Prix mis à jour avec succès pour l'article " + itemCode);
        } catch (Exception e) {
            logger.error("Error updating quotation price", e);
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Erreur lors de la mise à jour du prix: " + e.getMessage());
        }
        
        return "redirect:/supplier/quotation-details?rfqId=" + rfqId + "&supplierId=" + supplierId;
    }
}