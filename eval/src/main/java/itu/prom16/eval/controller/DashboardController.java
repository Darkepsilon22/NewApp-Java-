package itu.prom16.eval.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import itu.prom16.eval.dto.ClientDTO;
import itu.prom16.eval.service.ClientService;
import jakarta.servlet.http.HttpSession;

@Controller
public class DashboardController {

    private final ClientService clientService;

    public DashboardController(ClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping("/dashboard")
    public String showDashboard(Model model, HttpSession session) {
        String sid = (String) session.getAttribute("sid");
        if (sid == null) {
            return "redirect:/";
        }

        try {
            List<ClientDTO> clients = clientService.getClients(sid);
            model.addAttribute("clients", clients);
            model.addAttribute("totalClients", clients.size());
            return "dashboard";
        } catch (Exception e) {
            return "redirect:/";
        }
    }
}
