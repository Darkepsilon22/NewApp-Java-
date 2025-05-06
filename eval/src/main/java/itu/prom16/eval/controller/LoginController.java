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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;

import java.util.Collections;

@Controller
public class LoginController {

    @Value("${erpnext.api.base-url}")
    private String erpnextBaseUrl;

    @GetMapping("/")
    public String showLoginPage(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            return "redirect:/suppliers";
        }
        return "login";
    }

    @PostMapping("/")
    public String login(
            @RequestParam String username,
            @RequestParam String password,
            HttpServletRequest request,
            Model model
    ) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("usr", username);
        formData.add("pwd", password);

        HttpEntity<MultiValueMap<String, String>> httpRequest = new HttpEntity<>(formData, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                erpnextBaseUrl + "/api/method/login",
                httpRequest,
                String.class
            );

            String sid = null;
            String cookieHeader = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
            if (cookieHeader != null && cookieHeader.contains("sid=")) {
                String[] cookies = cookieHeader.split(";");
                for (String cookie : cookies) {
                    if (cookie.trim().startsWith("sid=")) {
                        sid = cookie.trim().substring(4);
                        break;
                    }
                }
            }

            if (sid != null) {
                // Stocker le SID dans la session
                HttpSession session = request.getSession(true);
                session.setAttribute("sid", sid);
                session.setAttribute("username", username);
                
                // Créer un token d'authentification pour Spring Security
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        username, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
                
                // Définir l'authentification dans le contexte de sécurité
                SecurityContextHolder.getContext().setAuthentication(authToken);
                
                return "redirect:/suppliers";
            } else {
                model.addAttribute("loginerror", true);
                return "login";
            }
        } catch (HttpClientErrorException e) {
            model.addAttribute("loginerror", true);
            return "login";
        }
    }

  
}