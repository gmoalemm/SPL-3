package impl.tftp;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public class PublicResources {
    public static ConcurrentHashMap<Integer, String> usersMap = new ConcurrentHashMap<>();
    public static Semaphore accessSemaphore = new Semaphore(1, true);
}
