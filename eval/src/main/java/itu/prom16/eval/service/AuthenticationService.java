package itu.prom16.eval.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

@Service
public class AuthenticationService {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationService.class);
    private static final String SESSION_SID_KEY = "erp_sid";
    private static final String SESSION_USER_KEY = "erp_user";

    @Value("${erpnext.api.base-url}")
    private String baseUrl;

    /**
     * Authenticate user against ERPNext
     * @param username ERPNext username
     * @param password ERPNext password
     * @param session HTTP session to store auth info
     * @return true if authentication successful
     */
    public boolean authenticate(String username, String password, HttpSession session) {
        try {
            // Prepare login request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, String> loginRequest = new HashMap<>();
            loginRequest.put("usr", username);
            loginRequest.put("pwd", password);
            
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(loginRequest, headers);
            RestTemplate restTemplate = new RestTemplate();
            
            // Send login request
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl + "/api/method/login",
                HttpMethod.POST,
                entity,
                JsonNode.class
            );
            
            // Extract session ID from cookies
            String sid = extractSidFromResponse(response);
            
            if (sid != null && !sid.isEmpty()) {
                // Store session information
                session.setAttribute(SESSION_SID_KEY, sid);
                session.setAttribute(SESSION_USER_KEY, username);
                logger.info("User {} authenticated successfully", username);
                return true;
            }
            
            logger.warn("Authentication failed for user {}: No SID returned", username);
            return false;
            
        } catch (Exception e) {
            logger.error("Authentication error for user " + username, e);
            return false;
        }
    }
    
    /**
     * Get ERPNext session ID from current session
     * @param session HTTP session
     * @return session ID or null if not authenticated
     */
    public String getSessionId(HttpSession session) {
        return (String) session.getAttribute(SESSION_SID_KEY);
    }
    
    /**
     * Get authenticated username
     * @param session HTTP session
     * @return username or null if not authenticated
     */
    public String getUsername(HttpSession session) {
        return (String) session.getAttribute(SESSION_USER_KEY);
    }
    
    /**
     * Check if user is authenticated
     * @param session HTTP session
     * @return true if authenticated
     */
    public boolean isAuthenticated(HttpSession session) {
        return session.getAttribute(SESSION_SID_KEY) != null;
    }
    
    /**
     * Extract session ID from login response
     */
    private String extractSidFromResponse(ResponseEntity<JsonNode> response) {
        HttpHeaders headers = response.getHeaders();
        
        // Look for Set-Cookie header containing sid
        if (headers.containsKey(HttpHeaders.SET_COOKIE)) {
            for (String cookie : headers.get(HttpHeaders.SET_COOKIE)) {
                if (cookie.contains("sid=")) {
                    // Extract sid value from cookie string
                    int startIndex = cookie.indexOf("sid=") + 4;
                    int endIndex = cookie.indexOf(";", startIndex);
                    if (endIndex == -1) {
                        endIndex = cookie.length();
                    }
                    return cookie.substring(startIndex, endIndex);
                }
            }
        }
        
        return null;
    }
    
    /**
     * Logout from ERPNext
     * @param session HTTP session
     */
    public void logout(HttpSession session) {
        String sid = getSessionId(session);
        if (sid != null) {
            try {
                // Call logout API
                HttpHeaders headers = new HttpHeaders();
                headers.add("Cookie", "sid=" + sid);
                
                HttpEntity<String> entity = new HttpEntity<>(headers);
                RestTemplate restTemplate = new RestTemplate();
                
                restTemplate.exchange(
                    baseUrl + "/api/method/logout",
                    HttpMethod.GET,
                    entity,
                    String.class
                );
            } catch (Exception e) {
                logger.warn("Error during logout", e);
            }
        }
        
        // Clear session data
        session.removeAttribute(SESSION_SID_KEY);
        session.removeAttribute(SESSION_USER_KEY);
    }
}