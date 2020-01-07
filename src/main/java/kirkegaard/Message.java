package kirkegaard;

import org.apache.commons.lang3.SerializationUtils;

import java.io.Serializable;

public class Message implements Serializable {

    public static final int REQUEST = 0, REPLY = 1, ACKNOWLEDGEMENT = 2;

    public final int messageType; // 0 = request, 1 = reply, ack = 2
    public final int requestID;
    public final int operation;
    public final String messageData;

    public Message(int messageType, int requestID, int operation, String messageData) {
        this.messageType = messageType;
        this.requestID = requestID;
        this.operation = operation;
        this.messageData = messageData;
    }

    public Message(int messageType, int requestID, int operation) {
        this(messageType, requestID, operation, "");
    }

    public byte[] marshall(){
        return SerializationUtils.serialize(this);
    }

    public static Message unMarshall(byte[] data){
        return SerializationUtils.deserialize(data);
    }

    public boolean isRequest() {
        return messageType == REQUEST;
    }

    public boolean isAcknowledgement() {
        return messageType == ACKNOWLEDGEMENT;
    }

    public Operation getOperation() {
        return Operation.operationFromID(operation);
    }

    public int getNumber() {
        return Integer.parseInt(messageData);
    }

    @Override
    public String toString() {
        return "Message{" +
                "MessageType : " + getMessageTypeName() +
                ", requestID = " + requestID +
                ", operation = " + getOperation().name() +
                ", messageData = '" + messageData + '\'' +
                '}';
    }

    private String getMessageTypeName() {
        if(messageType == 0) return "REQUEST";
        if(messageType == 1) return "REPLY";
        else return "ACKNOWLEDGEMENT";
    }
}
