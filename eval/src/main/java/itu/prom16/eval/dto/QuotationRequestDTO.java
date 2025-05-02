package itu.prom16.eval.dto;

public class QuotationRequestDTO {
    private String requestId;
    private String description;
    private double rate;
    private String itemCode;
    private String supplierQuotationName;
    private String itemName;
    private int docStatus; // 0 = Draft, 1 = Submitted, 2 = Cancelled

    public String getSupplierQuotationName() {
        return supplierQuotationName;
    }

    public void setSupplierQuotationName(String supplierQuotationName) {
        this.supplierQuotationName = supplierQuotationName;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getRate() {
        return rate;
    }

    public void setRate(double rate) {
        this.rate = rate;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }
    
    public int getDocStatus() {
        return docStatus;
    }
    
    public void setDocStatus(int docStatus) {
        this.docStatus = docStatus;
    }
    
    public boolean isEditable() {
        return docStatus == 0; // Only draft quotations (docStatus = 0) are editable
    }
}