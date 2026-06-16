package io.agentcore.extensions;

import java.util.*;

public class ExtensionLoader {
    public List<Extension> loadFromServiceLoader() {
        List<Extension> result = new ArrayList<>();
        ServiceLoader<Extension> loader = ServiceLoader.load(Extension.class);
        for (var ext : loader) {
            result.add(ext);
        }
        return result;
    }
}
