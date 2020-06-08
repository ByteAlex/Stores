package discord4j.store.infinispan;

import com.austinv11.servicer.WireService;
import discord4j.store.api.Store;
import discord4j.store.api.primitive.ForwardingStore;
import discord4j.store.api.primitive.LongObjStore;
import discord4j.store.api.service.StoreService;
import discord4j.store.api.util.StoreContext;
import org.infinispan.Cache;
import org.infinispan.commons.api.CacheContainerAdmin;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.cache.StorageType;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.transaction.LockingMode;
import org.infinispan.transaction.TransactionMode;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;

@WireService(StoreService.class)
public class InfinispanStoreService implements StoreService {

    public static InfinispanStoreService.Builder builder() {
        return new Builder();
    }

    public static InfinispanStoreService defaultConfiguration() {
        return InfinispanStoreService.builder().build();
    }

    private final DefaultCacheManager cacheManager;
    private final CacheProvider cacheProvider;

    private InfinispanStoreService(Consumer<GlobalConfigurationBuilder> configurationBuilder,
                                   CacheProvider cacheProvider) {
        final GlobalConfigurationBuilder builder = GlobalConfigurationBuilder.defaultClusteredBuilder();
        configurationBuilder.accept(builder);
        this.cacheManager = new DefaultCacheManager(builder.build());
        this.cacheProvider = cacheProvider;
    }

    @Override
    public boolean hasGenericStores() {
        return true;
    }

    @Override
    public <K extends Comparable<K>, V> Store<K, V> provideGenericStore(Class<K> keyClass, Class<V> valueClass) {
        final Cache<K, V> cache = cacheProvider.buildCache(cacheManager, valueClass.getSimpleName(), keyClass,
                valueClass);
        return new InfinispanStore<>(cache);
    }

    @Override
    public boolean hasLongObjStores() {
        return true;
    }

    @Override
    public <V> LongObjStore<V> provideLongObjStore(Class<V> valueClass) {
        return new ForwardingStore<>(provideGenericStore(Long.class, valueClass));
    }

    @Override
    public void init(StoreContext context) {

    }

    @Override
    public Mono<Void> dispose() {
        return Mono.fromRunnable(cacheManager::stop);
    }

    public static class Builder {

        private Consumer<GlobalConfigurationBuilder> configurationBuilder = configuration -> {
            configuration.transport().defaultTransport()
                    .clusterName("Discord4J")
                    .addProperty("configurationFile", "default-jgroups-tcp.xml"); // provided via library classpath
        };
        private CacheProvider cacheProvider = new CacheProvider() {
            @Override
            public <K, V> Cache<K, V> buildCache(DefaultCacheManager cacheManager, String name, Class<K> keyClass,
                                                 Class<V> valueClass) {
                final ConfigurationBuilder configurationBuilder = new ConfigurationBuilder();
                configurationBuilder.clustering()
                        .cacheMode(CacheMode.DIST_ASYNC);
                configurationBuilder.clustering().transaction()
                        .transactionMode(TransactionMode.TRANSACTIONAL)
                        .lockingMode(LockingMode.PESSIMISTIC);
                configurationBuilder.clustering().l1()
                        .enable()
                        .lifespan(1000);
                configurationBuilder.clustering().memory()
                        .storageType(StorageType.OBJECT);
                return cacheManager.administration()
                        .withFlags(CacheContainerAdmin.AdminFlag.VOLATILE)
                        .getOrCreateCache("discord4j:store:" + name, configurationBuilder.build());
            }
        };

        private Builder() {
        }

        public InfinispanStoreService.Builder withConfigurationBuilder(Consumer<GlobalConfigurationBuilder> builder) {
            configurationBuilder = builder;
            return this;
        }

        public InfinispanStoreService.Builder withCacheProvider(CacheProvider provider) {
            this.cacheProvider = provider;
            return this;
        }

        public InfinispanStoreService build() {
            return new InfinispanStoreService(configurationBuilder, cacheProvider);
        }
    }

    @FunctionalInterface
    public interface CacheProvider {

        <K, V> Cache<K, V> buildCache(DefaultCacheManager cacheManager, String name, Class<K> keyClass,
                                      Class<V> valueClass);
    }

}
