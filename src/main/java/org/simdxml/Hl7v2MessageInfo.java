package org.simdxml;

import java.util.Objects;

/** Allocation-bounded projection of key fields from an HL7 v2.x pipe-delimited message. */
public final class Hl7v2MessageInfo implements VerticalMessageInfo {
    private final String sendingApplication;
    private final String sendingFacility;
    private final String receivingApplication;
    private final String receivingFacility;
    private final String dateTimeOfMessage;
    private final String messageType;
    private final String messageControlId;
    private final String processingId;
    private final String versionId;

    public Hl7v2MessageInfo(String sendingApplication, String sendingFacility,
                            String receivingApplication, String receivingFacility,
                            String dateTimeOfMessage, String messageType,
                            String messageControlId, String processingId,
                            String versionId) {
        this.sendingApplication = sendingApplication;
        this.sendingFacility = sendingFacility;
        this.receivingApplication = receivingApplication;
        this.receivingFacility = receivingFacility;
        this.dateTimeOfMessage = dateTimeOfMessage;
        this.messageType = messageType;
        this.messageControlId = messageControlId;
        this.processingId = processingId;
        this.versionId = versionId;
    }

    public String sendingApplication() { return sendingApplication; }
    public String sendingFacility() { return sendingFacility; }
    public String receivingApplication() { return receivingApplication; }
    public String receivingFacility() { return receivingFacility; }
    public String dateTimeOfMessage() { return dateTimeOfMessage; }
    public String messageType() { return messageType; }
    public String messageControlId() { return messageControlId; }
    public String processingId() { return processingId; }
    public String versionId() { return versionId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Hl7v2MessageInfo)) return false;
        Hl7v2MessageInfo that = (Hl7v2MessageInfo) other;
        return Objects.equals(sendingApplication, that.sendingApplication) &&
               Objects.equals(sendingFacility, that.sendingFacility) &&
               Objects.equals(receivingApplication, that.receivingApplication) &&
               Objects.equals(receivingFacility, that.receivingFacility) &&
               Objects.equals(dateTimeOfMessage, that.dateTimeOfMessage) &&
               Objects.equals(messageType, that.messageType) &&
               Objects.equals(messageControlId, that.messageControlId) &&
               Objects.equals(processingId, that.processingId) &&
               Objects.equals(versionId, that.versionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sendingApplication, sendingFacility, receivingApplication,
                receivingFacility, dateTimeOfMessage, messageType, messageControlId,
                processingId, versionId);
    }

    @Override
    public String toString() {
        return "Hl7v2MessageInfo[sendingApp=" + sendingApplication + ", sendingFac=" + sendingFacility +
                ", receivingApp=" + receivingApplication + ", receivingFac=" + receivingFacility +
                ", dateTime=" + dateTimeOfMessage + ", msgType=" + messageType +
                ", ctrlId=" + messageControlId + ", procId=" + processingId + ", version=" + versionId + "]";
    }
}