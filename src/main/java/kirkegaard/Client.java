package kirkegaard;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Scanner;

public class Client {

    public static void main(String[] args) throws IOException {
        new Client().start();
    }

    private static final int REQUEST_TIME_OUT_MS = 5000;
    private static final int ASSUMED_SERVER_FAILURE_TIMEOUT = REQUEST_TIME_OUT_MS * 5;

    private DatagramSocket datagramSocket;
    private final int CLIENT_PORT = 25566;
    private final int SERVER_PORT = 25565;
    private InetAddress serverAddress;

    private final int DEFAULT_BUFFER_SIZE = 256;
    private byte[] msgBuffer = new byte[DEFAULT_BUFFER_SIZE];

    private boolean running = false;
    private int lastRequestID = -1;

    private void start() throws IOException {
        datagramSocket = new DatagramSocket(CLIENT_PORT);
        datagramSocket.setSoTimeout(REQUEST_TIME_OUT_MS);

        running = true;
        serverAddress = InetAddress.getByName("localhost");

        while (running) {
            Input userInput = promptUser();

            if(userInput.operation == Operation.STOP) break;

            doOperation(userInput);
        }
    }

    private Input promptUser(){
        System.out.print("Would you like to :   ");
        for(Operation operation : Operation.values())
            System.out.print(operation.operationID + ": " + operation.name() + "    ");
        System.out.println();

        Scanner scanner = new Scanner(System.in);
        Operation operation = Operation.operationFromID(scanner.nextInt());
        if(operation == Operation.WITHDRAW || operation == Operation.DEPOSIT){
            System.out.println("Enter amount : ");
            return new Input(operation, scanner.nextInt());
        }

        System.out.println();
        return new Input(operation);
    }


    private void doOperation(Input input) throws IOException {
        // Message containing information such as request type, id and the id of the operation that we wish to perform
        Message message = new Message(Message.REQUEST_TYPE, ++lastRequestID, input.operation.operationID, input.additionalData);
        byte[] data = message.marshall();

        DatagramPacket requestPacket = new DatagramPacket(data, data.length, serverAddress, SERVER_PORT);

        int totalWaitTime = 0;
        while(true){
            datagramSocket.send(requestPacket);

            try{
                Message replyMessage = getReply();
                sendAck(replyMessage);
                return;
            }catch (SocketTimeoutException e){
                totalWaitTime += datagramSocket.getSoTimeout();
                System.out.println("Did not receive response from server after " + REQUEST_TIME_OUT_MS / 1000 + " seconds. " +
                        "Retransmitting request");
            }

            if(totalWaitTime > ASSUMED_SERVER_FAILURE_TIMEOUT){
                System.out.println("Server is assumed to be unresponsive. Stopping retransmission of request");
                running = false;
                return;
            }
        }
    }

    private Message getReply() throws IOException {
        DatagramPacket receivePacket = getNewReceivePacket();
        datagramSocket.receive(receivePacket);
        Message receivedMessage = Message.unMarshall(receivePacket.getData());

        if(receivedMessage.requestID != lastRequestID)
            getReply(); // Received old duplicate. Discard it and try again.

        System.out.println(receivedMessage.messageData);
        return receivedMessage;
    }

    private void sendAck(Message replyMessage) throws IOException {
        Message ackMessage = new Message(Message.ACKNOWLEDGEMENT, replyMessage.requestID, replyMessage.operation);
        byte[] data = ackMessage.marshall();
        DatagramPacket requestPacket = new DatagramPacket(data, data.length, serverAddress, SERVER_PORT);
        datagramSocket.send(requestPacket);
        System.out.println("Sent ack for reply : " + replyMessage.requestID + "\n");
    }

    private DatagramPacket getNewReceivePacket() {
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        return new DatagramPacket(buffer, buffer.length);
    }
}
