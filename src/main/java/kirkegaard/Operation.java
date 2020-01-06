package kirkegaard;

public enum Operation {

    VIEW_BALANCE(1), DEPOSIT(2), WITHDRAW(3), STOP(-1);

    public final int operationID;

    Operation(int operationID) {
        this.operationID = operationID;
    }

    public static Operation operationFromID(int operationID) {
        for (Operation operation : Operation.values())
            if (operationID == operation.operationID) return operation;

        // Default
        return STOP;
    }
}
