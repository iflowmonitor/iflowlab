package com.iflowmonitor.iflowlab.app;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;
import java.util.ArrayList;
import java.util.List;

/**
 * Entry point. Maps the {@code --workspace <dir>} and {@code --port <n>} flags
 * (R10) to Quarkus config before boot, then hands off. Default workspace = CWD.
 */
@QuarkusMain
public class Main {

    public static void main(String... args) {
        List<String> passthrough = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if ("--workspace".equals(args[i]) && i + 1 < args.length) {
                System.setProperty("iflowlab.workspace", args[++i]);
            } else if ("--port".equals(args[i]) && i + 1 < args.length) {
                System.setProperty("quarkus.http.port", args[++i]);
            } else {
                passthrough.add(args[i]);
            }
        }
        Quarkus.run(passthrough.toArray(new String[0]));
    }
}
