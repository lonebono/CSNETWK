package main.handlers;

import main.UDPSocketManager;
import main.utils.*;

public class GroupHandler {

    private final UDPSocketManager socketManager;
    private final GroupManager groupManager;
    private final TokenValidator tokenValidator;
    private final VerboseLogger verboseLogger;

    public GroupHandler(UDPSocketManager socketManager, GroupManager groupManager, TokenValidator tokenValidator,
            VerboseLogger verboseLogger) {
        this.socketManager = socketManager;
        this.groupManager = groupManager;
        this.tokenValidator = tokenValidator;
        this.verboseLogger = verboseLogger;
    }

}
