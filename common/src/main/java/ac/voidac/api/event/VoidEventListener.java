package ac.voidac.api.event;

@FunctionalInterface
public interface VoidEventListener<T extends VoidEvent<?>> {
    void handle(T event) throws Exception;
}
