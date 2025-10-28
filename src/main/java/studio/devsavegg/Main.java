package studio.devsavegg;

import studio.devsavegg.server.ServerMain;

public class Main {
    public static void main(String[] args) throws Exception {
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : 8080;
        new ServerMain(port).run();
    }
}