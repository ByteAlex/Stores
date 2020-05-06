package discord4j.store.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import discord4j.store.api.Store;
import discord4j.store.api.util.WithinRangePredicate;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.time.Duration;

public class DelegatingCacheStore<K extends Comparable<K>, V> implements Store<K, V> {

    private final Store<K, V> delegate;
    private final Cache<K, V> cache;

    public DelegatingCacheStore(final Duration cacheTime, final Store<K, V> delegate) {
        this.delegate = delegate;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(cacheTime)
                .build();
    }

    @Override
    public Mono<Void> save(K key, V value) {
        return Mono.fromRunnable(() -> cache.put(key, value))
                .then(delegate.save(key, value));
    }

    @Override
    public Mono<Void> save(Publisher<Tuple2<K, V>> entryStream) {
        return Flux.from(entryStream)
                .doOnNext(tuple -> cache.put(tuple.getT1(), tuple.getT2()))
                .then(delegate.save(entryStream));
    }

    @Override
    public Mono<Void> delete(K id) {
        return Mono.fromRunnable(() -> cache.invalidate(id))
                .then(delegate.delete(id));
    }

    @Override
    public Mono<Void> delete(Publisher<K> ids) {
        return Flux.from(ids)
                .collectList()
                .doOnNext(cache::invalidateAll)
                .then(delegate.delete(ids));
    }

    @Override
    public Mono<Void> deleteInRange(K start, K end) {
        return Flux.fromIterable(cache.asMap().keySet())
                .filter(new WithinRangePredicate<>(start, end))
                .doOnNext(cache::invalidate)
                .then(delegate.deleteInRange(start, end));
    }

    @Override
    public Mono<Void> deleteAll() {
        return Mono.fromRunnable(cache::invalidateAll);
    }

    @Override
    public Mono<Void> invalidate() {
        return Mono.fromRunnable(cache::invalidateAll);
    }

    @Override
    public Mono<V> find(K id) {
        return Mono.fromCallable(() -> cache.getIfPresent(id))
                .switchIfEmpty(delegate.find(id));
    }

    @Override
    public Flux<V> findInRange(K start, K end) {
        return delegate.findInRange(start, end); // no guarantee that local cache contains all elements
    }

    @Override
    public Mono<Long> count() {
        return delegate.count(); // no guarantee that local cache contains all elements
    }

    @Override
    public Flux<K> keys() {
        return delegate.keys(); // no guarantee that local cache contains all elements
    }

    @Override
    public Flux<V> values() {
        return delegate.values(); // no guarantee that local cache contains all elements
    }

    @Override
    public Flux<Tuple2<K, V>> entries() {
        return delegate.entries(); // no guarantee that local cache contains all elements
    }
}
