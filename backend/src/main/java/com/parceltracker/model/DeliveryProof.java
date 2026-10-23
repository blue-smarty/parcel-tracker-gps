package com.parceltracker.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

public class DeliveryProof {
    @Size(max = 20_000)
    private String recipientSignature;

    @Size(max = 120)
    private String barcode;

    @Size(max = 400)
    private String dropOffNote;

    @Size(max = 5_600_000)
    private String photoBase64;

    @DecimalMin("0.0")
    private Double tipAmount = 0.0;

    @DecimalMin("0.0")
    private Double payoutAmount = 0.0;

    public String getRecipientSignature() { return recipientSignature; }
    public void setRecipientSignature(String recipientSignature) { this.recipientSignature = recipientSignature; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public String getDropOffNote() { return dropOffNote; }
    public void setDropOffNote(String dropOffNote) { this.dropOffNote = dropOffNote; }
    public String getPhotoBase64() { return photoBase64; }
    public void setPhotoBase64(String photoBase64) { this.photoBase64 = photoBase64; }
    public Double getTipAmount() { return tipAmount; }
    public void setTipAmount(Double tipAmount) { this.tipAmount = tipAmount; }
    public Double getPayoutAmount() { return payoutAmount; }
    public void setPayoutAmount(Double payoutAmount) { this.payoutAmount = payoutAmount; }
}
