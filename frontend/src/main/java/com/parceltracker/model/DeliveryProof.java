package com.parceltracker.model;

public class DeliveryProof {
    private String recipientSignature;
    private String barcode;
    private String dropOffNote;
    private String photoBase64;

    public String getRecipientSignature() { return recipientSignature; }
    public void setRecipientSignature(String recipientSignature) { this.recipientSignature = recipientSignature; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public String getDropOffNote() { return dropOffNote; }
    public void setDropOffNote(String dropOffNote) { this.dropOffNote = dropOffNote; }
    public String getPhotoBase64() { return photoBase64; }
    public void setPhotoBase64(String photoBase64) { this.photoBase64 = photoBase64; }
}
