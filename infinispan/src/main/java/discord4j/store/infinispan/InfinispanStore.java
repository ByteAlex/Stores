package discord4j.store.infinispan;

import discord4j.store.api.Store;
import discord4j.store.api.util.WithinRangePredicate;
import org.infinispan.Cache;
import org.infinispan.context.Flag;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

public class InfinispanStore<K extends Comparable<K>, V> implements Store<K, V> {

    private final Cache<K, V> cache;

    public InfinispanStore(Cache<K, V> cache) {
        this.cache = cache;
    }

    @Override
    public Mono<Void> save(K key, V value) {
        return Mono.fromFuture(cache.getAdvancedCache().withFlags(Flag.SKIP_CACHE_LOAD).putAsync(key, value))
                .then();
    }

    @Override
    public Mono<Void> save(Publisher<Tuple2<K, V>> entryStream) {
        return Flux.from(entryStream)
                .flatMap(tuple -> save(tuple.getT1(), tuple.getT2()))
                .then();
    }

    @Override
    public Mono<Void> delete(K id) {
        return Mono.fromFuture(cache.getAdvancedCache().withFlags(Flag.SKIP_CACHE_LOAD).removeAsync(id))
                .then();
    }

    @Override
    public Mono<Void> delete(Publisher<K> ids) {
        return Flux.from(ids)
                .flatMap(this::delete)
                .then();
    }

    @Override
    public Mono<Void> deleteInRange(K start, K end) {
        return keys()
                .filter(new WithinRangePredicate<>(start, end))
                .flatMap(this::delete)
                .then();
    }

    @Override
    public Mono<Void> deleteAll() {
        return Mono.fromFuture(cache.clearAsync());
    }

    @Override
    public Mono<Void> invalidate() {
        return deleteAll();
    }

    @Override
    public Mono<V> find(K id) {
        return Mono.fromFuture(cache.getAsync(id));
    }

    @Override
    public Flux<V> findInRange(K start, K end) {
        return keys()
                .filter(new WithinRangePredicate<>(start, end))
                .flatMap(this::find);
    }

    @Override
    public Mono<Long> count() {
        return Mono.fromFuture(cache.sizeAsync());
    }

    @Override
    public Flux<K> keys() {
        return Flux.fromIterable(cache.keySet());
    }

    @Override
    public Flux<V> values() {
        return Flux.fromIterable(cache.values());
    }
}
