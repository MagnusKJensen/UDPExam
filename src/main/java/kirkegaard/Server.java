package kirkegaard;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;

public class Server {

    public static void main(String[] args) throws IOException {
        new Server().start();
    }

    private int lastAcknowledgedRequestID = -1;
    private int lastProcessedRequestID = -1;

    private boolean running = false;
    private byte[] buffer = new byte[256];

    private DatagramSocket serverSocket;
    private final int SERVER_PORT = 25565;
    private InetAddress clientAddress;
    private int clientPort;

    private int accountBalance = 0;

    ArrayList<Message> savedReplies = new ArrayList<Message>();

    public void start() throws IOException {
        serverSocket = new DatagramSocket(SERVER_PORT);
        runServerLoop();
    }

    private void runServerLoop() throws IOException {
        running = true;

        while (running) {
            DatagramPacket receivedPacket = new DatagramPacket(buffer, buffer.length);
            serverSocket.receive(receivedPacket);
            clientAddress = receivedPacket.getAddress();
            clientPort = receivedPacket.getPort();

            Message receivedMessage = Message.unMarshall(receivedPacket.getData());
            if(receivedMessage.isRequest())
                processRequest(receivedMessage);
            else if(receivedMessage.isAcknowledgement())
                processAcknowledgement(receivedMessage);
            // Otherwise just discard message
        }
    }

    private void processRequest(Message message) throws IOException {
        if(message.requestID <= lastAcknowledgedRequestID){
            System.out.println("Received old duplicate package, already acknowledged, with request id : " + message.requestID);
            // The message is an old duplicate and the repsonse has already been acknowledged by the client
            return;
        }

        if(message.requestID <= lastProcessedRequestID){
            System.out.println("Received old duplicate package with request id : " + message.requestID);
            // The operation has already been performed, but acknowledgement of reply has not been received
            // Operations are not idempotent so send copy of previous reply instead of running operation again
            resendReply(message);
            return;
        }

        doOperation(message);
    }

    private void resendReply(Message message) throws IOException {
        // Find reply
        for(Message savedReply: savedReplies){
            if(savedReply.requestID == message.requestID){
                sendMessageToClient(message);
                return;
            }
        }
        System.out.println("Resent reply for request : " + message.getOperation().name() + ", " + message.messageData);
    }

    private void doOperation(Message message) throws IOException {
        Operation operation = message.getOperation();
        String responseString = "";
        switch (operation){
            case WITHDRAW:
                int amount = message.getNumber();
                accountBalance -= message.getNumber();
                responseString = "Withdrew " + amount + ". Your balance is now :" + accountBalance;
                break;
            case DEPOSIT:
                amount = message.getNumber();
                accountBalance += message.getNumber();
                responseString = "Deposited " + amount + ". Your balance is now :" + accountBalance;
                break;
            case VIEW_BALANCE:
                responseString = "Your current balance is : " + accountBalance;
        }

        lastProcessedRequestID = message.requestID;
        Message reply = sendReply(message, responseString);
        savedReplies.add(reply);
        System.out.println("Sent reply : " + responseString);
    }

    private Message sendReply(Message originalMessage, String replyString) throws IOException {
        Message replyMessage = new Message(Message.REPLY_TYPE, originalMessage.requestID, originalMessage.operation, replyString);
        sendMessageToClient(replyMessage);
        return replyMessage;
    }

    private void sendMessageToClient(Message message) throws IOException {
        byte[] replyData = message.marshall();
        DatagramPacket responsePacket = new DatagramPacket(replyData, replyData.length, clientAddress, clientPort);
        serverSocket.send(responsePacket);
    }

    private void processAcknowledgement(Message ackMessage) {
        int ackID = ackMessage.requestID;
        savedReplies.removeIf((m) -> m.requestID == ackID);
        if(ackID > lastAcknowledgedRequestID) lastAcknowledgedRequestID = ackID;

        System.out.println("Processed acknowledgement of request " + ackMessage.requestID);
    }
}