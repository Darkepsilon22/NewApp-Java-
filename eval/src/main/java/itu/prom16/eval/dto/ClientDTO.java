package itu.prom16.eval.dto;

public class ClientDTO {
    private String customerName  = "N/A";
    private String customerType  = "N/A";

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = (customerName != null) ? customerName : "N/A";
    }

    public String getCustomerType() {
        return customerType;
    }

    public void setCustomerType(String customerType) {
        this.customerType = (customerType != null) ? customerType : "N/A";
    }
}