package impl.tftp;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public class PublicResources {
    // a username map, saves each logged in user
    public static ConcurrentHashMap<Integer, String> usersMap = new ConcurrentHashMap<>();

    // a semaphore to use when accessing files
    // only one thread at a given time is allowed to modify files
    public static Semaphore accessSemaphore = new Semaphore(1, true);
}
