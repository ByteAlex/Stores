package discord4j.store.cache;

import discord4j.store.api.Store;
import discord4j.store.api.primitive.ForwardingStore;
import discord4j.store.api.primitive.LongObjStore;
import discord4j.store.api.service.StoreService;
import discord4j.store.api.util.StoreContext;
import reactor.core.publisher.Mono;

import java.time.Duration;

public class DelegatingCacheStoreService implements StoreService {

    private final Duration cacheTime;
    private final StoreService delegate;

    public DelegatingCacheStoreService(final Duration cacheTime, final StoreService delegate) {
        this.cacheTime = cacheTime;
        this.delegate = delegate;
    }

    @Override
    public boolean hasGenericStores() {
        return delegate.hasGenericStores();
    }

    @Override
    public <K extends Comparable<K>, V> Store<K, V> provideGenericStore(Class<K> keyClass, Class<V> valueClass) {
        return new DelegatingCacheStore<>(cacheTime, delegate.provideGenericStore(keyClass, valueClass));
    }

    @Override
    public boolean hasLongObjStores() {
        return false;
    }

    @Override
    public <V> LongObjStore<V> provideLongObjStore(Class<V> valueClass) {
        return new ForwardingStore<>(provideGenericStore(Long.class, valueClass));
    }

    @Override
    public void init(StoreContext context) {
        delegate.init(context);
    }

    @Override
    public Mono<Void> dispose() {
        return delegate.dispose();
    }
}
