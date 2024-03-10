package impl.tftp;

import java.util.concurrent.ConcurrentHashMap;

public class LoggedUsers {
    public static ConcurrentHashMap<Integer, String> usersMap = new ConcurrentHashMap<>();
}
