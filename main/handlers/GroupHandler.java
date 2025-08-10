package main.handlers;

import java.net.InetAddress;
import java.util.*;
import java.util.stream.Collectors;

import main.UDPSocketManager;
import main.data.GroupStore;
import main.utils.*;

public class GroupHandler {
    private final UDPSocketManager socketManager;
    private final GroupManager groupManager;

    public GroupHandler(UDPSocketManager socketManager, GroupManager groupManager, String currentUserId) {
        this.socketManager = socketManager;
        this.groupManager = groupManager;
        this.currentUserId = currentUserId;
    }

}
