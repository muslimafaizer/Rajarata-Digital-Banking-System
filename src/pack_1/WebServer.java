package pack_1;

/**
 * Entry point wrapper for WebServer in the pack_1 root package.
 * Dispatches to pack_1.controller.WebServer.
 */
public class WebServer {
    public static void main(String[] args) throws Exception {
        pack_1.controller.WebServer.main(args);
    }
}
