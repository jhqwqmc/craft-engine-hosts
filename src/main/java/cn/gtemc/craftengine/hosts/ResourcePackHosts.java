package cn.gtemc.craftengine.hosts;

import cn.gtemc.craftengine.hosts.impl.GitHubHost;
import cn.gtemc.craftengine.hosts.impl.GiteeHost;
import net.momirealms.craftengine.core.pack.host.ResourcePackHostFactory;
import net.momirealms.craftengine.core.registry.BuiltInRegistries;
import net.momirealms.craftengine.core.registry.Registries;
import net.momirealms.craftengine.core.registry.WritableRegistry;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.ResourceKey;

public class ResourcePackHosts {
    public static final Key GITHUB = Key.of("gtemc:github");
    public static final Key GITEE = Key.of("gtemc:gitee");

    public static void load() {
        register(GITHUB, GitHubHost.FACTORY);
        register(GITEE, GiteeHost.FACTORY);
    }

    private static void register(Key key, ResourcePackHostFactory factory) {
        ((WritableRegistry<ResourcePackHostFactory>) BuiltInRegistries.RESOURCE_PACK_HOST_FACTORY)
                .register(ResourceKey.create(Registries.RESOURCE_PACK_HOST_FACTORY.location(), key), factory);
    }
}
