package itu.prom16.eval.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;

@Controller
public class LoginController {

    @Value("${erpnext.api.base-url}")
    private String erpnextBaseUrl;

    @GetMapping("/")
    public String showLoginPage() {
        return "login";
    }

    @PostMapping("/")
    public String login(
            @RequestParam String username,
            @RequestParam String password,
            HttpSession session,
            Model model
    ) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("usr", username);
        formData.add("pwd", password);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                erpnextBaseUrl + "/api/method/login",
                request,
                String.class
            );

            String sid = response.getHeaders().getFirst("Set-Cookie");
            if (sid != null && sid.contains("sid=")) {
                sid = sid.split("sid=")[1].split(";")[0];
                session.setAttribute("sid", sid);
                return "redirect:/dashboard";
            } else {
                model.addAttribute("error", "Échec de l'authentification");
                return "/login";
            }
        } catch (HttpClientErrorException e) {
            model.addAttribute("error", "Identifiant ou mot de passe incorrect");
            return "/login";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        String sid = (String) session.getAttribute("sid");
        if (sid != null) {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.add("Cookie", "sid=" + sid);
            HttpEntity<String> request = new HttpEntity<>(headers);
            restTemplate.exchange(
                erpnextBaseUrl + "/api/method/logout",
                HttpMethod.GET,
                request,
                String.class
            );
            session.invalidate();
        }
        return "redirect:/";
    }
}