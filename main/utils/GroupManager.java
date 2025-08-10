package main.utils;

import java.util.Collection;
import main.data.GroupStore;

public class GroupManager {
    private final GroupStore groupStore;

    public GroupManager(GroupStore groupStore) {
        this.groupStore = groupStore;
    }

    public boolean createGroup(String groupId, String groupName, Collection<String> members, String creatorUserId,
            long timestamp) {
        return groupStore.createGroup(groupId, groupName, members, creatorUserId, timestamp);
    }

    public boolean updateGroupMembers(String groupId, Collection<String> addMembers, Collection<String> removeMembers) {
        return groupStore.updateGroupMembers(groupId, addMembers, removeMembers);
    }

    public boolean isUserMember(String groupId, String userId) {
        return groupStore.isUserMember(groupId, userId);
    }

    public GroupStore.Group getGroup(String groupId) {
        return groupStore.getGroup(groupId);
    }

    public boolean deleteGroup(String groupId) {
        return groupStore.deleteGroup(groupId);
    }
}