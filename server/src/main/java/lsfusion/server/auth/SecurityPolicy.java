package lsfusion.server.auth;

public class SecurityPolicy {
    public final long ID;
    public Boolean configurator;

    public SecurityPolicy() {
        this(-1);
    }

    public SecurityPolicy(long ID) {
        this.ID = ID;
    }

    public ClassSecurityPolicy cls = new ClassSecurityPolicy();
    public PropertySecurityPolicy property = new PropertySecurityPolicy();
    public NavigatorSecurityPolicy navigator = new NavigatorSecurityPolicy();

    public void override(SecurityPolicy policy) {
        cls.override(policy.cls);
        property.override(policy.property);
        navigator.override(policy.navigator);
        if (policy.configurator != null) {
            configurator = policy.configurator;
        }
    }

    public void setReplaceMode(boolean replaceMode) {
        cls.setReplaceMode(replaceMode);
        property.setReplaceMode(replaceMode);
        navigator.replaceMode = true;
    }
}
