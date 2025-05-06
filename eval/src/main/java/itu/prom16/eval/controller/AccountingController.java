package itu.prom16.eval.controller;

import itu.prom16.eval.dto.SupplierInvoiceDTO;
import itu.prom16.eval.service.AccountingService;

import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ch.qos.logback.classic.Logger;
import jakarta.servlet.http.HttpSession;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@Controller
public class AccountingController {

    private final AccountingService accountingService;
    private static final Logger logger = (Logger) LoggerFactory.getLogger(AccountingController.class);

    public AccountingController(AccountingService accountingService) {
        this.accountingService = accountingService;
    }

    @GetMapping("/accounting")
    public String showInvoices(Model model, HttpSession session) {
        String sid = (String) session.getAttribute("sid");
        if (sid == null)
            return "redirect:/";
        List<SupplierInvoiceDTO> invoices = accountingService.getAllInvoices(sid);
        model.addAttribute("invoices", invoices);
        return "accounting";
    }

    @PostMapping("/accounting/pay")
public String payInvoice(
        @RequestParam String invoiceId,
        @RequestParam String supplier,
        @RequestParam double amount,
        HttpSession session,
        RedirectAttributes redirectAttributes) {
    
    String sid = (String) session.getAttribute("sid");
    if (sid == null) return "redirect:/";
    
    try {
        accountingService.payInvoice(sid, invoiceId, supplier, amount);
        redirectAttributes.addFlashAttribute("success", "Paiement effectué avec succès");
    } catch (Exception e) {
        logger.error("Erreur de paiement", e);
        redirectAttributes.addFlashAttribute("error", 
            "Erreur lors du paiement: " + e.getMessage());
    }
    return "redirect:/accounting";
}

@GetMapping("/accounting/{invoiceId}/export")
public ResponseEntity<byte[]> exportInvoicePdf(@PathVariable String invoiceId,
                                               HttpSession session) {
    String sid = (String) session.getAttribute("sid");
    if (sid == null) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    try {
        byte[] pdf = accountingService.fetchInvoicePdf(invoiceId, sid);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        // on force le téléchargement via Content-Disposition
        headers.setContentDisposition(ContentDisposition
            .builder("attachment")
            .filename(invoiceId + ".pdf")
            .build());
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);

    } catch (Exception e) {
        logger.error("Erreur export PDF pour facture {}", invoiceId, e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
}

}
