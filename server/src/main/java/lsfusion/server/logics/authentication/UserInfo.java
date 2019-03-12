package lsfusion.server.logics.authentication;

import java.io.Serializable;
import java.util.List;

public class UserInfo implements Serializable {
    public static String salt = "sdkvswhw34839h";

    public Boolean isLocked;
    public String username;
    public String password;
    public List<String> roles;

    public UserInfo(Boolean isLocked, String username, String password, List<String> roles) {
        this.isLocked = isLocked;
        this.username = username;
        this.password = password;
        this.roles = roles;
    }
}
