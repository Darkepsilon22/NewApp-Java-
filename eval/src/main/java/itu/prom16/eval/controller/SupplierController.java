package itu.prom16.eval.controller;

import org.springframework.stereotype.Controller;
import itu.prom16.eval.dto.OrderDTO;
import itu.prom16.eval.dto.QuotationRequestDTO;
import itu.prom16.eval.dto.SupplierDTO;
import itu.prom16.eval.service.OrdersService;
import itu.prom16.eval.service.QuotationRequestService;
import itu.prom16.eval.service.SupplierService;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;
import java.util.List;

@Controller
public class SupplierController {
    private final OrdersService ordersService;
    private final QuotationRequestService quotationRequestService;
    private final SupplierService supplierService;

    public SupplierController(OrdersService ordersService, QuotationRequestService quotationRequestService,
                               SupplierService supplierService) {
        this.ordersService = ordersService;
        this.quotationRequestService = quotationRequestService;
        this.supplierService = supplierService;
    }

    @GetMapping("/suppliers")
    public String showSuppliers(Model model, HttpSession session) {
        String sid = (String) session.getAttribute("sid");
        if (sid == null) {
            return "redirect:/";
        }

        List<SupplierDTO> suppliers = supplierService.getSuppliers(sid);
        model.addAttribute("suppliers", suppliers);
        return "suppliers";
    }

    @GetMapping("/supplier/quotations")
    public String showQuotations(@RequestParam String supplierId, Model model, HttpSession session) {
        String sid = (String) session.getAttribute("sid");
        if (sid == null) {
            return "redirect:/";
        }

        List<QuotationRequestDTO> quotations = quotationRequestService.getQuotationRequests(supplierId, sid);
        model.addAttribute("quotations", quotations);
        return "quotations";
    }

    @GetMapping("/supplier/quotation-details")
    public String getQuotationDetails(
            @RequestParam String rfqId,
            @RequestParam String supplierId,
            Model model,
            HttpSession session) {

        String sid = (String) session.getAttribute("sid");
        if (sid == null) {
            return "redirect:/";
        }

        List<QuotationRequestDTO> quotationDetails = quotationRequestService.getQuotationDetails(rfqId, supplierId, sid);

        model.addAttribute("quotations", quotationDetails);
        model.addAttribute("rfqId", rfqId);
        model.addAttribute("supplierId", supplierId);

        return "quotation-details";
    }

    @GetMapping("/supplier/orders")
    public String showOrders(@RequestParam String supplierId, Model model, HttpSession session) {
        String sid = (String) session.getAttribute("sid");
        if (sid == null) {
            return "redirect:/";
        }

        List<OrderDTO> orders = ordersService.getOrders(supplierId, sid);
        model.addAttribute("orders", orders);
        return "orders";
    }
}
