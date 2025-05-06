package itu.prom16.eval.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Component
public class CustomLogoutHandler implements LogoutHandler {

    @Value("${erpnext.api.base-url}")
    private String erpnextBaseUrl;

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        HttpSession session = request.getSession(false);
        String sid = null;
        
        if (session != null) {
            sid = (String) session.getAttribute("sid");
        }
        
        if (sid != null) {
            try {
                RestTemplate restTemplate = new RestTemplate();
                HttpHeaders headers = new HttpHeaders();
                headers.add("Cookie", "sid=" + sid);
                HttpEntity<String> httpRequest = new HttpEntity<>(headers);
                
                restTemplate.exchange(
                    erpnextBaseUrl + "/api/method/logout",
                    HttpMethod.GET,
                    httpRequest,
                    String.class
                );
            } catch (Exception e) {
                System.err.println("Erreur lors de la déconnexion de l'API ERPNext: " + e.getMessage());
            }
        }
        
    }
}