package kirkegaard;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Random;

public class Server {

    public static void main(String[] args) throws IOException {
        new Server().start();
    }

    private boolean running = false;

    private DatagramSocket serverSocket;
    private final int SERVER_PORT = 25565;
    private InetAddress clientAddress;
    private int clientPort;

    private final int DEFAULT_BUFFER_SIZE = 256;
    private byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];

    // Last request for which the final acknowledgement has been received
    private int lastAcknowledgedRequestID = -1;
    // The last request that has been processed and replied to
    private int lastProcessedRequestID = -1;

    // Since operations are not idempotent, replies are saved to avoid having to execute operations again
    // in cases where the reply packet gets lost
    ArrayList<Message> savedReplies = new ArrayList<>();

    private int accountBalance = 0;

    private Random random = new Random(9999);

    public void start() throws IOException {
        serverSocket = new DatagramSocket(SERVER_PORT);
        runServerLoop();
    }

    private void runServerLoop() throws IOException {
        running = true;

        while (running) {
            buffer = new byte[DEFAULT_BUFFER_SIZE];
            DatagramPacket receivedPacket = new DatagramPacket(buffer, buffer.length);
            serverSocket.receive(receivedPacket);
            if(random.nextBoolean())
                continue;
            clientAddress = receivedPacket.getAddress();
            clientPort = receivedPacket.getPort();

            Message receivedMessage = Message.unMarshall(receivedPacket.getData());
            System.out.println("RECEIVED : " + receivedMessage.toString());
            if(receivedMessage.isRequest())
                processRequest(receivedMessage);
            else if(receivedMessage.isAcknowledgement())
                processAcknowledgement(receivedMessage);
            // Otherwise just discard message

            System.out.println();
        }
    }

    private void processRequest(Message message) throws IOException {
        if(message.requestID <= lastAcknowledgedRequestID){
            System.out.println("Received old duplicate packet, already acknowledged, with request id : " + message.requestID);
            System.out.println("Ignoring packet");
            // The message is an old duplicate and the response has already been acknowledged by the client
            return;
        }

        if(message.requestID <= lastProcessedRequestID){
            System.out.println("Received request that had already been processed. ID : " + message.requestID);
            // The operation has already been performed, but acknowledgement of reply has not been received
            // Operations are not idempotent so send copy of previous reply instead of running operation again
            resendReply(message);
            return;
        }

        doOperation(message);
    }

    private void processAcknowledgement(Message ackMessage) {
        int ackID = ackMessage.requestID;

        // The saved reply for this request and all preceding requests can now be deleted,
        // because the client has acknowledged that it has received them
        savedReplies.removeIf((m) -> m.requestID <= ackID);
        if(ackID > lastAcknowledgedRequestID) lastAcknowledgedRequestID = ackID;

        System.out.println("Processed acknowledgement of request " + ackMessage.requestID);
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
        System.out.println("Sent reply : \"" + responseString + "\"");
    }

    private Message sendReply(Message originalMessage, String replyString) throws IOException {
        Message replyMessage
                = new Message(Message.REPLY, originalMessage.requestID, originalMessage.operation, replyString);

        sendMessageToClient(replyMessage);
        return replyMessage;
    }

    private void resendReply(Message requestMsg) throws IOException {
        // Find reply
        for(Message savedReply: savedReplies) {
            if (savedReply.requestID == requestMsg.requestID) {
                sendMessageToClient(savedReply);
                System.out.println("Resent reply for request : " + requestMsg.getOperation().name()
                        + " : \"" + savedReply.messageData + "\"");
                return;
            }
        }
    }

    private void sendMessageToClient(Message message) throws IOException {
        byte[] replyData = message.marshall();
        DatagramPacket responsePacket = new DatagramPacket(replyData, replyData.length, clientAddress, clientPort);
        if(random.nextBoolean())
            serverSocket.send(responsePacket);
    }


}