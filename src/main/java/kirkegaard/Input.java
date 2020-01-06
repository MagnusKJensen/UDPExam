package kirkegaard;

public class Input {

    public final Operation operation;
    public final String additionalData;

    public Input(Operation operation, int additionalData) {
        this.operation = operation;
        this.additionalData = Integer.toString(additionalData);
    }

    public Input(Operation operation) {
        this.operation = operation;
        this.additionalData = "";
    }
}
