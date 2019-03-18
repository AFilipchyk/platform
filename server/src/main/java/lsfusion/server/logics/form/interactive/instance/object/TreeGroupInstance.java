package lsfusion.server.logics.form.interactive.instance.object;

import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.ImOrderSet;
import lsfusion.base.col.interfaces.mutable.MOrderFilterSet;
import lsfusion.server.base.caches.IdentityLazy;
import lsfusion.server.logics.form.struct.object.TreeGroupEntity;

public class TreeGroupInstance {
    public TreeGroupEntity entity;
    public final ImOrderSet<GroupObjectInstance> groups;

    private static ImOrderSet<GroupObjectInstance> getUpTreeGroups(ImOrderSet<GroupObjectInstance> groups, GroupObjectInstance group, boolean include) {
        MOrderFilterSet<GroupObjectInstance> upGroups = SetFact.mOrderFilter(groups);
        for(GroupObjectInstance upGroup : groups) {
            if(include)
                upGroups.keep(upGroup);
            if(upGroup.equals(group))
                return SetFact.imOrderFilter(upGroups, groups);
            if(!include)
                upGroups.keep(upGroup);
        }
        throw new RuntimeException("should not be");
    }

    @IdentityLazy
    public ImOrderSet<GroupObjectInstance> getDownTreeGroups(GroupObjectInstance group) {
        return getUpTreeGroups(groups.reverseOrder(), group, false).reverseOrder();
    }
    
    public GroupObjectInstance getDownTreeGroup(GroupObjectInstance group) {
        ImOrderSet<GroupObjectInstance> downTreeGroups = getDownTreeGroups(group);
        return downTreeGroups.isEmpty() ? null : downTreeGroups.last();
    }

    @IdentityLazy
    public GroupObjectInstance getUpTreeGroup(GroupObjectInstance group) {
        GroupObjectInstance result = null;
        for(GroupObjectInstance upGroup : groups) {
            if(upGroup.equals(group))
                return result;
            result = upGroup;
        }
        throw new RuntimeException("should not be");
    }

    @IdentityLazy
    public ImOrderSet<GroupObjectInstance> getUpTreeGroups(GroupObjectInstance group) {
        return getUpTreeGroups(groups, group, true);
    }

    public TreeGroupInstance(TreeGroupEntity entity, ImOrderSet<GroupObjectInstance> groups) {
        this.entity = entity;
        this.groups = groups;

        for(GroupObjectInstance group : groups)
            group.treeGroup = this;
    }

    public int getID() {
        return entity.getID();
    }
}
