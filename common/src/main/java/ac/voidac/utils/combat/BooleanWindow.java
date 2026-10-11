package ac.voidac.utils.combat;

public final class BooleanWindow {
    private final boolean[] values;
    private int next;
    private int size;
    private int trueCount;

    public BooleanWindow(int capacity) {
        this.values = new boolean[capacity];
    }

    public void add(boolean value) {
        if (size == values.length && values[next]) trueCount--;
        values[next] = value;
        if (value) trueCount++;
        next = (next + 1) % values.length;
        if (size < values.length) size++;
    }

    public boolean isFull() {
        return size == values.length;
    }

    public int trueCount() {
        return trueCount;
    }

    public void clear() {
        next = 0;
        size = 0;
        trueCount = 0;
    }
}
